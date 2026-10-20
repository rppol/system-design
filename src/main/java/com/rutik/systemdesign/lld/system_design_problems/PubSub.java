package com.rutik.systemdesign.lld.system_design_problems;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

// =============================================================================
//  IN-MEMORY PUB-SUB / MESSAGE QUEUE - Low-Level Design
//  A Kafka-shaped broker: each topic is N append-only partition logs; a message
//  key picks the partition, so per-key order holds. Consumer groups share a
//  topic's partitions (each partition read by one member at a time); different
//  groups each see every message (fan-out). Consumers pull, track their own
//  positions, and commit offsets after processing: at-least-once delivery.
//  Patterns used:
//    - Observer, inverted to pull : subscribers poll instead of being called
//    - Strategy                   : PsPartitioner picks the partition
//    - Mediator                   : PsConsumerGroup assigns partitions to members
// =============================================================================

record PsMessage(String topic, int partition, long offset, String key, String payload) {}

record PsPublishResult(int partition, long offset) {}

interface PsPartitioner {
    int partitionFor(String key, int partitionCount);
}

/** Same key always maps to the same partition; null keys spread round-robin. */
final class PsKeyHashPartitioner implements PsPartitioner {
    private final AtomicLong roundRobin = new AtomicLong();

    @Override
    public int partitionFor(String key, int partitionCount) {
        if (key == null) return (int) (roundRobin.getAndIncrement() % partitionCount);
        return Math.floorMod(key.hashCode(), partitionCount);
    }
}

/** Append-only log with count-based retention; offsets never repeat. */
final class PsPartitionLog {
    private final List<PsMessage> messages = new ArrayList<>();
    private final int retainLast;
    private long firstOffset;           // offset of messages.get(0)

    PsPartitionLog(int retainLast) {
        this.retainLast = retainLast;
    }

    synchronized long append(String topic, int partition, String key, String payload) {
        long offset = firstOffset + messages.size();
        messages.add(new PsMessage(topic, partition, offset, key, payload));
        if (messages.size() > retainLast) {
            messages.remove(0);
            firstOffset++;
        }
        return offset;
    }

    /** Reads up to max messages starting at fromOffset; a position below retention snaps forward. */
    synchronized List<PsMessage> read(long fromOffset, int max) {
        long start = Math.max(fromOffset, firstOffset);
        int from = (int) (start - firstOffset);
        int to = Math.min(messages.size(), from + max);
        return from >= to ? List.of() : List.copyOf(messages.subList(from, to));
    }
}

final class PsTopic {
    final String name;
    final List<PsPartitionLog> partitions = new ArrayList<>();

    PsTopic(String name, int partitionCount, int retainLast) {
        this.name = name;
        for (int i = 0; i < partitionCount; i++) partitions.add(new PsPartitionLog(retainLast));
    }
}

/** Members, partition assignment and committed offsets for one group on one topic. */
final class PsConsumerGroup {
    private final String groupId;
    private final int partitionCount;
    private final List<String> members = new ArrayList<>();
    private final Map<Integer, Long> committed = new HashMap<>();
    private Map<String, List<Integer>> assignment = Map.of();

    PsConsumerGroup(String groupId, int partitionCount) {
        this.groupId = groupId;
        this.partitionCount = partitionCount;
    }

    synchronized void join(String consumerId) {
        members.add(consumerId);
        rebalance();
    }

    synchronized void leave(String consumerId) {
        members.remove(consumerId);
        rebalance();
    }

    synchronized List<Integer> partitionsOf(String consumerId) {
        return assignment.getOrDefault(consumerId, List.of());
    }

    synchronized long committedOffset(int partition) {
        return committed.getOrDefault(partition, 0L);
    }

    synchronized void commit(String consumerId, Map<Integer, Long> positions) {
        for (Map.Entry<Integer, Long> p : positions.entrySet()) {
            if (partitionsOf(consumerId).contains(p.getKey())) committed.put(p.getKey(), p.getValue());
        }
    }

    /** Round-robin: partition i goes to member i mod memberCount. */
    private void rebalance() {
        Map<String, List<Integer>> next = new LinkedHashMap<>();
        for (String m : members) next.put(m, new ArrayList<>());
        for (int p = 0; p < partitionCount && !members.isEmpty(); p++) {
            next.get(members.get(p % members.size())).add(p);
        }
        assignment = next;
        System.out.println("  [" + groupId + "] rebalance -> " + next);
    }
}

