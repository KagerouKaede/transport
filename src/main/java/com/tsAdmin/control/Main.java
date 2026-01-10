package com.tsAdmin.control;

import java.util.Random;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.jfinal.core.JFinal;

import com.tsAdmin.common.ConfigLoader;
import com.tsAdmin.control.manager.CarManager;
import com.tsAdmin.control.manager.DemandManager;
import com.tsAdmin.control.manager.PoiManager;

public class Main
{
    public static Random RANDOM;
    private static DataUpdater updater;
    private static Thread updaterThread;
    private static volatile boolean isRunning = false;

    private static final Logger logger = LogManager.getLogger("App");

    public static void main(String[] args)
    {
        try
        {
            // 打开浏览器
            java.awt.Desktop.getDesktop().browse(new java.net.URI("http://localhost:8080"));
        }
        catch (Exception e)
        {
            logger.warn("Failed to open browser automatically", e);
        }

        logger.info("Server starts at http://localhost:8080");
        JFinal.start("src/main/webapp", 8080, "/", 5);
    }

    public static void start()
    {
        logger.info("Starting simulation...");
        
        // 检查是否已有线程在运行，如果有则先停止
        if (isRunning)
        {
            logger.warn("Simulation is already running. Stopping previous instance...");
            try
            {
                stop();
            }
            catch (InterruptedException e)
            {
                logger.error("Error while stopping previous simulation", e);
                Thread.currentThread().interrupt();
            }
        }

        RANDOM = new Random(ConfigLoader.getInt("Main.random_seed"));

        PoiManager.init();
        CarManager.init();
        DemandManager.init();

        // 创建新的 DataUpdater 实例
        updater = new DataUpdater();
        
        // 将 DataUpdater 作为独立线程运行，避免阻塞主线程
        updaterThread = new Thread(updater);
        // 设置为守护线程，主程序结束时自动结束
        updaterThread.setDaemon(true);
        updaterThread.start();
        
        // 更新运行状态
        isRunning = true;

        logger.info("Simulation started successfully, preset uuid: {}", ConfigLoader.getConfigUUID());
    }

    public static void stop() throws InterruptedException
    {
        logger.info("Stopping simulation...");

        if (updater != null)
        {
            updater.stop();
        }

        if (updaterThread != null && updaterThread.isAlive())
        {
            updaterThread.join(5000);
            if (updaterThread.isAlive())
            {
                logger.warn("DataUpdater thread did not stop within timeout");
                // 强制中断线程
                updaterThread.interrupt();
            }
        }

        PoiManager.onStop();
        CarManager.onStop();
        DemandManager.onStop();
        isRunning = false;

        logger.info("Simulation stopped successfully");
    }
}