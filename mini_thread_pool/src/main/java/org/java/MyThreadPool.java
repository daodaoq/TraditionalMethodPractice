package org.java;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

public class MyThreadPool {

    // 核心线程数
    private final int corePoolSize;

    // 最大线程数
    private final int maxPoolSize;

    // 非核心线程存活时间
    private final int timeOut;

    // 时间单位
    private final TimeUnit timeUnit;

    // 阻塞队列
    BlockingQueue<Runnable> blockingQueue;

    // 拒绝策略
    private final RejectHandle rejectHandle;

    public MyThreadPool(int corePoolSize, int maxPoolSize, int timeOut, TimeUnit timeUnit, BlockingQueue<Runnable> blockingQueue, RejectHandle rejectHandle) {
        this.corePoolSize = corePoolSize;
        this.maxPoolSize = maxPoolSize;
        this.timeOut = timeOut;
        this.timeUnit = timeUnit;
        this.blockingQueue = blockingQueue;
        this.rejectHandle = rejectHandle;
    }

    // 核心线程
    List<Thread> coreList = new ArrayList<>();

    // 非核心线程
    List<Thread> supportList = new ArrayList<>();

    // 1.判断 thread list 中有多少个元素，如果没有到 core pool size 那么就创建线程
    void execute(Runnable command) {
        if (coreList.size() < corePoolSize) {
            Thread thread = new CoreThread();
            coreList.add(thread);
            thread.start();
        }

        if (blockingQueue.offer(command)) {
            return;
        }

        if (coreList.size() + supportList.size() < maxPoolSize) {
            Thread thread = new SupportThread();
            supportList.add(thread);
            thread.start();
        }

        if (!blockingQueue.offer(command)) {
            // throw new RuntimeException("阻塞队列满了！");
            rejectHandle.reject(command, this);
        }
    }

    class CoreThread extends Thread {
        @Override
        public void run() {
            while (true) {
                try {
                    Runnable command = blockingQueue.take();
                    command.run();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    class SupportThread extends Thread {
        @Override
        public void run() {
            while (true) {
                try {
                    Runnable command = blockingQueue.poll(timeOut, timeUnit);
                    if (command == null) {
                        break;
                    }
                    command.run();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            System.out.println(Thread.currentThread().getName() + "线程结束了！");
        }
    }
}