/** One member of a group. Not thread-safe: one consumer per thread, as in Kafka. */
final class PsConsumer {
    private final String id;
    private final PsTopic topic;
    private final PsConsumerGroup group;
    private final Map<Integer, Long> positions = new HashMap<>();

    PsConsumer(String id, PsTopic topic, PsConsumerGroup group) {
        this.id = id;
        this.topic = topic;
        this.group = group;
    }

    public List<PsMessage> poll(int maxPerPartition) {
        List<PsMessage> out = new ArrayList<>();
        List<Integer> mine = group.partitionsOf(id);
        positions.keySet().retainAll(mine);                        // drop partitions lost in a rebalance
        for (int p : mine) {
            long from = positions.computeIfAbsent(p, group::committedOffset);
            List<PsMessage> batch = topic.partitions.get(p).read(from, maxPerPartition);
            if (!batch.isEmpty()) positions.put(p, batch.get(batch.size() - 1).offset() + 1);
            out.addAll(batch);
        }
        return out;
    }

    /** Commit after processing: a crash before this line means redelivery, not loss. */
    public void commit() {
        group.commit(id, positions);
    }

    public void close() {
        group.leave(id);
    }

    public String id() {
        return id;
    }
}

public final class PubSub {
    private final Map<String, PsTopic> topics = new ConcurrentHashMap<>();
    private final Map<String, PsConsumerGroup> groups = new ConcurrentHashMap<>();
    private final PsPartitioner partitioner;
    private final int retainLast;

    public PubSub(PsPartitioner partitioner, int retainLast) {
        this.partitioner = partitioner;
        this.retainLast = retainLast;
    }

    public void createTopic(String name, int partitions) {
        if (topics.putIfAbsent(name, new PsTopic(name, partitions, retainLast)) != null) {
            throw new IllegalArgumentException("topic exists: " + name);
        }
    }

    public PsPublishResult publish(String topicName, String key, String payload) {
        PsTopic topic = topic(topicName);
        int p = partitioner.partitionFor(key, topic.partitions.size());
        long offset = topic.partitions.get(p).append(topicName, p, key, payload);
        return new PsPublishResult(p, offset);
    }

    public PsConsumer subscribe(String topicName, String groupId, String consumerId) {
        PsTopic topic = topic(topicName);
        PsConsumerGroup group = groups.computeIfAbsent(topicName + "/" + groupId,
                g -> new PsConsumerGroup(groupId, topic.partitions.size()));
        group.join(consumerId);
        return new PsConsumer(consumerId, topic, group);
    }

    private PsTopic topic(String name) {
        PsTopic t = topics.get(name);
        if (t == null) throw new IllegalArgumentException("no such topic: " + name);
        return t;
    }

    private static void print(PsConsumer c, List<PsMessage> batch) {
        for (PsMessage m : batch) {
            System.out.println("  " + c.id() + " got p" + m.partition() + "@" + m.offset()
                    + " key=" + m.key() + " " + m.payload());
        }
    }

    public static void main(String[] args) {
        PubSub broker = new PubSub(new PsKeyHashPartitioner(), 1000);
        broker.createTopic("orders", 3);

        System.out.println("== publish: same key, same partition ==");
        String[][] events = {
            {"user-1", "created"}, {"user-2", "created"}, {"user-1", "paid"},
            {"user-3", "created"}, {"user-1", "shipped"}, {"user-2", "paid"}};
        for (String[] e : events) {
            PsPublishResult r = broker.publish("orders", e[0], e[0] + ":" + e[1]);
            System.out.println("  " + e[0] + ":" + e[1] + " -> p" + r.partition() + "@" + r.offset());
        }

        System.out.println("== two groups: each sees every message ==");
        PsConsumer billing = broker.subscribe("orders", "billing", "billing-1");
        PsConsumer email = broker.subscribe("orders", "email", "email-1");
        System.out.println("  billing-1 polled " + billing.poll(10).size() + " messages");
        billing.commit();
        System.out.println("  email-1 polled " + email.poll(10).size() + " messages");
        email.commit();

        System.out.println("== one group, two members: partitions are split ==");
        PsConsumer a = broker.subscribe("orders", "analytics", "analytics-a");
        PsConsumer b = broker.subscribe("orders", "analytics", "analytics-b");
        print(a, a.poll(10));
        a.commit();
        List<PsMessage> bBatch = b.poll(10);
        print(b, bBatch);
        System.out.println("  analytics-b crashes before commit");
        b.close();

        System.out.println("== rebalance: survivor re-reads b's uncommitted messages ==");
        print(a, a.poll(10));
        a.commit();
    }
}
