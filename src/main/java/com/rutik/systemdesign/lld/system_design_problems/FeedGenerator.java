package com.rutik.systemdesign.lld.system_design_problems;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

// =============================================================================
//  FEED GENERATOR - Low-Level Design
//
//  Patterns used:
//    - Strategy  : FdRankingStrategy (chronological, engagement with time decay)
//    - Decorator : FdAffinityBoost wraps any ranking strategy
//    - Chain     : FdFeedFilter list (muted authors, author diversity) applied
//                  in order after ranking
//    - Facade    : FdFeedService is the only entry point
//
//  Key design points:
//    - Hybrid fan-out. A post by a normal author is PUSHED into each
//      follower's bounded inbox at write time. A post by an author above the
//      celebrity threshold is NOT pushed; readers PULL it at read time.
//    - Offset pagination on a live feed repeats items when new posts arrive
//      between pages: demonstrateOffsetBug(). Chronological feeds use a keyset
//      cursor (createdAt, postId). Ranked feeds use a per-user snapshot in a
//      TTL + LRU cache, and the cursor points into that snapshot.
//    - The inbox can hold stale entries (unfollowed author, deleted post), so
//      the read path re-checks the follow graph and the post store.
//
//  All type names carry the Fd prefix because every file in this package is
//  compiled together.
// =============================================================================

record FdPost(long id, String authorId, long createdAtSec, int likes, int comments, String text) { }

record FdPage(List<FdPost> items, String nextCursor) { }

class FdCursorExpiredException extends RuntimeException {
    FdCursorExpiredException(String message) {
        super(message);
    }
}

class FdClock {
    private final AtomicLong nowSec;

    FdClock(long startSec) {
        this.nowSec = new AtomicLong(startSec);
    }

    long now() {
        return nowSec.get();
    }

    void advance(long seconds) {
        nowSec.addAndGet(seconds);
    }
}

// -----------------------------------------------------------------------------
//  FOLLOW GRAPH (both directions, thread-safe)
// -----------------------------------------------------------------------------
class FdFollowGraph {
    private final Map<String, Set<String>> following = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> followers = new ConcurrentHashMap<>();

    void follow(String user, String author) {
        following.computeIfAbsent(user, k -> ConcurrentHashMap.newKeySet()).add(author);
        followers.computeIfAbsent(author, k -> ConcurrentHashMap.newKeySet()).add(user);
    }

    void unfollow(String user, String author) {
        following.getOrDefault(user, Set.of()).remove(author);
        followers.getOrDefault(author, Set.of()).remove(user);
    }

    Set<String> followingOf(String user) {
        return following.getOrDefault(user, Set.of());
    }

    Set<String> followersOf(String author) {
        return followers.getOrDefault(author, Set.of());
    }

    boolean follows(String user, String author) {
        return followingOf(user).contains(author);
    }
}

// -----------------------------------------------------------------------------
//  POST STORE: by id, and per-author newest-first timelines
// -----------------------------------------------------------------------------
class FdPostStore {
    private final Map<Long, FdPost> byId = new ConcurrentHashMap<>();
    private final Map<String, Deque<FdPost>> byAuthor = new ConcurrentHashMap<>();
    private final AtomicLong ids = new AtomicLong();

    FdPost create(String author, long createdAtSec, int likes, int comments, String text) {
        FdPost post = new FdPost(ids.incrementAndGet(), author, createdAtSec, likes, comments, text);
        byId.put(post.id(), post);
        Deque<FdPost> timeline = byAuthor.computeIfAbsent(author, k -> new ArrayDeque<>());
        synchronized (timeline) {
            timeline.addFirst(post);
        }
        return post;
    }

    FdPost get(long id) {
        return byId.get(id);
    }

    void delete(long id) {
        byId.remove(id);   // timelines and inboxes keep the id; readers skip it
    }

    List<FdPost> recentByAuthor(String author, int limit) {
        Deque<FdPost> timeline = byAuthor.getOrDefault(author, new ArrayDeque<>());
        List<FdPost> out = new ArrayList<>();
        synchronized (timeline) {
            for (FdPost p : timeline) {
                if (out.size() == limit) {
                    break;
                }
                if (byId.containsKey(p.id())) {
                    out.add(p);
                }
            }
        }
        return out;
    }
}

