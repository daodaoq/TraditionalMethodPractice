package org.java;

public interface RejectHandle {

    void reject(Runnable rejectCommand, MyThreadPool threadPool);
}
