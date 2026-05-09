package com.rutik.systemdesign.lld.concurrency_patterns; /**
 * THREAD POOL PATTERN
 *
 * Problem: Creating a new Thread for every task is expensive.
 *          Thread pools reuse a fixed set of worker threads,
 *          queuing excess tasks until a thread is free.
 *
 * Benefits:
 *   - Bounded resource usage (thread creation/teardown is expensive)
 *   - Better throughput (reusing threads avoids creation overhead)
 *   - Backpressure via bounded queue
 *   - Rejection policies for overload handling
 *
 * Four implementations:
 *   1. Custom SimpleThreadPool (educational — shows the mechanism)
 *   2. Java Executor Framework (production — ThreadPoolExecutor)
 *   3. Web server request handler (real-world)
 *   4. Fork/Join pool for parallel divide-and-conquer
 */

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

// ══════════════════════════════════════════════════════════════
// IMPLEMENTATION 1: Custom SimpleThreadPool (educational)
// ══════════════════════════════════════════════════════════════

class SimpleThreadPool {
    private final BlockingQueue<Runnable> taskQueue;
    private final List<WorkerThread> workers;
    private volatile boolean shutdown = false;
    private final AtomicInteger completedTasks = new AtomicInteger();

    public SimpleThreadPool(int poolSize, int queueCapacity) {
        taskQueue = new ArrayBlockingQueue<>(queueCapacity);
        workers = new ArrayList<>(poolSize);

        for (int i = 0; i < poolSize; i++) {
            WorkerThread worker = new WorkerThread("Worker-" + i);
            workers.add(worker);
            worker.start();
        }
        System.out.printf("  [Pool] Started with %d workers, queue capacity=%d%n", poolSize, queueCapacity);
    }

    public void submit(Runnable task) {
        if (shutdown) throw new RejectedExecutionException("Thread pool is shut down");
        boolean offered = taskQueue.offer(task);
        if (!offered) throw new RejectedExecutionException("Task queue is full");
    }

    public void shutdown() {
        shutdown = true;
        workers.forEach(Thread::interrupt);
    }

    public void awaitTermination(long timeoutMs) throws InterruptedException {
        for (WorkerThread w : workers) {
            w.join(timeoutMs);
        }
    }

    public int getCompletedTaskCount() { return completedTasks.get(); }

    private class WorkerThread extends Thread {
        WorkerThread(String name) { super(name); }