// -----------------------------------------------------------------------------
//  INBOX: bounded, newest-first list of post ids pushed at write time
// -----------------------------------------------------------------------------
class FdInbox {
    private final int capacity;
    private final Deque<Long> postIds = new ArrayDeque<>();

    FdInbox(int capacity) {
        this.capacity = capacity;
    }

    synchronized void push(long postId) {
        postIds.addFirst(postId);
        while (postIds.size() > capacity) {
            postIds.removeLast();          // oldest falls off: inbox memory is bounded
        }
    }

    synchronized List<Long> snapshot() {
        return new ArrayList<>(postIds);
    }

    synchronized int size() {
        return postIds.size();
    }
}

// -----------------------------------------------------------------------------
//  RANKING: Strategy + Decorator
// -----------------------------------------------------------------------------
interface FdRankingStrategy {
    double score(FdPost post, String viewer, long nowSec);

    String name();
}

class FdChronological implements FdRankingStrategy {
    @Override
    public double score(FdPost post, String viewer, long nowSec) {
        return post.createdAtSec();
    }

    @Override
    public String name() {
        return "chronological";
    }
}

/** Engagement divided by a power of age, so a post must keep earning interactions to stay on top. */
class FdEngagementDecay implements FdRankingStrategy {
    private final double gravity;

    FdEngagementDecay(double gravity) {
        this.gravity = gravity;
    }

    @Override
    public double score(FdPost post, String viewer, long nowSec) {
        double ageHours = Math.max(0, nowSec - post.createdAtSec()) / 3600.0;
        double engagement = 1 + post.likes() + 2.0 * post.comments();
        return engagement / Math.pow(ageHours + 2, gravity);
    }

    @Override
    public String name() {
        return "engagement / (ageHours + 2)^" + gravity;
    }
}

/** Decorator: boosts authors the viewer interacts with, on top of ANY base strategy. */
class FdAffinityBoost implements FdRankingStrategy {
    private final FdRankingStrategy inner;
    private final Map<String, Map<String, Integer>> interactions = new ConcurrentHashMap<>();

    FdAffinityBoost(FdRankingStrategy inner) {
        this.inner = inner;
    }

    void recordInteraction(String viewer, String author) {
        interactions.computeIfAbsent(viewer, k -> new ConcurrentHashMap<>()).merge(author, 1, Integer::sum);
    }

    @Override
    public double score(FdPost post, String viewer, long nowSec) {
        int n = interactions.getOrDefault(viewer, Map.of()).getOrDefault(post.authorId(), 0);
        return inner.score(post, viewer, nowSec) * (1 + 0.5 * Math.min(n, 10));
    }

    @Override
    public String name() {
        return inner.name() + " x affinity";
    }
}

// -----------------------------------------------------------------------------
//  FILTERS: applied in order after ranking
// -----------------------------------------------------------------------------
interface FdFeedFilter {
    List<FdPost> apply(String viewer, List<FdPost> ranked);
}

class FdMutedAuthorsFilter implements FdFeedFilter {
    private final Map<String, Set<String>> muted = new ConcurrentHashMap<>();

    void mute(String viewer, String author) {
        muted.computeIfAbsent(viewer, k -> ConcurrentHashMap.newKeySet()).add(author);
    }

    @Override
    public List<FdPost> apply(String viewer, List<FdPost> ranked) {
        Set<String> m = muted.getOrDefault(viewer, Set.of());
        return ranked.stream().filter(p -> !m.contains(p.authorId())).toList();
    }
}

/** No more than `maxRun` consecutive posts by one author: later posts are deferred, not dropped. */
class FdAuthorDiversityFilter implements FdFeedFilter {
    private final int maxRun;

    FdAuthorDiversityFilter(int maxRun) {
        this.maxRun = maxRun;
    }

    @Override
    public List<FdPost> apply(String viewer, List<FdPost> ranked) {
        List<FdPost> out = new ArrayList<>();
        List<FdPost> deferred = new ArrayList<>(ranked);
        while (!deferred.isEmpty()) {
            boolean placed = false;
            for (Iterator<FdPost> it = deferred.iterator(); it.hasNext(); ) {
                FdPost p = it.next();
                if (runLength(out, p.authorId()) < maxRun) {
                    out.add(p);
                    it.remove();
                    placed = true;
                    break;
                }
            }
            if (!placed) {
                out.addAll(deferred);      // only one author left: nothing to interleave with
                break;
            }
        }
        return out;
    }

