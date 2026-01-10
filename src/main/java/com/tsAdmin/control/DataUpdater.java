package com.tsAdmin.control;

import com.tsAdmin.common.ConfigLoader;
import com.tsAdmin.control.manager.DemandManager;
import com.tsAdmin.control.manager.PoiManager;
import com.tsAdmin.control.scheduler.*;

public class DataUpdater implements Runnable
{
    private volatile boolean running = true;
    private static int UPDATE_INTERVAL;

    private static MOSAScheduler scheduler = new MOSAScheduler();

    public static MOSAScheduler getScheduler() { return scheduler; }

    public void stop() { running = false; }

    @Override
    public void run()
    {
        // s => ms
        UPDATE_INTERVAL = ConfigLoader.getInt("Main.update_interval", 5) * 1000;

        while (running)
        {
            long start = System.currentTimeMillis();

            // 这里开始数据更新逻辑

            PoiManager.update();

            if (!DemandManager.isEmpty())
            {
                scheduler.schedule();
            }

            // 这里结束数据更新逻辑

            long cost = System.currentTimeMillis() - start;
            long sleep = UPDATE_INTERVAL - cost;
            if (sleep > 0)
            {
                try
                {
                    Thread.sleep(sleep);
                }
                catch (InterruptedException e)
                {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }
}
