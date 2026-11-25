package com.rutik.systemdesign.lld.system_design_problems;

import java.math.BigInteger;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.regex.Pattern;

// =============================================================================
//  URL SHORTENER - Low-Level Design
//
//  The HLD case study (hld/case_studies/design_url_shortener.md) decides the
//  fleet: counter service, storage, CDN. This file is the class design inside
//  one service instance:
//    - CodeGenerator strategy: counter + Base62, scrambled counter, hash, random
//    - IdBlockAllocator hands out id ranges so many instances never collide
//    - UrlRepository with an atomic saveIfAbsent (a unique constraint in a DB)
//    - CachingUrlRepository decorator for the read path
//    - A chain of ValidationRules, an AliasPolicy, a RedirectListener observer
//
//  Patterns used:
//    - Strategy  : CodeGenerator, RedirectMode
//    - Decorator : CachingUrlRepository wraps any UrlRepository
//    - Chain of Responsibility : ValidationRule list
//    - Observer  : RedirectListener (click counting)
//    - Repository + Facade : UrlRepository, UrlShortenerService
// =============================================================================

public class UrlShortener {

    // -------------------------------------------------------------------------
    //  BASE62
    // -------------------------------------------------------------------------

    static final class Base62 {
        static final String ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        static final int CODE_LENGTH = 7;
        static final long SPACE = pow62(CODE_LENGTH);           // 62^7 = 3,521,614,606,208

        static long pow62(int n) {
            long r = 1;
            for (int i = 0; i < n; i++) {
                r = Math.multiplyExact(r, 62);
            }
            return r;
        }

        static String encode(long n, int width) {
            if (n < 0) {
                throw new IllegalArgumentException("negative id");
            }
            char[] out = new char[width];
            for (int i = width - 1; i >= 0; i--) {
                out[i] = ALPHABET.charAt((int) (n % 62));
                n /= 62;
            }
            if (n != 0) {
                throw new IllegalArgumentException("id does not fit in " + width + " chars");
            }
            return new String(out);
        }

        static long decode(String code) {
            long n = 0;
            for (char c : code.toCharArray()) {
                int d = ALPHABET.indexOf(c);
                if (d < 0) {
                    throw new IllegalArgumentException("not base62: " + c);
                }
                n = n * 62 + d;
            }
            return n;
        }
    }

    // -------------------------------------------------------------------------
    //  DOMAIN
    // -------------------------------------------------------------------------

    record ShortUrl(String code, String longUrl, String owner, Instant createdAt, Instant expiresAt,
                    boolean customAlias) {
        boolean expired(Instant now) {
            return expiresAt != null && !now.isBefore(expiresAt);
        }
    }

    record ShortenRequest(String longUrl, String owner, String alias, Duration ttl) {
        static ShortenRequest of(String longUrl, String owner) {
            return new ShortenRequest(longUrl, owner, null, null);
        }
    }

    record Resolution(int status, String location) { }

    static final class InvalidUrlException extends RuntimeException {
        InvalidUrlException(String msg) {
            super(msg);
        }
    }

    static final class AliasUnavailableException extends RuntimeException {
        AliasUnavailableException(String msg) {
            super(msg);
        }
    }

    interface TimeSource {
        Instant now();
    }

    static final class MutableTime implements TimeSource {
        private volatile Instant now = Instant.parse("2026-11-25T00:00:00Z");

        @Override
        public Instant now() {
            return now;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }
    }

    // -------------------------------------------------------------------------
    //  STRATEGY - code generation
    // -------------------------------------------------------------------------

    interface CodeGenerator {
        /** attempt is 0 for the first try and grows on each collision. */
        String generate(String longUrl, int attempt);
    }

    /** Shared range allocator: the stand-in for a counter row or a ZooKeeper node. */
    static final class IdBlockAllocator {
        private final AtomicLong nextBlockStart = new AtomicLong(1);
        private final int blockSize;
        final AtomicInteger blocksIssued = new AtomicInteger();

        IdBlockAllocator(int blockSize) {
            this.blockSize = blockSize;
        }

        long[] claim() {
            blocksIssued.incrementAndGet();
            long start = nextBlockStart.getAndAdd(blockSize);
            return new long[] {start, start + blockSize};
        }
    }

    /** Counter + Base62. Each instance draws ids from its own block, no network call per id. */
    static class CounterGenerator implements CodeGenerator {
        private final IdBlockAllocator allocator;
        private long next;
        private long end;