    private static int runLength(List<FdPost> out, String author) {
        int n = 0;
        for (int i = out.size() - 1; i >= 0 && out.get(i).authorId().equals(author); i--) {
            n++;
        }
        return n;
    }
}

// -----------------------------------------------------------------------------
//  PER-USER SNAPSHOT CACHE: TTL + LRU, so a ranked feed pages stably
// -----------------------------------------------------------------------------
class FdFeedCache {
    private record Entry(List<Long> postIds, long createdAtSec) { }

    private final int maxSessions;
    private final long ttlSec;
    private final FdClock clock;
    private final LinkedHashMap<String, Entry> sessions;
    private int evictions;

    FdFeedCache(int maxSessions, long ttlSec, FdClock clock) {
        this.maxSessions = maxSessions;
        this.ttlSec = ttlSec;
        this.clock = clock;
        this.sessions = new LinkedHashMap<>(16, 0.75f, true) {   // access order = LRU
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Entry> eldest) {
                boolean evict = size() > FdFeedCache.this.maxSessions;
                if (evict) {
                    evictions++;
                }
                return evict;
            }
        };
    }

    synchronized void put(String sessionId, List<Long> postIds) {
        sessions.put(sessionId, new Entry(List.copyOf(postIds), clock.now()));
    }

    /** Returns null when the session is unknown, evicted or older than the TTL. */
    synchronized List<Long> get(String sessionId) {
        Entry e = sessions.get(sessionId);
        if (e == null) {
            return null;
        }
        if (clock.now() - e.createdAtSec() >= ttlSec) {
            sessions.remove(sessionId);
            return null;
        }
        return e.postIds();
    }

    synchronized int evictions() {
        return evictions;
    }
}

// -----------------------------------------------------------------------------
//  FACADE
// -----------------------------------------------------------------------------
class FdFeedService {
    private static final int CELEBRITY_PULL_LIMIT = 50;

    private final FdFollowGraph graph;
    private final FdPostStore posts;
    private final FdClock clock;
    private final int celebrityThreshold;
    private final int inboxCapacity;
    private final Map<String, FdInbox> inboxes = new ConcurrentHashMap<>();
    private final FdFeedCache cache;
    private final List<FdFeedFilter> filters = new ArrayList<>();
    private final AtomicLong sessionSeq = new AtomicLong();
    private final AtomicLong inboxWrites = new AtomicLong();
    private volatile FdRankingStrategy ranking = new FdChronological();

    FdFeedService(FdFollowGraph graph, FdPostStore posts, FdClock clock,
                  int celebrityThreshold, int inboxCapacity, FdFeedCache cache) {
        this.graph = graph;
        this.posts = posts;
        this.clock = clock;
        this.celebrityThreshold = celebrityThreshold;
        this.inboxCapacity = inboxCapacity;
        this.cache = cache;
    }

    void setRanking(FdRankingStrategy ranking) {
        this.ranking = ranking;
    }

    void addFilter(FdFeedFilter filter) {
        filters.add(filter);
    }

    long inboxWrites() {
        return inboxWrites.get();
    }

    int inboxSize(String user) {
        return inboxes.containsKey(user) ? inboxes.get(user).size() : 0;
    }

    boolean isCelebrity(String author) {
        return graph.followersOf(author).size() > celebrityThreshold;
    }

    /** Write path: push to followers' inboxes unless the author is a celebrity. */
    FdPost publish(String author, int likes, int comments, String text) {
        FdPost post = posts.create(author, clock.now(), likes, comments, text);
        if (!isCelebrity(author)) {
            for (String follower : graph.followersOf(author)) {
                inboxes.computeIfAbsent(follower, k -> new FdInbox(inboxCapacity)).push(post.id());
                inboxWrites.incrementAndGet();
            }
        }
        return post;
    }

