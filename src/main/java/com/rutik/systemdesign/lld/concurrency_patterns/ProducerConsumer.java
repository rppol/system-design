package com.rutik.systemdesign.lld.concurrency_patterns; /**
 * PRODUCER-CONSUMER PATTERN
 *
 * Problem: Producers generate data/work items at their own rate.
 *          Consumers process them at their own rate.
 *          Need a thread-safe buffer between them.
 *
 * Three implementations shown:
 *   1. Low-level wait/notify (educational — shows the underlying mechanism)
 *   2. BlockingQueue (recommended for production)
 *   3. Real-world: Log Processing Pipeline
 *
 * Key concepts:
 *   - Bounded buffer: prevents memory exhaustion when producers outrun consumers
 *   - Backpressure: producers slow down or block when buffer is full
 *   - Graceful shutdown: Poison Pill pattern
 */

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

// ══════════════════════════════════════════════════════════════
// IMPLEMENTATION 1: Low-level wait/notify
// ══════════════════════════════════════════════════════════════

class WaitNotifyBuffer {
    private final Queue<Integer> buffer = new LinkedList<>();
    private final int CAPACITY;

    public WaitNotifyBuffer(int capacity) { this.CAPACITY = capacity; }

    /**
     * Producer calls this. Blocks if buffer is full.
     * Uses while() not if() to guard against spurious wakeups.
     */
    public synchronized void produce(int item) throws InterruptedException {
        // CRITICAL: use while(), not if() — guards against spurious wakeups
        while (buffer.size() == CAPACITY) {
            System.out.println("  [Producer] Buffer full (" + CAPACITY + "). Waiting...");
            wait(); // releases lock and waits
        }
        buffer.add(item);
        System.out.println("  [Producer] Produced: " + item + " | Buffer size: " + buffer.size());
        notifyAll(); // wake up all waiting consumers
    }

    /**
     * Consumer calls this. Blocks if buffer is empty.
     */
    public synchronized int consume() throws InterruptedException {
        // CRITICAL: use while(), not if()
        while (buffer.isEmpty()) {
            System.out.println("  [Consumer] Buffer empty. Waiting...");
            wait();
        }
        int item = buffer.poll();
        System.out.println("  [Consumer] Consumed: " + item + " | Buffer size: " + buffer.size());
        notifyAll(); // wake up all waiting producers
        return item;
    }
}