        CounterGenerator(IdBlockAllocator allocator) {
            this.allocator = allocator;
        }

        synchronized long nextId() {
            if (next == end) {
                long[] block = allocator.claim();
                next = block[0];
                end = block[1];
            }
            return next++;
        }

        @Override
        public String generate(String longUrl, int attempt) {
            return Base62.encode(nextId(), Base62.CODE_LENGTH);
        }
    }

    /**
     * Same counter, but id -> (id * P) mod 62^7 before encoding. P is coprime to
     * 62^7 = 2^7 * 31^7, so the map is a bijection: no collisions, no sequence.
     */
    static final class ScrambledCounterGenerator extends CounterGenerator {
        private static final BigInteger P = BigInteger.valueOf(1_000_000_007L);   // prime, not 2 or 31
        private static final BigInteger SPACE = BigInteger.valueOf(Base62.SPACE);

        ScrambledCounterGenerator(IdBlockAllocator allocator) {
            super(allocator);
        }

        @Override
        public String generate(String longUrl, int attempt) {
            long scrambled = BigInteger.valueOf(nextId()).multiply(P).mod(SPACE).longValueExact();
            return Base62.encode(scrambled, Base62.CODE_LENGTH);
        }
    }

    /** Hash of the URL, truncated. Deterministic, so collisions need a salted retry. */
    static final class HashGenerator implements CodeGenerator {
        private final int length;

        HashGenerator(int length) {
            this.length = length;
        }

        @Override
        public String generate(String longUrl, int attempt) {
            try {
                MessageDigest sha = MessageDigest.getInstance("SHA-256");
                byte[] digest = sha.digest((longUrl + "#" + attempt).getBytes(StandardCharsets.UTF_8));
                long bits = ByteBuffer.wrap(digest).getLong() & Long.MAX_VALUE;
                return Base62.encode(bits % Base62.pow62(length), length);
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    // -------------------------------------------------------------------------
    //  REPOSITORY - saveIfAbsent is the only write and it is atomic
    // -------------------------------------------------------------------------

    interface UrlRepository {
        boolean saveIfAbsent(ShortUrl url);

        Optional<ShortUrl> findByCode(String code);

        Optional<ShortUrl> findByOwnerAndUrl(String owner, String longUrl);
    }

    static final class InMemoryUrlRepository implements UrlRepository {
        private final Map<String, ShortUrl> byCode = new ConcurrentHashMap<>();
        private final Map<String, String> byOwnerUrl = new ConcurrentHashMap<>();
        final LongAdder reads = new LongAdder();

        @Override
        public boolean saveIfAbsent(ShortUrl url) {
            if (byCode.putIfAbsent(url.code(), url) != null) {
                return false;                                   // unique violation on code
            }
            if (!url.customAlias()) {
                byOwnerUrl.putIfAbsent(url.owner() + "|" + url.longUrl(), url.code());
            }
            return true;
        }

        @Override
        public Optional<ShortUrl> findByCode(String code) {
            reads.increment();
            return Optional.ofNullable(byCode.get(code));
        }

        @Override
        public Optional<ShortUrl> findByOwnerAndUrl(String owner, String longUrl) {
            String code = byOwnerUrl.get(owner + "|" + longUrl);
            return code == null ? Optional.empty() : Optional.ofNullable(byCode.get(code));
        }

        int size() {
            return byCode.size();
        }
    }

    /** DECORATOR - read-through LRU in front of any repository. Codes never change, so no invalidation. */
    static final class CachingUrlRepository implements UrlRepository {
        private final UrlRepository delegate;
        private final Map<String, ShortUrl> lru;
        final LongAdder hits = new LongAdder();
        final LongAdder misses = new LongAdder();

        CachingUrlRepository(UrlRepository delegate, int capacity) {
            this.delegate = delegate;
            this.lru = new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, ShortUrl> eldest) {
                    return size() > capacity;
                }
            };
        }

        @Override
        public boolean saveIfAbsent(ShortUrl url) {
            return delegate.saveIfAbsent(url);
        }

        @Override
        public Optional<ShortUrl> findByCode(String code) {
            synchronized (lru) {
                ShortUrl cached = lru.get(code);
                if (cached != null) {
                    hits.increment();
                    return Optional.of(cached);
                }
            }
            misses.increment();
            Optional<ShortUrl> loaded = delegate.findByCode(code);
            loaded.ifPresent(u -> {
                synchronized (lru) {
                    lru.put(code, u);
                }
            });
            return loaded;
        }

        @Override
        public Optional<ShortUrl> findByOwnerAndUrl(String owner, String longUrl) {
            return delegate.findByOwnerAndUrl(owner, longUrl);
        }
    }

