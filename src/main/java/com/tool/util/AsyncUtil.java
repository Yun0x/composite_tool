package com.tool.util;

import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * @Author lachesism
 * @Date 2024-12-12 03:09
 **/
public class AsyncUtil {

    private static final ScheduledExecutorService executorService;

    // 默认线程池大小为 CPU 核心数 * 2
    static {
        int corePoolSize = Runtime.getRuntime().availableProcessors() * 2;
        executorService = Executors.newScheduledThreadPool(corePoolSize);
    }

    /**
     * 提交异步任务，不关心返回值。
     *
     * @param task 要执行的任务
     */
    public static void executeAsync(Runnable task) {
        executorService.submit(task);
    }

    /**
     * 提交异步任务，并返回结果。
     * 如果任务执行成功，返回结果；如果发生异常，返回默认值或空值。
     *
     * @param task 要执行的任务
     * @param <T> 返回值类型
     * @return 任务的结果
     */
    public static <T> CompletableFuture<T> executeAsync(Supplier<T> task) {
        return CompletableFuture.supplyAsync(task, executorService);
    }

    /**
     * 执行异步任务，设置超时，如果任务在超时前未完成，则抛出 TimeoutException。
     *
     * @param task     要执行的任务
     * @param timeout  超时时间
     * @param timeUnit 时间单位
     * @param <T>      返回值类型
     * @return 任务的结果
     * @throws TimeoutException 超时异常
     * @throws InterruptedException 中断异常
     */
    public static <T> T executeWithTimeout(Supplier<T> task, long timeout, TimeUnit timeUnit) throws TimeoutException, InterruptedException {
        CompletableFuture<T> future = CompletableFuture.supplyAsync(task, executorService);

        try {
            return future.get(timeout, timeUnit);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw e;
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw e;
        } catch (ExecutionException e) {
            future.cancel(true);
            throw new RuntimeException("Task execution failed", e);
        }
    }

    /**
     * 执行多个异步任务，等待所有任务完成。
     *
     * @param tasks 要执行的任务列表
     * @param <T>   返回值类型
     * @return 所有任务的结果
     */
    public static <T> CompletableFuture<Void> executeAllAsync(CompletableFuture<T>... tasks) {
        return CompletableFuture.allOf(tasks);
    }

    /**
     * 执行多个异步任务，等待任何一个任务完成。
     *
     * @param tasks 要执行的任务列表
     * @param <T>   返回值类型
     * @return 任意一个任务的结果
     */
    public static <T> CompletableFuture<T> executeAnyAsync(CompletableFuture<T>... tasks) {
        return CompletableFuture.anyOf(tasks).thenApply(result -> (T) result);
    }

    /**
     * 返回异步任务的默认值，如果任务执行失败或出现异常
     *
     * @param task 要执行的任务
     * @param defaultValue 默认值
     * @param <T> 返回值类型
     * @return 任务结果或默认值
     */
    public static <T> CompletableFuture<T> executeWithDefaultValue(Supplier<T> task, T defaultValue) {
        return CompletableFuture.supplyAsync(task, executorService)
                .exceptionally(ex -> defaultValue);
    }

    /**
     * 限制并发数的执行（使用 Semaphore 来限制并发数）
     *
     * @param task 要执行的任务
     * @param maxConcurrent 执行的最大并发数
     * @param <T> 返回值类型
     * @return 任务的结果
     */
    public static <T> CompletableFuture<T> executeWithConcurrencyLimit(Supplier<T> task, int maxConcurrent) {
        Semaphore semaphore = new Semaphore(maxConcurrent);
        return CompletableFuture.supplyAsync(() -> {
            try {
                semaphore.acquire();
                return task.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            } finally {
                semaphore.release();
            }
        }, executorService);
    }

    /**
     * 延迟执行任务
     *
     * @param task 要执行的任务
     * @param delay 延迟时间
     * @param timeUnit 时间单位
     * @param <T> 返回值类型
     * @return 延迟执行的任务结果
     */
    public static <T> CompletableFuture<T> executeWithDelay(Supplier<T> task, long delay, TimeUnit timeUnit) {
        CompletableFuture<T> future = new CompletableFuture<>();
        executorService.schedule(() -> future.complete(task.get()), delay, timeUnit);
        return future;
    }

    /**
     * 获取 CompletableFuture 的状态（是否已完成）
     *
     * @param future 需要检查的 CompletableFuture
     * @return 任务是否已完成
     */
    public static boolean isCompleted(CompletableFuture<?> future) {
        return future.isDone();
    }

    /**
     * 关闭线程池
     */
    public static void shutdown() {
        executorService.shutdown();
    }

    /**
     * 优雅地关闭线程池（等待已提交的任务执行完毕）
     */
    public static void shutdownGracefully() throws InterruptedException {
        executorService.shutdown();
        if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
            executorService.shutdownNow();
        }
    }
}
