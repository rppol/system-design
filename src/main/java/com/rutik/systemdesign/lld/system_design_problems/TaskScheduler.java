package com.rutik.systemdesign.lld.system_design_problems;

import java.util.PriorityQueue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

// =============================================================================
//  IN-PROCESS TASK SCHEDULER - Low-Level Design
//  Extracted verbatim from TaskScheduler.md's Implementation section.
//  Patterns used:
//    - Command  : ScheduledTask wraps the Runnable and is its own sort key
//    - Strategy : RetryPolicy (NoRetryPolicy / ExponentialBackoffRetryPolicy)
//    - Handle   : TaskHandle exposes cancel()/status() without leaking the task
//  One scheduler thread parks on a Condition for the exact delay to the head of
//  a PriorityQueue and hands due tasks to a fixed WorkerPool. insert() below is
//  the FIXED half of the page's lost-wakeup pair -- it signals whenever the new
//  task becomes the head, not only on the empty-to-nonempty transition. The
//  BROKEN insertBroken() variant is teaching material and is not carried here.
//  The page's TaskSchedulerDemo.main() lives on TaskScheduler itself, so the
//  file's namesake class is the entry point (house style for this folder).
// =============================================================================

enum Priority {
    LOW(0), NORMAL(1), HIGH(2), CRITICAL(3);

    final int weight;

    Priority(int weight) {
        this.weight = weight;
    }
}

enum TaskStatus {
    SCHEDULED, QUEUED, RUNNING, COMPLETED, FAILED, CANCELLED
}

interface RetryPolicy {
    boolean shouldRetry(int attemptNumber, Throwable lastFailure);
    long backoffMillis(int attemptNumber);
}

final class NoRetryPolicy implements RetryPolicy {
    @Override public boolean shouldRetry(int attemptNumber, Throwable lastFailure) {
        return false;
    }
    @Override public long backoffMillis(int attemptNumber) {
        return 0L;
    }
}

final class ExponentialBackoffRetryPolicy implements RetryPolicy {
    private final int maxAttempts;
    private final long baseMillis;

    public ExponentialBackoffRetryPolicy(int maxAttempts, long baseMillis) {
        this.maxAttempts = maxAttempts;
        this.baseMillis = baseMillis;
    }

    @Override public boolean shouldRetry(int attemptNumber, Throwable lastFailure) {
        return attemptNumber < maxAttempts;
    }

    @Override public long backoffMillis(int attemptNumber) {
        return baseMillis * (1L << Math.min(attemptNumber, 10)); // capped shift, avoids overflow
    }
}

final class ScheduledTask implements Comparable<ScheduledTask> {
    private static final AtomicLong SEQUENCE = new AtomicLong();

    final long id = SEQUENCE.getAndIncrement();
    final Runnable command;
    final Priority priority;
    final long periodNanos;    // 0 = one-shot
    final boolean fixedDelay;  // true = scheduleWithFixedDelay semantics
    final RetryPolicy retryPolicy;

    volatile long nextRunNanos;
    volatile TaskStatus status = TaskStatus.SCHEDULED;
    volatile Thread runningThread;
    volatile int attempt = 0;

    ScheduledTask(Runnable command, Priority priority, long nextRunNanos,
                  long periodNanos, boolean fixedDelay, RetryPolicy retryPolicy) {
        this.command = command;
        this.priority = priority;
        this.nextRunNanos = nextRunNanos;
        this.periodNanos = periodNanos;
        this.fixedDelay = fixedDelay;
        this.retryPolicy = retryPolicy;
    }

    boolean isPeriodic() {
        return periodNanos > 0;
    }

    @Override
    public int compareTo(ScheduledTask other) {
        int byTime = Long.compare(nextRunNanos, other.nextRunNanos);
        if (byTime != 0) {
            return byTime;
        }
        int byPriority = Integer.compare(other.priority.weight, priority.weight); // higher first
        if (byPriority != 0) {
            return byPriority;
        }
        return Long.compare(id, other.id); // FIFO among true ties — never return 0
    }
}

final class TaskHandle {
    private final ScheduledTask task;
    private final TaskScheduler scheduler;

    TaskHandle(ScheduledTask task, TaskScheduler scheduler) {
        this.task = task;
        this.scheduler = scheduler;
    }

    public boolean cancel(boolean mayInterruptIfRunning) {
        return scheduler.cancel(task, mayInterruptIfRunning);
    }

    public TaskStatus status() {
        return task.status;
    }
}

final class WorkerPool {
    private final ExecutorService executor;

    public WorkerPool(int size) {
        this.executor = Executors.newFixedThreadPool(size);
    }