    // -------------------------------------------------------------------------
    //  CHAIN OF RESPONSIBILITY - URL validation
    // -------------------------------------------------------------------------

    interface ValidationRule {
        void check(URI uri, String raw);
    }

    static List<ValidationRule> defaultRules(String ownHost, Set<String> blockedHosts) {
        return List.of(
                (uri, raw) -> {
                    if (raw.length() > 2_048) {
                        throw new InvalidUrlException("longer than 2,048 chars");
                    }
                },
                (uri, raw) -> {
                    String s = uri.getScheme();
                    if (s == null || !(s.equals("http") || s.equals("https"))) {
                        throw new InvalidUrlException("scheme must be http or https: " + raw);
                    }
                },
                (uri, raw) -> {
                    if (uri.getHost() == null) {
                        throw new InvalidUrlException("no host: " + raw);
                    }
                },
                (uri, raw) -> {
                    if (uri.getHost().equalsIgnoreCase(ownHost)) {
                        throw new InvalidUrlException("redirect loop to " + ownHost);
                    }
                },
                (uri, raw) -> {
                    if (blockedHosts.contains(uri.getHost().toLowerCase())) {
                        throw new InvalidUrlException("blocked host " + uri.getHost());
                    }
                });
    }

    static final class AliasPolicy {
        private static final Pattern SHAPE = Pattern.compile("[A-Za-z0-9_-]{4,30}");
        private static final Set<String> RESERVED = Set.of("api", "admin", "login", "health", "static");

        void check(String alias) {
            if (!SHAPE.matcher(alias).matches()) {
                throw new AliasUnavailableException("alias must match " + SHAPE.pattern() + ": " + alias);
            }
            if (RESERVED.contains(alias.toLowerCase())) {
                throw new AliasUnavailableException("alias is reserved: " + alias);
            }
        }
    }

    // -------------------------------------------------------------------------
    //  OBSERVER - redirect events
    // -------------------------------------------------------------------------

    interface RedirectListener {
        void onRedirect(ShortUrl url, Instant at);
    }

    static final class ClickCounter implements RedirectListener {
        private final Map<String, LongAdder> clicks = new ConcurrentHashMap<>();

        @Override
        public void onRedirect(ShortUrl url, Instant at) {
            clicks.computeIfAbsent(url.code(), c -> new LongAdder()).increment();
        }

        long clicks(String code) {
            LongAdder a = clicks.get(code);
            return a == null ? 0 : a.sum();
        }
    }

    /** STRATEGY - 302 keeps every click visible to the service, 301 lets browsers cache it. */
    enum RedirectMode {
        PERMANENT_301(301), TEMPORARY_302(302);

        final int status;

        RedirectMode(int status) {
            this.status = status;
        }
    }

    // -------------------------------------------------------------------------
    //  FACADE - the service
    // -------------------------------------------------------------------------

    static final class UrlShortenerService {
        private final CodeGenerator generator;
        private final UrlRepository repository;
        private final List<ValidationRule> rules;
        private final AliasPolicy aliasPolicy = new AliasPolicy();
        private final RedirectMode redirectMode;
        private final TimeSource time;
        private final List<RedirectListener> listeners = new CopyOnWriteArrayList<>();
        private final int maxAttempts;
        final LongAdder collisions = new LongAdder();

        UrlShortenerService(CodeGenerator generator, UrlRepository repository, List<ValidationRule> rules,
                            RedirectMode redirectMode, TimeSource time, int maxAttempts) {
            this.generator = generator;
            this.repository = repository;
            this.rules = rules;
            this.redirectMode = redirectMode;
            this.time = time;
            this.maxAttempts = maxAttempts;
        }

        void addListener(RedirectListener l) {
            listeners.add(l);
        }