        @Override
        public void run() {
            while (!shutdown || !taskQueue.isEmpty()) {
                try {
                    Runnable task = taskQueue.poll(200, TimeUnit.MILLISECONDS);
                    if (task != null) {
                        task.run();
                        completedTasks.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    System.out.println("  [Worker] Task threw exception: " + e.getMessage());
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════
// IMPLEMENTATION 2: Java Executor Framework
// ══════════════════════════════════════════════════════════════

class ExecutorFrameworkDemo {

    public static void demonstrate() throws InterruptedException, ExecutionException {
        System.out.println("\n=== Java Executor Framework ===");

        // ── Fixed thread pool ──
        System.out.println("\n-- FixedThreadPool (4 threads) --");
        ExecutorService fixed = Executors.newFixedThreadPool(4);
        for (int i = 1; i <= 6; i++) {
            final int taskId = i;
            fixed.submit(() -> {
                System.out.printf("  [FixedPool] Task %d running on %s%n",
                        taskId, Thread.currentThread().getName());
                try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            });
        }
        fixed.shutdown();
        fixed.awaitTermination(2, TimeUnit.SECONDS);

        // ── Future and Callable ──
        System.out.println("\n-- Future<T> and Callable<T> --");
        ExecutorService executor = Executors.newFixedThreadPool(3);
        List<Future<Integer>> futures = new ArrayList<>();

        for (int i = 1; i <= 5; i++) {
            final int x = i;
            Future<Integer> future = executor.submit(() -> {
                Thread.sleep(50);
                return x * x;  // returns x squared
            });
            futures.add(future);
        }

        System.out.print("  Squares: ");
        for (Future<Integer> f : futures) {
            System.out.print(f.get() + " ");  // blocks until result available
        }
        System.out.println();
        executor.shutdown();

        // ── CompletableFuture for async pipelines ──
        System.out.println("\n-- CompletableFuture async pipeline --");
        CompletableFuture<String> pipeline = CompletableFuture
                .supplyAsync(() -> {
                    System.out.println("  [CF] Step 1: Fetching user...");
                    return "user_123";
                })
                .thenApplyAsync(userId -> {
                    System.out.println("  [CF] Step 2: Loading profile for " + userId);
                    return "Profile{name=Alice, score=95}";
                })
                .thenApplyAsync(profile -> {
                    System.out.println("  [CF] Step 3: Enriching " + profile);
                    return profile + " [enriched]";
                })
                .exceptionally(ex -> "Error: " + ex.getMessage());

        System.out.println("  [CF] Result: " + pipeline.get());

        // ── ThreadPoolExecutor with full configuration ──
        System.out.println("\n-- Custom ThreadPoolExecutor --");
        ThreadPoolExecutor customPool = new ThreadPoolExecutor(
                2,              // corePoolSize: keep 2 threads alive
                8,              // maximumPoolSize: burst up to 8 threads
                30,             // keepAliveTime: extra threads die after 30s idle
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(20),        // bounded work queue
                Executors.defaultThreadFactory(),
                new ThreadPoolExecutor.CallerRunsPolicy() // rejection: caller thread runs it
        );

        System.out.printf("  Created pool: core=%d, max=%d%n",
                customPool.getCorePoolSize(), customPool.getMaximumPoolSize());

        for (int i = 0; i < 5; i++) {
            final int id = i;
            customPool.submit(() -> {
                System.out.printf("  [Custom] Task %d on %s (active=%d, queue=%d)%n",
                        id, Thread.currentThread().getName(),
                        customPool.getActiveCount(), customPool.getQueue().size());
            });
        }
        customPool.shutdown();
        customPool.awaitTermination(2, TimeUnit.SECONDS);
        System.out.printf("  Completed tasks: %d%n", customPool.getCompletedTaskCount());
    }
}

// ══════════════════════════════════════════════════════════════
// IMPLEMENTATION 3: Web Server Request Handler
// ══════════════════════════════════════════════════════════════

class HttpRequest {
    private final int requestId;
    private final String method;
    private final String path;

    public HttpRequest(int id, String method, String path) {
        this.requestId = id;
        this.method = method;
        this.path = path;
    }

    @Override
    public String toString() {
        return String.format("Request{id=%d, %s %s}", requestId, method, path);
    }
}

class HttpResponse {
    private final int statusCode;
    private final String body;

    HttpResponse(int status, String body) {
        this.statusCode = status;
        this.body = body;
    }

    @Override
    public String toString() {
        return String.format("Response{%d, body='%s'}", statusCode, body);
    }
}

class RequestHandler implements Runnable {
    private final HttpRequest request;

    RequestHandler(HttpRequest request) { this.request = request; }

    @Override
    public void run() {
        // Simulate request processing
        try {
            Thread.sleep(50); // DB query, business logic, etc.
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        HttpResponse response = new HttpResponse(200, "OK for " + request.getPath());
        System.out.printf("  [%s] Handled %s → %s%n",
                Thread.currentThread().getName(), request, response);
    }
}

class SimpleWebServer {
    private final ThreadPoolExecutor requestPool;
    private final AtomicInteger requestCounter = new AtomicInteger();

    public SimpleWebServer(int coreThreads, int maxThreads, int queueSize) {
        this.requestPool = new ThreadPoolExecutor(
                coreThreads, maxThreads,
                60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queueSize),
                r -> new Thread(r, "http-worker-" + requestCounter.incrementAndGet()),
                (r, executor) -> System.out.println("  [Server] REJECTED: queue full, request dropped!")
        );
        System.out.printf("  [Server] Started. core=%d max=%d queue=%d%n",
                coreThreads, maxThreads, queueSize);
    }

    public void handleRequest(HttpRequest request) {
        try {
            requestPool.execute(new RequestHandler(request));
        } catch (RejectedExecutionException e) {
            System.out.println("  [Server] Server too busy: " + request);
        }
    }

    public void printStats() {
        System.out.printf("  [Server] Pool stats: active=%d, completed=%d, queueSize=%d%n",
                requestPool.getActiveCount(),
                requestPool.getCompletedTaskCount(),
                requestPool.getQueue().size());
    }

    public void shutdown() throws InterruptedException {
        requestPool.shutdown();
        requestPool.awaitTermination(5, TimeUnit.SECONDS);
    }
}

// ══════════════════════════════════════════════════════════════
// IMPLEMENTATION 4: Fork/Join Pool for parallel computation
// ══════════════════════════════════════════════════════════════

class ParallelMergeSort extends RecursiveAction {
    private static final int THRESHOLD = 16; // below this, use sequential sort
    private final int[] array;
    private final int left;
    private final int right;

    public ParallelMergeSort(int[] array, int left, int right) {
        this.array = array;
        this.left  = left;
        this.right = right;
    }

    @Override
    protected void compute() {
        if (right - left <= THRESHOLD) {
            // Base case: sort sequentially
            Arrays.sort(array, left, right + 1);
            return;
        }
        int mid = (left + right) / 2;

        // Fork: create subtasks and execute them in parallel
        ParallelMergeSort leftTask  = new ParallelMergeSort(array, left, mid);
        ParallelMergeSort rightTask = new ParallelMergeSort(array, mid + 1, right);

        leftTask.fork();   // schedule left task asynchronously
        rightTask.compute(); // compute right task in current thread
        leftTask.join();   // wait for left task to finish

        // Merge sorted halves
        merge(array, left, mid, right);
    }

    private void merge(int[] arr, int l, int m, int r) {
        int[] temp = Arrays.copyOfRange(arr, l, r + 1);
        int i = 0, j = m - l + 1, k = l;
        while (i <= m - l && j <= r - l) {
            arr[k++] = temp[i] <= temp[j] ? temp[i++] : temp[j++];
        }
        while (i <= m - l) arr[k++] = temp[i++];
        while (j <= r - l) arr[k++] = temp[j++];
    }
}

class ParallelSum extends RecursiveTask<Long> {
    private static final int THRESHOLD = 1000;
    private final long[] numbers;
    private final int start;
    private final int end;

    public ParallelSum(long[] numbers, int start, int end) {
        this.numbers = numbers;
        this.start   = start;
        this.end     = end;
    }

    @Override
    protected Long compute() {
        if (end - start <= THRESHOLD) {
            long sum = 0;
            for (int i = start; i < end; i++) sum += numbers[i];
            return sum;
        }
        int mid = (start + end) / 2;
        ParallelSum left  = new ParallelSum(numbers, start, mid);
        ParallelSum right = new ParallelSum(numbers, mid, end);
        left.fork();
        long rightResult = right.compute();
        long leftResult  = left.join();
        return leftResult + rightResult;
    }
}

// ─────────────────────────────────────────────────────────────
// Main Demo
// ─────────────────────────────────────────────────────────────

class ThreadPoolDemo {
    public static void main(String[] args) throws InterruptedException, ExecutionException {

        System.out.println("=== Custom SimpleThreadPool ===");
        SimpleThreadPool pool = new SimpleThreadPool(3, 10);
        for (int i = 1; i <= 8; i++) {
            final int id = i;
            pool.submit(() -> {
                System.out.printf("  [SimplePool] Task %d on %s%n", id, Thread.currentThread().getName());
                try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            });
        }
        Thread.sleep(800);
        pool.shutdown();
        pool.awaitTermination(2000);
        System.out.printf("  Completed: %d tasks%n", pool.getCompletedTaskCount());

        ExecutorFrameworkDemo.demonstrate();

        System.out.println("\n=== Web Server Thread Pool ===");
        SimpleWebServer server = new SimpleWebServer(2, 5, 10);
        for (int i = 1; i <= 8; i++) {
            server.handleRequest(new HttpRequest(i, "GET", "/api/users/" + i));
        }
        Thread.sleep(1000);
        server.printStats();
        server.shutdown();

        System.out.println("\n=== Fork/Join Pool ===");
        ForkJoinPool forkJoinPool = ForkJoinPool.commonPool();

        // Parallel sort
        int[] data = new Random().ints(100, 0, 10000).toArray();
        System.out.println("  Before sort (first 10): " + Arrays.toString(Arrays.copyOf(data, 10)));
        forkJoinPool.invoke(new ParallelMergeSort(data, 0, data.length - 1));
        System.out.println("  After sort  (first 10): " + Arrays.toString(Arrays.copyOf(data, 10)));

        // Parallel sum
        long[] numbers = new long[100_000];
        for (int i = 0; i < numbers.length; i++) numbers[i] = i + 1;
        long sum = forkJoinPool.invoke(new ParallelSum(numbers, 0, numbers.length));
        System.out.printf("  Sum of 1..100000 = %d (expected: %d)%n",
                sum, (long) 100000 * 100001 / 2);
    }
}