    void dispatch(ScheduledTask task, TaskScheduler scheduler) {
        executor.execute(() -> run(task, scheduler));
    }

    private void run(ScheduledTask task, TaskScheduler scheduler) {
        if (task.status == TaskStatus.CANCELLED) {
            return; // cancelled after poll() removed it from the queue, before this ran
        }
        task.status = TaskStatus.RUNNING;
        task.runningThread = Thread.currentThread();
        try {
            task.command.run();
            task.status = TaskStatus.COMPLETED;
            task.attempt = 0;
        } catch (Throwable failure) {
            task.attempt++;
            if (task.retryPolicy.shouldRetry(task.attempt, failure)) {
                long backoffNanos = TimeUnit.MILLISECONDS.toNanos(task.retryPolicy.backoffMillis(task.attempt));
                task.nextRunNanos = System.nanoTime() + backoffNanos;
                task.status = TaskStatus.QUEUED;
                scheduler.reinsert(task);
                return;
            }
            task.status = TaskStatus.FAILED;
        } finally {
            task.runningThread = null;
        }
        if (task.status == TaskStatus.COMPLETED && task.isPeriodic() && !scheduler.isShutdown()) {
            // fixed-rate: next start is measured from the PREVIOUS scheduled start, so a slow
            // run causes the next one to fire immediately if it's already overdue (catch-up,
            // never concurrent, never skipped). fixed-delay: next start is measured from NOW,
            // so a slow run just pushes everything after it later, permanently.
            long base = task.fixedDelay ? System.nanoTime() : task.nextRunNanos;
            task.nextRunNanos = base + task.periodNanos;
            task.status = TaskStatus.QUEUED;
            scheduler.reinsert(task);
        }
    }

    void shutdown(boolean drain) {
        if (drain) {
            executor.shutdown();       // let queued/running work finish, refuse new work
        } else {
            executor.shutdownNow();    // interrupt running work, discard what hasn't started
        }
    }
}

public final class TaskScheduler {
    private final PriorityQueue<ScheduledTask> queue = new PriorityQueue<>();
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notEmpty = lock.newCondition();
    private final WorkerPool workers;
    private volatile boolean running = true;

    public TaskScheduler(int workerCount) {
        this.workers = new WorkerPool(workerCount);
        Thread schedulerThread = new Thread(this::loop, "task-scheduler");
        schedulerThread.start();
    }

    public TaskHandle schedule(Runnable command, long delayMs, Priority priority) {
        return schedule(command, delayMs, priority, 0, false, new NoRetryPolicy());
    }