        ShortUrl shorten(ShortenRequest req) {
            URI uri;
            try {
                uri = URI.create(req.longUrl());
            } catch (IllegalArgumentException e) {
                throw new InvalidUrlException("unparseable: " + req.longUrl());
            }
            for (ValidationRule r : rules) {
                r.check(uri, req.longUrl());
            }
            Instant now = time.now();
            Instant expiresAt = req.ttl() == null ? null : now.plus(req.ttl());

            if (req.alias() != null) {
                aliasPolicy.check(req.alias());
                ShortUrl u = new ShortUrl(req.alias(), req.longUrl(), req.owner(), now, expiresAt, true);
                if (!repository.saveIfAbsent(u)) {
                    throw new AliasUnavailableException("alias already taken: " + req.alias());
                }
                return u;
            }
            if (req.ttl() == null) {
                Optional<ShortUrl> existing = repository.findByOwnerAndUrl(req.owner(), req.longUrl());
                if (existing.isPresent()) {
                    return existing.get();                       // same owner, same URL: same code
                }
            }
            for (int attempt = 0; attempt < maxAttempts; attempt++) {
                String code = generator.generate(req.longUrl(), attempt);
                ShortUrl u = new ShortUrl(code, req.longUrl(), req.owner(), now, expiresAt, false);
                if (repository.saveIfAbsent(u)) {
                    return u;
                }
                collisions.increment();
            }
            throw new IllegalStateException("no free code after " + maxAttempts + " attempts");
        }

        Resolution resolve(String code) {
            Optional<ShortUrl> found = repository.findByCode(code);
            if (found.isEmpty()) {
                return new Resolution(404, null);
            }
            ShortUrl u = found.get();
            Instant now = time.now();
            if (u.expired(now)) {
                return new Resolution(410, null);
            }
            for (RedirectListener l : listeners) {
                l.onRedirect(u, now);
            }
            return new Resolution(redirectMode.status, u.longUrl());
        }
    }

    // -------------------------------------------------------------------------
    //  DEMO
    // -------------------------------------------------------------------------