    /** Read path, step 1: inbox (push) + celebrity timelines (pull), deduplicated and re-checked. */
    List<FdPost> candidates(String viewer) {
        Map<Long, FdPost> byId = new HashMap<>();
        FdInbox inbox = inboxes.get(viewer);
        if (inbox != null) {
            for (long id : inbox.snapshot()) {
                FdPost p = posts.get(id);                              // deleted -> null
                if (p != null && graph.follows(viewer, p.authorId())) {   // unfollowed -> skip
                    byId.put(id, p);
                }
            }
        }
        for (String author : graph.followingOf(viewer)) {
            if (isCelebrity(author)) {
                for (FdPost p : posts.recentByAuthor(author, CELEBRITY_PULL_LIMIT)) {
                    byId.put(p.id(), p);
                }
            }
        }
        return new ArrayList<>(byId.values());
    }

    /** Read path, step 2: rank, then filters in order. */
    List<FdPost> rankAndFilter(String viewer) {
        long now = clock.now();
        FdRankingStrategy r = ranking;
        List<FdPost> ranked = new ArrayList<>(candidates(viewer));
        ranked.sort(Comparator.comparingDouble((FdPost p) -> r.score(p, viewer, now)).reversed()
                .thenComparing(Comparator.comparingLong(FdPost::id).reversed()));
        for (FdFeedFilter f : filters) {
            ranked = f.apply(viewer, ranked);
        }
        return ranked;
    }

    /** BROKEN on a live feed: positions shift when new posts arrive between pages. */
    List<FdPost> offsetPage(String viewer, int offset, int limit) {
        List<FdPost> all = chronological(viewer);
        return all.subList(Math.min(offset, all.size()), Math.min(offset + limit, all.size()));
    }

    /** FIX for chronological feeds: keyset cursor "createdAt:postId", strictly older than the last item seen. */
    FdPage chronologicalPage(String viewer, String cursor, int limit) {
        long afterTs = Long.MAX_VALUE;
        long afterId = Long.MAX_VALUE;
        if (cursor != null) {
            String[] parts = cursor.split(":");
            afterTs = Long.parseLong(parts[0]);
            afterId = Long.parseLong(parts[1]);
        }
        List<FdPost> page = new ArrayList<>();
        for (FdPost p : chronological(viewer)) {
            boolean older = p.createdAtSec() < afterTs || (p.createdAtSec() == afterTs && p.id() < afterId);
            if (older) {
                page.add(p);
                if (page.size() == limit) {
                    break;
                }
            }
        }
        String next = page.size() < limit ? null
                : page.get(page.size() - 1).createdAtSec() + ":" + page.get(page.size() - 1).id();
        return new FdPage(page, next);
    }

    /** FIX for ranked feeds: rank once per session, cache the id list, page through it. */
    FdPage rankedPage(String viewer, String cursor, int limit) {
        String sessionId;
        int offset;
        List<Long> snapshot;
        if (cursor == null) {
            sessionId = viewer + "#" + sessionSeq.incrementAndGet();
            snapshot = rankAndFilter(viewer).stream().map(FdPost::id).toList();
            cache.put(sessionId, snapshot);
            offset = 0;
        } else {
            int at = cursor.lastIndexOf('@');
            sessionId = cursor.substring(0, at);
            offset = Integer.parseInt(cursor.substring(at + 1));
            snapshot = cache.get(sessionId);
            if (snapshot == null) {
                throw new FdCursorExpiredException("feed session " + sessionId + " expired, refresh from the top");
            }
        }
        List<FdPost> page = new ArrayList<>();
        int i = offset;
        while (i < snapshot.size() && page.size() < limit) {
            FdPost p = posts.get(snapshot.get(i++));
            if (p != null) {                      // deleted since the snapshot: skip, keep the order
                page.add(p);
            }
        }
        String next = i < snapshot.size() ? sessionId + "@" + i : null;
        return new FdPage(page, next);
    }

    private List<FdPost> chronological(String viewer) {
        List<FdPost> all = new ArrayList<>(candidates(viewer));
        all.sort(Comparator.comparingLong(FdPost::createdAtSec).reversed()
                .thenComparing(Comparator.comparingLong(FdPost::id).reversed()));
        return all;
    }
}

// -----------------------------------------------------------------------------
//  DEMO
// -----------------------------------------------------------------------------
public class FeedGenerator {
    private static final long T0 = 1_795_000_000L;   // a fixed epoch second

