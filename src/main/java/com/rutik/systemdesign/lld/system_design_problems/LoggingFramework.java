package com.rutik.systemdesign.lld.system_design_problems;

import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

public final class LoggingFramework {

    // ---------- Level ----------
    public enum LogLevel {
        TRACE, DEBUG, INFO, WARN, ERROR, FATAL;

        boolean isEnabledFor(LogLevel threshold) {
            return this.ordinal() >= threshold.ordinal();
        }
    }

    // ---------- Record ----------
    public static final class LogRecord {
        final String loggerName;
        final LogLevel level;
        final String message;
        final long timestampMs;
        final String threadName;
        final Throwable throwable;

        LogRecord(String loggerName, LogLevel level, String message, Throwable throwable) {
            this.loggerName = loggerName;
            this.level = level;
            this.message = message;
            this.timestampMs = System.currentTimeMillis();
            this.threadName = Thread.currentThread().getName();
            this.throwable = throwable;
        }
    }

    // ---------- Formatter (Strategy) ----------
    public interface Formatter {
        String format(LogRecord r);
    }

    public static final class PlainTextFormatter implements Formatter {
        private static final DateTimeFormatter TS =
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        public String format(LogRecord r) {
            String ts = Instant.ofEpochMilli(r.timestampMs).atZone(java.time.ZoneOffset.UTC).format(TS);
            String base = ts + " " + r.level + " [" + r.threadName + "] " + r.loggerName + " - " + r.message;
            return r.throwable == null ? base : base + " | " + r.throwable;
        }
    }

    public static final class JsonFormatter implements Formatter {
        public String format(LogRecord r) {
            return "{\"ts\":" + r.timestampMs
                    + ",\"level\":\"" + r.level + "\""
                    + ",\"logger\":\"" + r.loggerName + "\""
                    + ",\"thread\":\"" + r.threadName + "\""
                    + ",\"msg\":\"" + r.message.replace("\"", "#quot;") + "\"}";
        }
    }

    // ---------- Appender ----------
    public interface Appender {
        void append(LogRecord r);
        default void close() { }
    }

    public static final class ConsoleAppender implements Appender {
        private final LogLevel threshold;
        private final Formatter formatter;

        public ConsoleAppender(LogLevel threshold, Formatter formatter) {
            this.threshold = threshold;
            this.formatter = formatter;
        }

        public synchronized void append(LogRecord r) {
            if (!r.level.isEnabledFor(threshold)) return;
            System.out.println(formatter.format(r));
        }
    }

    public static final class FileAppender implements Appender {
        private final LogLevel threshold;
        private final Formatter formatter;
        private BufferedWriter writer;
        private long currentBytes;