    public static void main(String[] args) throws Exception {
        System.out.println("========================================");
        System.out.println("   URL Shortener - LLD Demo");
        System.out.println("========================================");

        System.out.println("\n--- 1. Base62 ---");
        System.out.println("  62^7 = " + String.format("%,d", Base62.SPACE) + " codes of length 7");
        System.out.println("  encode(125)   = " + Base62.encode(125, 7) + "   decode -> " + Base62.decode("0000021"));
        System.out.println("  encode(62^7-1)= " + Base62.encode(Base62.SPACE - 1, 7));

        MutableTime time = new MutableTime();
        List<ValidationRule> rules = defaultRules("sho.rt", Set.of("malware.example"));
        InMemoryUrlRepository db = new InMemoryUrlRepository();
        CachingUrlRepository cached = new CachingUrlRepository(db, 10_000);
        IdBlockAllocator allocator = new IdBlockAllocator(1_000);
        UrlShortenerService plain = new UrlShortenerService(new CounterGenerator(allocator), cached, rules,
                RedirectMode.TEMPORARY_302, time, 5);
        ClickCounter clicks = new ClickCounter();
        plain.addListener(clicks);

        System.out.println("\n--- 2. Plain counter: short, unique, and guessable ---");
        for (int i = 1; i <= 3; i++) {
            System.out.println("  " + plain.shorten(ShortenRequest.of("https://example.com/page/" + i, "u1")).code());
        }

        System.out.println("\n--- 3. Scrambled counter: same ids, no visible sequence ---");
        IdBlockAllocator scrambledIds = new IdBlockAllocator(1_000);
        UrlShortenerService scrambled = new UrlShortenerService(new ScrambledCounterGenerator(scrambledIds),
                new InMemoryUrlRepository(), rules, RedirectMode.TEMPORARY_302, time, 5);
        for (int i = 1; i <= 3; i++) {
            System.out.println("  id " + i + " -> "
                    + scrambled.shorten(ShortenRequest.of("https://example.com/page/" + i, "u1")).code());
        }

        System.out.println("\n--- 4. Same owner, same URL -> same code ---");
        ShortUrl a = plain.shorten(ShortenRequest.of("https://example.com/docs", "u1"));
        ShortUrl b = plain.shorten(ShortenRequest.of("https://example.com/docs", "u1"));
        ShortUrl c = plain.shorten(ShortenRequest.of("https://example.com/docs", "u2"));
        System.out.println("  u1 first=" + a.code() + " u1 again=" + b.code() + " u2=" + c.code());

        System.out.println("\n--- 5. Validation chain and alias policy ---");
        for (String bad : List.of("javascript:alert(1)", "https://sho.rt/0000001", "http://malware.example/x")) {
            try {
                plain.shorten(ShortenRequest.of(bad, "u1"));
            } catch (InvalidUrlException e) {
                System.out.println("  rejected: " + e.getMessage());
            }
        }
        System.out.println("  alias ok: " + plain.shorten(
                new ShortenRequest("https://example.com/launch", "u1", "launch-2026", null)).code());
        for (String alias : List.of("launch-2026", "admin", "a b")) {
            try {
                plain.shorten(new ShortenRequest("https://example.com/other", "u2", alias, null));
            } catch (AliasUnavailableException e) {
                System.out.println("  rejected: " + e.getMessage());
            }
        }

        System.out.println("\n--- 6. Hash generator with a tiny 2-char space: collisions are retried ---");
        UrlShortenerService hashed = new UrlShortenerService(new HashGenerator(2), new InMemoryUrlRepository(),
                rules, RedirectMode.TEMPORARY_302, time, 20);
        for (int i = 0; i < 200; i++) {
            hashed.shorten(ShortenRequest.of("https://example.com/item/" + i, "u1"));
        }
        System.out.println("  200 URLs into 62^2 = 3,844 codes: collisions retried = " + hashed.collisions.sum()
                + " (birthday estimate ~ 200^2 / (2 x 3,844) = 5.2)");

        System.out.println("\n--- 7. Redirects, cache, clicks, expiry ---");
        ShortUrl promo = plain.shorten(new ShortenRequest("https://example.com/promo", "u1", null,
                Duration.ofDays(1)));
        for (int i = 0; i < 1_000; i++) {
            plain.resolve(promo.code());
        }
        System.out.println("  resolve x1000 -> " + plain.resolve(promo.code()));
        System.out.println("  cache hits=" + cached.hits.sum() + ", misses=" + cached.misses.sum()
                + ", clicks=" + clicks.clicks(promo.code()));
        time.advance(Duration.ofDays(2));
        System.out.println("  after 2 days -> " + plain.resolve(promo.code()));
        System.out.println("  unknown code -> " + plain.resolve("zzzzzzz"));

        System.out.println("\n--- 8. Two instances, 4 threads each, 10,000 links per thread ---");
        IdBlockAllocator shared = new IdBlockAllocator(1_000);
        InMemoryUrlRepository fleetDb = new InMemoryUrlRepository();
        UrlShortenerService instanceA = new UrlShortenerService(new CounterGenerator(shared), fleetDb, rules,
                RedirectMode.TEMPORARY_302, time, 5);
        UrlShortenerService instanceB = new UrlShortenerService(new CounterGenerator(shared), fleetDb, rules,
                RedirectMode.TEMPORARY_302, time, 5);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch go = new CountDownLatch(1);
        for (int t = 0; t < 8; t++) {
            UrlShortenerService svc = t < 4 ? instanceA : instanceB;
            int worker = t;
            pool.submit(() -> {
                go.await();
                for (int i = 0; i < 10_000; i++) {
                    svc.shorten(ShortenRequest.of("https://example.com/w" + worker + "/" + i, "load"));
                }
                return null;
            });
        }
        go.countDown();
        pool.shutdown();
        pool.awaitTermination(60, TimeUnit.SECONDS);
        System.out.println("  links stored=" + fleetDb.size() + ", collisions="
                + (instanceA.collisions.sum() + instanceB.collisions.sum())
                + ", id blocks claimed=" + shared.blocksIssued.get());

        System.out.println("\n--- 9. Eight users race for one alias ---");
        AtomicInteger winners = new AtomicInteger();
        List<String> losers = new ArrayList<>();
        ExecutorService racers = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        for (int t = 0; t < 8; t++) {
            String owner = "racer-" + t;
            racers.submit(() -> {
                start.await();
                try {
                    plain.shorten(new ShortenRequest("https://example.com/sale", owner, "black-friday", null));
                    winners.incrementAndGet();
                } catch (AliasUnavailableException e) {
                    synchronized (losers) {
                        losers.add(owner);
                    }
                }
                return null;
            });
        }
        start.countDown();
        racers.shutdown();
        racers.awaitTermination(10, TimeUnit.SECONDS);
        System.out.println("  winners=" + winners.get() + ", rejected=" + losers.size());

        System.out.println("\n========================================");
        System.out.println("              Demo complete");
        System.out.println("========================================");
    }
}