    public static void main(String[] args) throws Exception {
        System.out.println("=== Feed Generator: hybrid fan-out, pluggable ranking, stable pagination ===");
        System.out.println();
        demonstrateOffsetBug();
        demonstrateFanOut();
        demonstrateRanking();
        demonstrateFilters();
        demonstrateRankedPaging();
        demonstrateStaleInbox();
        demonstrateConcurrency();
    }

    private static FdFeedService newService(FdClock clock, FdFollowGraph g, FdPostStore s, int celebrityThreshold) {
        return new FdFeedService(g, s, clock, celebrityThreshold, 500, new FdFeedCache(1_000, 600, clock));
    }

    private static void demonstrateOffsetBug() {
        System.out.println("--- Broken: offset pagination while new posts arrive ---");
        FdClock clock = new FdClock(T0);
        FdFollowGraph g = new FdFollowGraph();
        FdPostStore s = new FdPostStore();
        FdFeedService feed = newService(clock, g, s, 1_000);
        g.follow("viewer", "ana");
        for (int i = 1; i <= 8; i++) {
            clock.advance(60);
            feed.publish("ana", 0, 0, "post " + i);
        }
        List<FdPost> page1 = new ArrayList<>(feed.offsetPage("viewer", 0, 3));
        System.out.println("page 1 (offset 0): " + ids(page1));
        clock.advance(60);
        feed.publish("ana", 0, 0, "new A");
        feed.publish("ana", 0, 0, "new B");
        List<FdPost> page2 = feed.offsetPage("viewer", 3, 3);
        System.out.println("2 new posts arrive; page 2 (offset 3): " + ids(page2)
                + "   <- " + overlap(page1, page2) + " repeated from page 1");

        FdClock c2 = new FdClock(T0);
        FdFollowGraph g2 = new FdFollowGraph();
        FdPostStore s2 = new FdPostStore();
        FdFeedService feed2 = newService(c2, g2, s2, 1_000);
        g2.follow("viewer", "ana");
        for (int i = 1; i <= 8; i++) {
            c2.advance(60);
            feed2.publish("ana", 0, 0, "post " + i);
        }
        FdPage k1 = feed2.chronologicalPage("viewer", null, 3);
        c2.advance(60);
        feed2.publish("ana", 0, 0, "new A");
        feed2.publish("ana", 0, 0, "new B");
        FdPage k2 = feed2.chronologicalPage("viewer", k1.nextCursor(), 3);
        System.out.println("Fix (keyset cursor): page 1 " + ids(k1.items()) + "  cursor=" + k1.nextCursor()
                + "  page 2 " + ids(k2.items()) + "  repeated: " + overlap(k1.items(), k2.items()));
        System.out.println();
    }

    private static void demonstrateFanOut() {
        System.out.println("--- Step 1: write cost of push, pull and hybrid fan-out ---");
        for (int threshold : new int[] {Integer.MAX_VALUE, 0, 1_000}) {
            FdClock clock = new FdClock(T0);
            FdFollowGraph g = new FdFollowGraph();
            FdPostStore s = new FdPostStore();
            FdFeedService feed = newService(clock, g, s, threshold);
            for (int u = 0; u < 20_000; u++) {
                g.follow("u" + u, "star");          // 20,000 followers
                if (u < 200) {
                    g.follow("u" + u, "friend");    // 200 followers
                }
            }
            feed.publish("star", 0, 0, "hello fans");
            for (int i = 0; i < 10; i++) {
                feed.publish("friend", 0, 0, "update " + i);
            }
            String label = threshold == Integer.MAX_VALUE ? "push for everyone     "
                    : threshold == 0 ? "pull for everyone     " : "hybrid (celebrity>1000)";
            System.out.println(label + ": inbox writes = " + feed.inboxWrites()
                    + ", u7 sees " + feed.candidates("u7").size() + " posts");
        }
        System.out.println();
    }

