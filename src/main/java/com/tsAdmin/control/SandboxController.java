package com.tsAdmin.control;

import java.util.Map;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.jfinal.core.Controller;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.tsAdmin.common.ConfigLoader;

/**
 * 配置控制器
 * 主要处理前端对参数配置的更新
 */
public class SandboxController extends Controller
{
    private static final Logger logger = LogManager.getLogger(SandboxController.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private void reply(boolean success, String message)
    {
        renderJson(Map.of("success", success, "message", message));
    }

    /**
     * 按照已应用的预设开始模拟
     * <p>返回数据格式：{"success":{@code boolean}, "message":{@code String}}
     */
    public void startSimulation()
    {
        try
        {
            Main.start();
            reply(true, "Simulation started successfully");
        }
        catch (Exception e)
        {
            logger.error("Failed to start simulation", e);
            reply(false, "Failed to start simulation, please check log to learn more");
        }
    }

    public void stopSimulation()
    {
        try
        {
            Main.stop();
            reply(true, "Simulation stopped successfully");
        }
        catch (Exception e)
        {
            logger.error("Failed to stop simulation", e);
            reply(false, "Failed to stop simulation, please check log to learn more");
        }
    }

    /**
     * 获取默认配置
     * <p>数据返回格式：{"success":{@code boolean}, "message":{@code String}}；
     * 其中，成功时返回的 message 内容为：{"UUID":{@code String}, "content":{@code String(Json)}}
     */
    public void getDefaultConfig()
    {
        ConfigLoader.use("0");
        String config = ConfigLoader.getFullJson().toString();

        try
        {
            reply(true, objectMapper.writeValueAsString(Map.of("UUID", "0", "content", config)));
        }
        catch (Exception e)
        {
            logger.error("Failed to get default config", e);
            reply(false, "Failed to get default config, please check log to learn more");
        }
    }

    /**
     * 获取所有预设
     * <p>返回数据格式：[{"UUID":{@code String}, "content":{@code String(Json)}}, {...}, ...]
     */
    public void getAllPresets()
    {
        renderJson(DBManager.getPresetList());
    }

    /**
     * 应用预设，需要传入预设的 UUID
     * <p>返回数据格式：{"success":{@code boolean}, "message":{@code String}}
     */
    public void applyPreset()
    {
        String uuid = getPara("UUID");
        if (ConfigLoader.use(uuid))
        {
            reply(true, "Preset applied successfully");
        }
        else
        {
            logger.warn("Failed to apply preset(UUID: {})", uuid);
            reply(false, "Failed to apply preset(UUID:" + uuid + "), please check log to learn more");
        }
    }

    /**
     * 保存预设，如果是新预设，只需要传入content（内容格式同resources/config.json）；如果是已存在预设，还需传入该预设的 UUID
     * <p>返回数据格式：{"success":{@code boolean}, "message":{@code String}}；
     * 其中，成功时返回的 message 内容为：{"UUID":{@code String}}
     */
    public void savePreset()
    {
        String uuid = getPara("UUID");
        boolean isNew = false;
        if (uuid == null || uuid.isEmpty())
        {
            uuid = UUID.randomUUID().toString().replace("-", "");
            isNew = true;
        }

        try
        {
            String content = getPara("content");
            if (content == null || content.isEmpty())
            {
                logger.warn("Content of preset(UUID: {}) is null or empty", uuid);
            }

            boolean success = DBManager.savePreset(isNew, uuid, content);

            // 为防止修改了当前预设但在使用时因 UUID 相同而跳过，需要重载当前配置
            if (!isNew && uuid == ConfigLoader.getConfigUUID() && success)
            {
                ConfigLoader.use(uuid, true);
            }

            reply(success, objectMapper.writeValueAsString(Map.of("UUID", uuid)));
        }
        catch (Exception e)
        {
            logger.error("Failed to save preset(UUID: {})", uuid, e);
            reply(false, "Failed to save preset, please check log to learn more");
        }
    }

    /**
     * 删除预设，需传入该预设的 UUID
     * <p>返回数据格式：{"success":{@code boolean}, "message":{@code String}}
     */
    public void rmvPreset()
    {
        String uuid = getPara("UUID");
        if (uuid == null || uuid.isEmpty())
        {
            logger.error("UUID of the preset to be removed is null or empty");
            uuid = "Unknown";
        }

        boolean success = DBManager.rmvPreset(uuid);

        // 若删除当前所应用的预设，则恢复为默认预设
        if (uuid == ConfigLoader.getConfigUUID() && success)
        {
            ConfigLoader.use("0");
        }

        reply(success, success ? "Preset removed successfully" : "Failed to remove preset");
    }
}
