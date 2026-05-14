package com.tsadmin.transport;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class TransportApplication
{
    public static void main(String[] args)
    {
        SpringApplication.run(TransportApplication.class, args);
    }

    // public static void start()
    // {
    //     logger.info("Starting simulation...");
        
    //     // 检查是否已有线程在运行，如果有则先停止
    //     if (isRunning)
    //     {
    //         logger.warn("Simulation is already running. Stopping previous instance...");
    //         try
    //         {
    //             stop();
    //         }
    //         catch (InterruptedException e)
    //         {
    //             logger.error("Error while stopping previous simulation", e);
    //             Thread.currentThread().interrupt();
    //         }
    //     }

    //     // 更新运行状态
    //     isRunning = true;

    //     logger.info("Simulation started successfully, preset uuid: {}", ConfigLoader.getConfigUUID());
    // }

    // public static void stop() throws InterruptedException
    // {
    //     logger.info("Stopping simulation...");

    //     // if (updater != null)
    //     // {
    //     //     updater.stop();
    //     // }

    //     // if (updaterThread != null && updaterThread.isAlive())
    //     // {
    //     //     updaterThread.join(5000);
    //     //     if (updaterThread.isAlive())
    //     //     {
    //     //         logger.warn("DataUpdater thread did not stop within timeout");
    //     //         // 强制中断线程
    //     //         updaterThread.interrupt();
    //     //     }
    //     // }

    //     isRunning = false;

    //     logger.info("Simulation stopped successfully");
    // }
}
