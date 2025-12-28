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
    private static DataUpdater updater = new DataUpdater();
    private static Thread updaterThread;
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

        RANDOM = new Random(ConfigLoader.getInt("Main.random_seed"));

        PoiManager.init();
        CarManager.init();
        DemandManager.init();

        // 将 DataUpdater 作为独立线程运行，避免阻塞主线程
        updaterThread = new Thread(updater);
        // 设置为守护线程，主程序结束时自动结束
        updaterThread.setDaemon(true);
        updaterThread.start();

        logger.info("Simulation started successfully, preset uuid: {}", ConfigLoader.getConfigUUID());
    }

    public static void stop() throws InterruptedException
    {
        logger.info("Stopping simulation...");

        // 停止 DataUpdater 线程
        if (updater != null)
        {
            updater.stop();
        }

        // 等待线程结束（最多等待5秒）
        if (updaterThread != null && updaterThread.isAlive())
        {
            updaterThread.join(5000);
            if (updaterThread.isAlive())
            {
                logger.warn("DataUpdater thread did not stop within timeout");
            }
        }
        
        // 保存各个 Manager 的数据
        PoiManager.onStop();
        CarManager.onStop();
        DemandManager.onStop();
        
        logger.info("Simulation stopped successfully");
    }
}