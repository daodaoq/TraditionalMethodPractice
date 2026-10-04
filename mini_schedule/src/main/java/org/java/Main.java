package org.java;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Main {
    public static void main(String[] args) throws InterruptedException {
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss SSS");
        ScheduleService scheduleService = new ScheduleService();
        scheduleService.schedule(() -> {
            System.out.println(LocalDateTime.now().format(dateTimeFormatter) + "这是 100 毫秒一次的");
        }, 100);

        Thread.sleep(1000);

        System.out.println("添加一个每 200 毫秒打印一个 2 的定时任务");

        scheduleService.schedule(() -> {
            System.out.println(LocalDateTime.now().format(dateTimeFormatter) + "这是 200 毫秒一次的");
        }, 200);
    }
}