class WaitNotifyProducerConsumer {
    public static void demonstrate() throws InterruptedException {
        System.out.println("=== Wait/Notify Implementation ===");
        WaitNotifyBuffer buffer = new WaitNotifyBuffer(3);

        Thread producer = new Thread(() -> {
            try {
                for (int i = 1; i <= 7; i++) {
                    buffer.produce(i);
                    Thread.sleep(100);
                }
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }, "WN-Producer");

        Thread consumer = new Thread(() -> {
            try {
                for (int i = 0; i < 7; i++) {
                    buffer.consume();
                    Thread.sleep(200); // consumer is slower
                }
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }, "WN-Consumer");

        producer.start();
        consumer.start();
        producer.join();
        consumer.join();
    }
}

// ══════════════════════════════════════════════════════════════
// IMPLEMENTATION 2: BlockingQueue (recommended)
// ══════════════════════════════════════════════════════════════

class Task {
    private final int id;
    private final String payload;
    private final long createdAt = System.currentTimeMillis();

    public Task(int id, String payload) {
        this.id = id;
        this.payload = payload;
    }

    public int    getId()      { return id; }
    public String getPayload() { return payload; }
    public long   getAge()     { return System.currentTimeMillis() - createdAt; }

    @Override
    public String toString() { return "Task{id=" + id + ", payload='" + payload + "'}"; }
}

// Poison pill — sentinel value to signal shutdown
class PoisonPill extends Task {
    public static final PoisonPill INSTANCE = new PoisonPill();
    private PoisonPill() { super(-1, "SHUTDOWN"); }
}

class BlockingQueueProducerConsumer {

    static class Producer implements Runnable {
        private final BlockingQueue<Task> queue;
        private final int producerId;
        private final int numTasks;

        Producer(BlockingQueue<Task> queue, int producerId, int numTasks) {
            this.queue = queue;
            this.producerId = producerId;
            this.numTasks = numTasks;
        }

        @Override
        public void run() {
            try {
                for (int i = 0; i < numTasks; i++) {
                    Task task = new Task(producerId * 100 + i, "data-" + producerId + "-" + i);
                    queue.put(task); // blocks if queue is full (backpressure!)
                    System.out.printf("  [Producer-%d] Queued %s (queue size: %d)%n",
                            producerId, task, queue.size());
                    Thread.sleep(50);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    static class Consumer implements Runnable {
        private final BlockingQueue<Task> queue;
        private final int consumerId;
        private int processedCount = 0;

        Consumer(BlockingQueue<Task> queue, int consumerId) {
            this.queue = queue;
            this.consumerId = consumerId;
        }

        @Override
        public void run() {
            try {
                while (true) {
                    Task task = queue.take(); // blocks if queue is empty
                    if (task instanceof PoisonPill) {
                        // Propagate the poison pill to other consumers
                        queue.put(PoisonPill.INSTANCE);
                        break;
                    }
                    processTask(task);
                    processedCount++;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            System.out.printf("  [Consumer-%d] Shutting down. Processed: %d tasks%n",
                    consumerId, processedCount);
        }

        private void processTask(Task task) throws InterruptedException {
            Thread.sleep(120); // simulate processing time
            System.out.printf("  [Consumer-%d] Processed %s (age=%dms)%n",
                    consumerId, task, task.getAge());
        }
    }

    public static void demonstrate() throws InterruptedException {
        System.out.println("\n=== BlockingQueue Implementation ===");

        // ArrayBlockingQueue: bounded, FIFO
        BlockingQueue<Task> queue = new ArrayBlockingQueue<>(10);

        // 2 producers × 5 tasks each = 10 tasks total
        int numProducers = 2;
        int numConsumers = 3;
        ExecutorService executor = Executors.newFixedThreadPool(numProducers + numConsumers);

        for (int i = 1; i <= numProducers; i++) {
            executor.submit(new Producer(queue, i, 5));
        }
        for (int i = 1; i <= numConsumers; i++) {
            executor.submit(new Consumer(queue, i));
        }

        // Wait for producers to finish, then send poison pill
        Thread.sleep(2000);
        queue.put(PoisonPill.INSTANCE);

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
        System.out.println("  All consumers done.");
    }
}

// ══════════════════════════════════════════════════════════════
// IMPLEMENTATION 3: Real-world — Log Processing Pipeline
// ══════════════════════════════════════════════════════════════

enum LogLevel { DEBUG, INFO, WARN, ERROR }

class LogEvent {
    private final String service;
    private final LogLevel level;
    private final String message;
    private final long timestamp = System.currentTimeMillis();

    public LogEvent(String service, LogLevel level, String message) {
        this.service = service;
        this.level   = level;
        this.message = message;
    }

    public String   getService()   { return service; }
    public LogLevel getLevel()     { return level; }
    public String   getMessage()   { return message; }
    public long     getTimestamp() { return timestamp; }

    @Override
    public String toString() {
        return String.format("[%s] %-5s %s: %s", timestamp, level, service, message);
    }
}

/** Simulates application threads generating log events */
class ApplicationLogProducer implements Runnable {
    private final BlockingQueue<LogEvent> queue;
    private final String serviceName;
    private final int logCount;
    private final AtomicInteger produced;

    ApplicationLogProducer(BlockingQueue<LogEvent> queue, String serviceName,
                            int logCount, AtomicInteger produced) {
        this.queue       = queue;
        this.serviceName = serviceName;
        this.logCount    = logCount;
        this.produced    = produced;
    }

    @Override
    public void run() {
        Random rnd = new Random();
        LogLevel[] levels = LogLevel.values();
        try {
            for (int i = 0; i < logCount; i++) {
                LogLevel level = levels[rnd.nextInt(levels.length)];
                LogEvent event = new LogEvent(serviceName, level, "Operation #" + i + " completed");
                if (!queue.offer(event, 100, TimeUnit.MILLISECONDS)) {
                    System.out.println("  [" + serviceName + "] Log queue full — dropping event!");
                } else {
                    produced.incrementAndGet();
                }
                Thread.sleep(rnd.nextInt(30));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

/** Filters logs and routes them */
class LogFilterProcessor implements Runnable {
    private final BlockingQueue<LogEvent> inputQueue;
    private final BlockingQueue<LogEvent> errorQueue;   // route ERRORs separately
    private final AtomicInteger processedCount;
    private volatile boolean running = true;

    LogFilterProcessor(BlockingQueue<LogEvent> input, BlockingQueue<LogEvent> errorQueue,
                       AtomicInteger processedCount) {
        this.inputQueue     = input;
        this.errorQueue     = errorQueue;
        this.processedCount = processedCount;
    }

    public void stop() { running = false; }

    @Override
    public void run() {
        try {
            while (running || !inputQueue.isEmpty()) {
                LogEvent event = inputQueue.poll(200, TimeUnit.MILLISECONDS);
                if (event == null) continue;

                // Route ERRORs to alert queue
                if (event.getLevel() == LogLevel.ERROR) {
                    errorQueue.offer(event);
                }
                // All events get counted
                processedCount.incrementAndGet();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        System.out.printf("  [LogProcessor] Processed %d events%n", processedCount.get());
    }
}

/** Handles ERROR-level events — sends alerts */
class AlertProcessor implements Runnable {
    private final BlockingQueue<LogEvent> errorQueue;
    private volatile boolean running = true;
    private int alertCount = 0;

    AlertProcessor(BlockingQueue<LogEvent> errorQueue) { this.errorQueue = errorQueue; }

    public void stop() { running = false; }

    @Override
    public void run() {
        try {
            while (running || !errorQueue.isEmpty()) {
                LogEvent event = errorQueue.poll(200, TimeUnit.MILLISECONDS);
                if (event == null) continue;
                alertCount++;
                System.out.println("  [ALERT] " + event);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        System.out.printf("  [AlertProcessor] Sent %d alerts%n", alertCount);
    }
}

class LogProcessingPipeline {
    public static void demonstrate() throws InterruptedException {
        System.out.println("\n=== Log Processing Pipeline ===");

        BlockingQueue<LogEvent> mainQueue  = new LinkedBlockingQueue<>(500);
        BlockingQueue<LogEvent> errorQueue = new LinkedBlockingQueue<>(100);

        AtomicInteger produced  = new AtomicInteger();
        AtomicInteger processed = new AtomicInteger();

        // Start consumers
        LogFilterProcessor filterProcessor = new LogFilterProcessor(mainQueue, errorQueue, processed);
        AlertProcessor     alertProcessor  = new AlertProcessor(errorQueue);

        Thread filterThread = new Thread(filterProcessor, "LogFilter");
        Thread alertThread  = new Thread(alertProcessor,  "AlertProcessor");
        filterThread.start();
        alertThread.start();

        // Start 3 producer services
        ExecutorService producers = Executors.newFixedThreadPool(3);
        producers.submit(new ApplicationLogProducer(mainQueue, "OrderService",    10, produced));
        producers.submit(new ApplicationLogProducer(mainQueue, "PaymentService",  10, produced));
        producers.submit(new ApplicationLogProducer(mainQueue, "NotifyService",   10, produced));
        producers.shutdown();
        producers.awaitTermination(5, TimeUnit.SECONDS);

        // Let consumers drain
        Thread.sleep(1000);
        filterProcessor.stop();
        alertProcessor.stop();

        filterThread.join();
        alertThread.join();

        System.out.printf("  Total produced: %d | Total processed: %d%n",
                produced.get(), processed.get());
    }
}

// ─────────────────────────────────────────────────────────────
// Main Demo
// ─────────────────────────────────────────────────────────────

class ProducerConsumerDemo {
    public static void main(String[] args) throws InterruptedException {
        WaitNotifyProducerConsumer.demonstrate();
        BlockingQueueProducerConsumer.demonstrate();
        LogProcessingPipeline.demonstrate();
    }
}
