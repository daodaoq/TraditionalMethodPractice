package org.java;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class MyThreadPool {

    // 核心线程数
    public final int corePoolSize;

    // 最大线程数
    public final int maxPoolSize;

    // 非核心线程存活时间
    public final long timeOut;

    // 时间单位
    public final TimeUnit timeUnit;

    // 阻塞队列
    public final BlockingQueue<Runnable> blockingQueue;

    // 拒绝策略
    public final RejectHandle rejectHandle;

    // 线程计数（线程安全）
    private final AtomicInteger totalThreads = new AtomicInteger(0);
    // 核心线程数统计
    private final AtomicInteger coreThreads = new AtomicInteger(0);

    // 用 CopyOnWriteArrayList，避免多线程 remove/add 时并发问题
    private final List<Thread> coreList = new CopyOnWriteArrayList<>();
    private final List<Thread> supportList = new CopyOnWriteArrayList<>();

    public MyThreadPool(int corePoolSize,
                        int maxPoolSize,
                        long timeOut,
                        TimeUnit timeUnit,
                        BlockingQueue<Runnable> blockingQueue,
                        RejectHandle rejectHandle) {
        if (corePoolSize < 0 || maxPoolSize <= 0 || maxPoolSize < corePoolSize || timeOut < 0) {
            throw new IllegalArgumentException("参数非法");
        }
        this.corePoolSize = corePoolSize;
        this.maxPoolSize = maxPoolSize;
        this.timeOut = timeOut;
        this.timeUnit = timeUnit;
        this.blockingQueue = blockingQueue;
        this.rejectHandle = rejectHandle;
    }

    public void execute(Runnable command) {
        if (command == null) throw new NullPointerException("command == null");

        // 1. 核心线程数未满 → 创建核心线程，任务直接作为它的第一个任务
        if (coreThreads.get() < corePoolSize) {
            if (coreThreads.incrementAndGet() <= corePoolSize) {  // 双重校验防止并发超额
                Thread t = new CoreThread(command);
                coreList.add(t);
                t.start();
                return;
            }
            coreThreads.decrementAndGet();
        }

        // 2. 尝试入队
        if (blockingQueue.offer(command)) {
            return;
        }

        // 3. 队列满，且总线程数未达上限 → 创建非核心线程，任务直接交给它
        if (totalThreads.get() < maxPoolSize) {
            if (totalThreads.incrementAndGet() <= maxPoolSize) {
                Thread t = new SupportThread(command);
                supportList.add(t);
                t.start();
                return;
            }
            totalThreads.decrementAndGet();
        }

        // 4. 达到上限 → 走拒绝策略
        rejectHandle.reject(command, this);
    }

    /**
     * 核心线程：任务执行完就阻塞在 take()，永不退出（除非被中断）
     */
    class CoreThread extends Thread {
        private Runnable firstTask;

        CoreThread(Runnable firstTask) {
            this.firstTask = firstTask;
        }

        @Override
        public void run() {
            Runnable task = firstTask;
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    if (task == null) {
                        task = blockingQueue.take();   // 阻塞等待新任务
                    }
                    try {
                        task.run();
                    } catch (RuntimeException e) {
                        // 单个任务抛异常不影响线程继续工作
                        e.printStackTrace();
                    }
                    task = null;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                coreList.remove(this);
                coreThreads.decrementAndGet();
                totalThreads.decrementAndGet();
                System.out.println(Thread.currentThread().getName() + " 核心线程结束");
            }
        }
    }

    /**
     * 非核心线程：队列空闲超过 timeOut 就退出并释放
     */
    class SupportThread extends Thread {
        private Runnable firstTask;

        SupportThread(Runnable firstTask) {
            this.firstTask = firstTask;
        }

        @Override
        public void run() {
            Runnable task = firstTask;
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    if (task == null) {
                        task = blockingQueue.poll(timeOut, timeUnit);
                        if (task == null) {
                            break;   // 超时，回收
                        }
                    }
                    try {
                        task.run();
                    } catch (RuntimeException e) {
                        e.printStackTrace();
                    }
                    task = null;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                supportList.remove(this);
                totalThreads.decrementAndGet();
                System.out.println(Thread.currentThread().getName() + " 非核心线程结束");
            }
        }
    }
}