        public FileAppender(LogLevel threshold, Formatter formatter, Path path) {
            this.threshold = threshold;
            this.formatter = formatter;
            try {
                this.writer = Files.newBufferedWriter(path, StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        public synchronized void append(LogRecord r) {
            if (!r.level.isEnabledFor(threshold)) return;
            String line = formatter.format(r);
            try {
                writer.write(line);
                writer.newLine();
                writer.flush();
                currentBytes += line.length() + 1;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        public synchronized void close() {
            try { writer.close(); } catch (IOException ignored) { }
        }
    }

    /** Rolling variant: check-and-rollover happens inside the same synchronized append(). */
    public static final class RollingFileAppender implements Appender {
        private final LogLevel threshold;
        private final Formatter formatter;
        private final long maxBytesPerFile;
        private final String baseName;
        private BufferedWriter writer;
        private long currentBytes;
        private int fileIndex = 0;

        public RollingFileAppender(LogLevel threshold, Formatter formatter, String baseName, long maxBytesPerFile) {
            this.threshold = threshold;
            this.formatter = formatter;
            this.baseName = baseName;
            this.maxBytesPerFile = maxBytesPerFile;
            openNewFile();
        }

        private void openNewFile() {
            try {
                if (writer != null) writer.close();
                writer = Files.newBufferedWriter(Path.of(baseName + "." + fileIndex + ".log"),
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                currentBytes = 0;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        public synchronized void append(LogRecord r) {
            if (!r.level.isEnabledFor(threshold)) return;
            // check-then-act: safe only because it's inside this same lock
            if (currentBytes >= maxBytesPerFile) {
                fileIndex++;
                openNewFile();
            }
            String line = formatter.format(r);
            try {
                writer.write(line);
                writer.newLine();
                writer.flush();
                currentBytes += line.length() + 1;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        public synchronized void close() {
            try { writer.close(); } catch (IOException ignored) { }
        }
    }

    // ---------- Async decorator ----------
    public enum DropPolicy { BLOCK, DROP_OLDEST, DROP_NEWEST }

    public static final class AsyncAppender implements Appender {
        private final Appender delegate;
        private final BlockingQueue<LogRecord> queue;
        private final DropPolicy dropPolicy;
        private final Thread worker;
        private volatile boolean running = true;

        public AsyncAppender(Appender delegate, int capacity, DropPolicy dropPolicy) {
            this.delegate = delegate;
            this.queue = new ArrayBlockingQueue<>(capacity);
            this.dropPolicy = dropPolicy;
            this.worker = new Thread(this::drain, "async-appender-worker");
            this.worker.setDaemon(true);
            this.worker.start();
        }

        public void append(LogRecord r) {
            switch (dropPolicy) {
                case BLOCK -> {
                    try {
                        queue.put(r);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                case DROP_NEWEST -> queue.offer(r); // silently no-op if full
                case DROP_OLDEST -> {
                    if (!queue.offer(r)) {
                        queue.poll();
                        queue.offer(r);
                    }
                }
            }
        }

        private void drain() {
            while (running || !queue.isEmpty()) {
                try {
                    LogRecord r = queue.poll(200, TimeUnit.MILLISECONDS);
                    if (r != null) delegate.append(r);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        public void close() {
            running = false;
            try { worker.join(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            delegate.close();
        }
    }

    // ---------- LoggerConfig ----------
    static final class LoggerConfig {
        volatile LogLevel level; // null means "inherit"
        final List<Appender> appenders = new CopyOnWriteArrayList<>();
        volatile boolean additive = true;
    }

    // ---------- Logger (Chain of Responsibility) ----------
    public static final class Logger {
        private final String name;
        private final Logger parent;
        private final LoggerConfig config = new LoggerConfig();

        Logger(String name, Logger parent) {
            this.name = name;
            this.parent = parent;
        }

        private LogLevel effectiveLevel() {
            for (Logger l = this; l != null; l = l.parent) {
                if (l.config.level != null) return l.config.level;
            }
            return LogLevel.INFO; // root default
        }

        private List<Appender> effectiveAppenders() {
            List<Appender> result = new ArrayList<>();
            for (Logger l = this; l != null; l = l.parent) {
                result.addAll(l.config.appenders);
                if (!l.config.additive) break;
            }
            return result;
        }

        public boolean isEnabledFor(LogLevel level) {
            return level.isEnabledFor(effectiveLevel());
        }

        private void log(LogLevel level, String message, Throwable t) {
            if (!isEnabledFor(level)) return; // cheapest possible exit
            LogRecord record = new LogRecord(name, level, message, t);
            for (Appender a : effectiveAppenders()) {
                a.append(record);
            }
        }

        public void trace(String msg) { log(LogLevel.TRACE, msg, null); }
        public void debug(String msg) { log(LogLevel.DEBUG, msg, null); }
        public void info(String msg)  { log(LogLevel.INFO, msg, null); }
        public void warn(String msg)  { log(LogLevel.WARN, msg, null); }
        public void error(String msg) { log(LogLevel.ERROR, msg, null); }
        public void error(String msg, Throwable t) { log(LogLevel.ERROR, msg, t); }

        /** Lazy variant: the Supplier is never invoked unless the level is enabled. */
        public void debug(Supplier<String> messageSupplier) {
            if (!isEnabledFor(LogLevel.DEBUG)) return;
            log(LogLevel.DEBUG, messageSupplier.get(), null);
        }

        /** SLF4J-style parameterized call: formatting is skipped entirely when disabled. */
        public void info(String template, Object... args) {
            if (!isEnabledFor(LogLevel.INFO)) return;
            log(LogLevel.INFO, formatTemplate(template, args), null);
        }

        private static String formatTemplate(String template, Object[] args) {
            StringBuilder sb = new StringBuilder();
            int argIdx = 0, i = 0;
            while (i < template.length()) {
                if (i + 1 < template.length() && template.charAt(i) == '{' && template.charAt(i + 1) == '}') {
                    sb.append(argIdx < args.length ? String.valueOf(args[argIdx++]) : "{}");
                    i += 2;
                } else {
                    sb.append(template.charAt(i++));
                }
            }
            return sb.toString();
        }
    }

    // ---------- LoggerFactory (registry, Singleton in spirit) ----------
    public static final class LoggerFactory {
        private static final ConcurrentHashMap<String, Logger> REGISTRY = new ConcurrentHashMap<>();
        private static final Logger ROOT = new Logger("root", null);

        static {
            ROOT.config.level = LogLevel.INFO;
            REGISTRY.put("root", ROOT);
        }

        private LoggerFactory() { }

        public static Logger getLogger(String name) {
            return REGISTRY.computeIfAbsent(name, n -> new Logger(n, resolveParent(n)));
        }

        private static Logger resolveParent(String name) {
            int dot = name.lastIndexOf('.');
            if (dot < 0) return ROOT;
            String parentName = name.substring(0, dot);
            return REGISTRY.containsKey(parentName)
                    ? REGISTRY.get(parentName)
                    : getLogger(parentName); // recursively materialize ancestors
        }

        public static void setLevel(String name, LogLevel level) {
            getLogger(name).config.level = level;
        }

        public static void addAppender(String name, Appender appender) {
            getLogger(name).config.appenders.add(appender);
        }
    }

    // ---------- Demo ----------
    public static void main(String[] args) throws Exception {
        System.out.println("========================================");
        System.out.println("   Logging Framework — LLD Demo");
        System.out.println("========================================\n");

        LoggerFactory.setLevel("root", LogLevel.INFO);
        LoggerFactory.addAppender("root", new ConsoleAppender(LogLevel.INFO, new PlainTextFormatter()));

        Path tmp = Files.createTempFile("app", ".log");
        Appender asyncFile = new AsyncAppender(
                new FileAppender(LogLevel.DEBUG, new JsonFormatter(), tmp),
                /* capacity */ 1024, DropPolicy.DROP_OLDEST);
        LoggerFactory.setLevel("com.app.service", LogLevel.DEBUG);
        LoggerFactory.addAppender("com.app.service", asyncFile);

        Logger paymentLogger = LoggerFactory.getLogger("com.app.service.PaymentService");

        System.out.println("--- Effective level for com.app.service.PaymentService: inherited DEBUG ---");
        paymentLogger.debug("Loading payment gateway config"); // reaches file only, console is INFO
        paymentLogger.info("Charged {} to card ending {}", 42.00, "4242");
        paymentLogger.warn("Retrying gateway call, attempt 2");

        System.out.println("\n--- Disabled-level guard: this Supplier body never runs ---");
        Logger noisyLogger = LoggerFactory.getLogger("com.app.service.noisy");
        LoggerFactory.setLevel("com.app.service.noisy", LogLevel.INFO);
        noisyLogger.debug(() -> {
            System.out.println("  [SIDE EFFECT] this line proves the supplier ran — it should NOT print");
            return "expensive dump";
        });
        System.out.println("(nothing printed above — the DEBUG guard rejected the call before evaluating the supplier)");

        System.out.println("\n--- Runtime level change ---");
        LoggerFactory.setLevel("com.app.service.noisy", LogLevel.DEBUG);
        noisyLogger.debug(() -> "now visible after raising the level at runtime");

        Thread.sleep(300); // let the async worker drain before we read the file back
        System.out.println("\n--- What actually landed in the async JSON file appender ---");
        for (String line : Files.readAllLines(tmp)) {
            System.out.println(line);
        }

        ((AsyncAppender) asyncFile).close();
        Files.deleteIfExists(tmp);

        System.out.println("\n========================================");
        System.out.println("              Demo complete");
        System.out.println("========================================");
    }
}
