package com.cloudstore.server.messaging;

import com.cloudstore.server.service.impl.CartServiceImpl;

import java.util.concurrent.CountDownLatch;

/**
 * Standalone entrypoint for asynchronous order processing.
 */
public class OrderWorkerApplication {

    public static void main(String[] args) throws Exception {
        CountDownLatch shutdownLatch = new CountDownLatch(1);
        OrderWorker worker = new OrderWorker(new CartServiceImpl());

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutdown signal received. Closing OrderWorker...");
            worker.close();
            shutdownLatch.countDown();
            System.out.println("OrderWorker stopped.");
        }, "OrderWorker-Shutdown-Hook"));

        worker.run();
        shutdownLatch.await();
    }
}