    /**
     * Same as schedule(), but rounds the deadline DOWN to a whole millisecond so that
     * tasks asked for at the same millisecond compare equal on time -- which is the only
     * way compareTo()'s priority tie-break is ever reached. Without this, three calls with
     * the same delay produce three deadlines nanoseconds apart and run FIFO.
     */
    public TaskHandle scheduleAtInstant(Runnable command, long delayMs, Priority priority) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(delayMs);
        long quantized = deadline - Math.floorMod(deadline, 1_000_000L);   // floor to the ms
        ScheduledTask task =
            new ScheduledTask(command, priority, quantized, 0, false, new NoRetryPolicy());
        insert(task);
        return new TaskHandle(task, this);
    }

    public TaskHandle scheduleAtFixedRate(Runnable command, long initialDelayMs, long periodMs, Priority priority) {
        return schedule(command, initialDelayMs, priority, TimeUnit.MILLISECONDS.toNanos(periodMs), false, new NoRetryPolicy());
    }

    public TaskHandle scheduleWithFixedDelay(Runnable command, long initialDelayMs, long delayMs, Priority priority) {
        return schedule(command, initialDelayMs, priority, TimeUnit.MILLISECONDS.toNanos(delayMs), true, new NoRetryPolicy());
    }

    private TaskHandle schedule(Runnable command, long delayMs, Priority priority,
                                 long periodNanos, boolean fixedDelay, RetryPolicy retryPolicy) {
        long nextRunNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(delayMs);
        ScheduledTask task = new ScheduledTask(command, priority, nextRunNanos, periodNanos, fixedDelay, retryPolicy);
        insert(task);
        return new TaskHandle(task, this);
    }

    void reinsert(ScheduledTask task) {
        insert(task); // the same "signal if new head" rule applies to reschedules and retries
    }

    private void loop() {
        lock.lock();
        try {
            while (running) {
                while (queue.isEmpty() && running) {
                    notEmpty.await();
                }
                if (!running) {
                    break;
                }
                ScheduledTask head = queue.peek();
                long delayNanos = head.nextRunNanos - System.nanoTime();
                if (delayNanos <= 0) {
                    queue.poll();
                    workers.dispatch(head, this); // hand off immediately — never run the command here
                } else {
                    notEmpty.awaitNanos(delayNanos); // exact wait, wakes early if a nearer task arrives
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            lock.unlock();
        }
    }

    boolean cancel(ScheduledTask task, boolean mayInterruptIfRunning) {
        lock.lock();
        try {
            if (task.status == TaskStatus.QUEUED) {
                queue.remove(task); // O(n): no index tracks a task's heap position
                task.status = TaskStatus.CANCELLED;
                return true;
            }
        } finally {
            lock.unlock();
        }
        if (task.status == TaskStatus.RUNNING) {
            task.status = TaskStatus.CANCELLED;
            Thread runner = task.runningThread;
            if (mayInterruptIfRunning && runner != null) {
                runner.interrupt(); // cooperative — see Design Decisions
            }
            return true;
        }
        return false; // already COMPLETED, FAILED, or CANCELLED
    }

    boolean isShutdown() {
        return !running;
    }

    public void shutdown(boolean drain) {
        lock.lock();
        try {
            running = false;
            notEmpty.signalAll();
        } finally {
            lock.unlock();
        }
        workers.shutdown(drain);
    }

    // FIX — signal whenever the inserted task becomes the new head
    void insert(ScheduledTask task) {
        lock.lock();
        try {
            queue.offer(task);
            task.status = TaskStatus.QUEUED;
            if (queue.peek() == task) {
                // covers BOTH cases the broken version conflated: the queue was
                // empty (any insert is trivially the new head), AND a nearer
                // task displaced whatever the scheduler was actually sleeping on.
                notEmpty.signalAll();
            }
        } finally {
            lock.unlock();
        }
    }

    public static void main(String[] args) throws InterruptedException {
        TaskScheduler scheduler = new TaskScheduler(3);

        // Three tasks with the SAME delay argument still get three different deadlines:
        // each schedule() call computes System.nanoTime() + delay at call time, and the
        // calls are nanoseconds apart. compareTo() breaks ties on priority ONLY when
        // nextRunNanos is exactly equal, so this runs in submission order, not priority
        // order. That is the lesson: a nanosecond deadline essentially never ties.
        System.out.println("--- Same delay, three deadlines: FIFO, not priority ---");
        scheduler.schedule(() -> log("LOW      task ran"), 200, Priority.LOW);
        scheduler.schedule(() -> log("CRITICAL task ran"), 200, Priority.CRITICAL);
        scheduler.schedule(() -> log("NORMAL   task ran"), 200, Priority.NORMAL);
        Thread.sleep(400);

        // To make priority decide within a window, QUANTIZE the deadline so ties are
        // possible -- bucket to the millisecond, then equal-millisecond tasks tie and
        // compareTo()'s priority branch is reached.
        System.out.println("--- Quantized deadlines: priority now decides ---");
        scheduler.scheduleAtInstant(() -> log("LOW      task ran"), 200, Priority.LOW);
        scheduler.scheduleAtInstant(() -> log("CRITICAL task ran"), 200, Priority.CRITICAL);
        scheduler.scheduleAtInstant(() -> log("NORMAL   task ran"), 200, Priority.NORMAL);
        Thread.sleep(400);

        System.out.println("--- scheduleAtFixedRate under a run that overruns its period ---");
        scheduler.scheduleAtFixedRate(() -> {
            log("fixedRate run starting");
            sleepQuietly(150); // longer than the 100ms period below
        }, 0, 100, Priority.NORMAL);
        Thread.sleep(500);

        System.out.println("--- Cancel a queued task before it becomes due ---");
        TaskHandle queued = scheduler.schedule(() -> log("should never print"), 300, Priority.NORMAL);
        boolean cancelled = queued.cancel(false);
        log("cancel(false) returned " + cancelled + ", status=" + queued.status());
        Thread.sleep(400);

        System.out.println("--- Cancel a running task: cooperative interrupt ---");
        TaskHandle running = scheduler.schedule(() -> {
            try {
                for (int i = 0; i < 5; i++) {
                    Thread.sleep(100);
                    log("still working, step " + i);
                }
            } catch (InterruptedException e) {
                log("interrupted, stopping early");
                Thread.currentThread().interrupt();
            }
        }, 0, Priority.NORMAL);
        Thread.sleep(150);
        running.cancel(true);
        Thread.sleep(200);

        System.out.println("--- Graceful shutdown, draining in-flight work ---");
        scheduler.shutdown(true);
        log("shutdown requested, in-flight work allowed to finish");
    }

    private static void log(String message) {
        System.out.printf("[+%4dms] %s%n", System.nanoTime() / 1_000_000 % 100000, message);
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