    private static void demonstrateRanking() {
        System.out.println("--- Step 2: the same candidates under two ranking strategies ---");
        FdClock clock = new FdClock(T0);
        FdFollowGraph g = new FdFollowGraph();
        FdPostStore s = new FdPostStore();
        FdFeedService feed = newService(clock, g, s, 1_000);
        for (String a : List.of("ana", "ben", "cy")) {
            g.follow("viewer", a);
        }
        feed.publish("ana", 900, 120, "viral, 10h old");
        clock.advance(4 * 3600);
        feed.publish("ben", 40, 5, "solid, 6h old");
        clock.advance(5 * 3600);
        feed.publish("cy", 2, 0, "quiet, 1h old");
        clock.advance(3600);
        feed.publish("ana", 0, 0, "brand new");
        FdRankingStrategy[] strategies = {new FdChronological(), new FdEngagementDecay(1.5)};
        for (FdRankingStrategy r : strategies) {
            feed.setRanking(r);
            System.out.println(r.name() + ":");
            for (FdPost p : feed.rankAndFilter("viewer")) {
                System.out.printf("  %-16s by %-4s likes=%-4d comments=%-4d score=%s%n", p.text(), p.authorId(),
                        p.likes(), p.comments(), r instanceof FdChronological ? "t" + (p.createdAtSec() - T0)
                                : String.format("%.2f", r.score(p, "viewer", clock.now())));
            }
        }
        FdAffinityBoost boosted = new FdAffinityBoost(new FdEngagementDecay(1.5));
        for (int i = 0; i < 10; i++) {
            boosted.recordInteraction("viewer", "cy");
        }
        feed.setRanking(boosted);
        System.out.println(boosted.name() + " (viewer interacted with cy 10 times): "
                + texts(feed.rankAndFilter("viewer")));
        System.out.println();
    }

    private static void demonstrateFilters() {
        System.out.println("--- Step 3: filters after ranking: mute, then author diversity (max 2 in a row) ---");
        FdClock clock = new FdClock(T0);
        FdFollowGraph g = new FdFollowGraph();
        FdPostStore s = new FdPostStore();
        FdFeedService feed = newService(clock, g, s, 1_000);
        for (String a : List.of("ana", "ben", "cy")) {
            g.follow("viewer", a);
        }
        String[] order = {"ben", "cy", "ana", "ana", "ana", "ana", "ben", "ana"};
        for (String a : order) {
            clock.advance(60);
            feed.publish(a, 0, 0, a);
        }
        System.out.println("ranked (chronological): " + authors(feed.rankAndFilter("viewer")));
        FdMutedAuthorsFilter mute = new FdMutedAuthorsFilter();
        mute.mute("viewer", "cy");
        feed.addFilter(mute);
        feed.addFilter(new FdAuthorDiversityFilter(2));
        System.out.println("after mute(cy) + diversity: " + authors(feed.rankAndFilter("viewer")));
        System.out.println();
    }

    private static void demonstrateRankedPaging() {
        System.out.println("--- Step 4: ranked feed paged through a cached per-user snapshot ---");
        FdClock clock = new FdClock(T0);
        FdFollowGraph g = new FdFollowGraph();
        FdPostStore s = new FdPostStore();
        FdFeedCache cache = new FdFeedCache(2, 600, clock);
        FdFeedService feed = new FdFeedService(g, s, clock, 1_000, 500, cache);
        feed.setRanking(new FdEngagementDecay(1.5));
        g.follow("viewer", "ana");
        g.follow("viewer", "ben");
        for (int i = 0; i < 7; i++) {
            clock.advance(600);
            feed.publish(i % 2 == 0 ? "ana" : "ben", 10 * i, i, "p" + (i + 1));
        }
        FdPage p1 = feed.rankedPage("viewer", null, 3);
        System.out.println("page 1: " + texts(p1.items()) + "  cursor=" + p1.nextCursor());
        feed.publish("ben", 5_000, 900, "VIRAL");
        FdPage p2 = feed.rankedPage("viewer", p1.nextCursor(), 3);
        System.out.println("a viral post arrives; page 2: " + texts(p2.items())
                + "  (stable, no repeats, VIRAL waits for refresh)");
        FdPage fresh = feed.rankedPage("viewer", null, 3);
        System.out.println("pull-to-refresh: " + texts(fresh.items()));
        feed.rankedPage("u2", null, 3);
        feed.rankedPage("u3", null, 3);
        System.out.println("two more sessions with cache size 2 -> LRU evictions so far: " + cache.evictions());
        try {
            feed.rankedPage("viewer", p2.nextCursor(), 3);
        } catch (FdCursorExpiredException e) {
            System.out.println("old cursor: " + e.getMessage());
        }
        FdPage again = feed.rankedPage("viewer", null, 3);
        clock.advance(601);
        try {
            feed.rankedPage("viewer", again.nextCursor(), 3);
        } catch (FdCursorExpiredException e) {
            System.out.println("after the 600 s TTL: " + e.getMessage());
        }
        System.out.println();
    }

    private static void demonstrateStaleInbox() {
        System.out.println("--- Step 5: inbox entries go stale; the read path re-checks ---");
        FdClock clock = new FdClock(T0);
        FdFollowGraph g = new FdFollowGraph();
        FdPostStore s = new FdPostStore();
        FdFeedService feed = newService(clock, g, s, 1_000);
        g.follow("viewer", "ana");
        g.follow("viewer", "ben");
        feed.publish("ana", 0, 0, "ana-1");
        FdPost doomed = feed.publish("ben", 0, 0, "ben-1");
        feed.publish("ben", 0, 0, "ben-2");
        System.out.println("inbox ids: " + feed.inboxSize("viewer") + ", feed: " + texts(feed.rankAndFilter("viewer")));
        g.unfollow("viewer", "ana");
        s.delete(doomed.id());
        System.out.println("unfollow ana, delete ben-1 -> inbox still holds " + feed.inboxSize("viewer")
                + " ids, feed shows " + texts(feed.rankAndFilter("viewer")));
        System.out.println();
    }

    private static void demonstrateConcurrency() throws Exception {
        System.out.println("--- Step 6: 4 writers and 4 readers at once ---");
        FdClock clock = new FdClock(T0);
        FdFollowGraph g = new FdFollowGraph();
        FdPostStore s = new FdPostStore();
        FdFeedService feed = newService(clock, g, s, 1_000);
        for (int a = 0; a < 4; a++) {
            for (int u = 0; u < 300; u++) {
                g.follow("r" + u, "w" + a);
            }
        }
        AtomicBoolean writing = new AtomicBoolean(true);
        AtomicInteger pagesRead = new AtomicInteger();
        AtomicInteger pagesWithDuplicates = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<java.util.concurrent.Future<?>> writers = new ArrayList<>();
        for (int a = 0; a < 4; a++) {
            final String author = "w" + a;
            writers.add(pool.submit(() -> {
                for (int i = 0; i < 1_000; i++) {
                    clock.advance(1);
                    feed.publish(author, i % 50, i % 7, author + "-" + i);
                }
            }));
        }
        for (int r = 0; r < 4; r++) {
            final String reader = "r" + r;
            pool.submit(() -> {
                while (writing.get()) {
                    String cursor = null;
                    Set<Long> seen = new HashSet<>();
                    boolean dup = false;
                    do {
                        FdPage page = feed.chronologicalPage(reader, cursor, 20);
                        for (FdPost p : page.items()) {
                            dup |= !seen.add(p.id());
                        }
                        cursor = page.nextCursor();
                        pagesRead.incrementAndGet();
                    } while (cursor != null && seen.size() < 200);
                    if (dup) {
                        pagesWithDuplicates.incrementAndGet();
                    }
                }
            });
        }
        for (java.util.concurrent.Future<?> w : writers) {
            w.get();
        }
        writing.set(false);
        pool.shutdown();
        pool.awaitTermination(1, TimeUnit.MINUTES);
        System.out.println("posts written: 4,000; inbox writes: " + feed.inboxWrites() + " (4,000 x 300 followers)");
        System.out.println("inbox of r0 bounded at capacity 500? " + (feed.inboxSize("r0") == 500));
        System.out.println("reader scrolls that saw a duplicate post: " + pagesWithDuplicates.get()
                + "  (across " + (pagesRead.get() > 1_000 ? "1,000+" : "some") + " pages read during the writes)");
    }

    private static List<Long> ids(List<FdPost> posts) {
        return posts.stream().map(FdPost::id).toList();
    }

    private static List<String> texts(List<FdPost> posts) {
        return posts.stream().map(FdPost::text).toList();
    }

    private static String authors(List<FdPost> posts) {
        return String.join(" ", posts.stream().map(FdPost::authorId).toList());
    }

    private static int overlap(List<FdPost> a, List<FdPost> b) {
        Set<Long> ids = new HashSet<>(ids(a));
        int n = 0;
        for (FdPost p : b) {
            if (ids.contains(p.id())) {
                n++;
            }
        }
        return n;
    }
}
