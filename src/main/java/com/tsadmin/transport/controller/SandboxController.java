package com.tsadmin.transport.controller;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tsadmin.transport.TransportApplication;
import com.tsadmin.transport.config.ConfigLoader;
// import com.tsadmin.transport.dao.DBManager;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 配置控制器
 * 主要处理前端对参数配置的更新
 */
@RestController
@RequestMapping("/api/sandbox")
public class SandboxController
{
    private static final Logger logger = LogManager.getLogger(SandboxController.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private Map<String, String> reply(boolean success, String message)
    {
        return Map.of("success", String.valueOf(success), "message", message);
    }

    /**
     * 开始模拟，需要传入沙箱的 UUID
     * <p>返回数据格式：{"success":{@code boolean}, "message":{@code String}}
     */
    @PostMapping("/start")
    public void startSimulation(@RequestBody String uuid)
    {
        // 应用沙箱
        if (!ConfigLoader.use(uuid))
        {
            logger.warn("Failed to apply sandbox(UUID: {})", uuid);
            reply(false, "Failed to apply sandbox(UUID:" + uuid + "), please check log to learn more");
            return;
        }

        try
        {
            // 按照已应用沙箱配置开始仿真
            TransportApplication.start();
            reply(true, "Simulation started successfully");
        }
        catch (Exception e)
        {
            logger.error("Failed to start simulation", e);
            reply(false, "Failed to start simulation, please check log to learn more");
        }
    }

    /** 停止模拟 */
    @PostMapping("/stop")
    public void stopSimulation()
    {
        try
        {
            TransportApplication.stop();
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
     * <p>数据返回格式：{"success":{@code boolean}, "message":{@code String}}
     */
    @GetMapping("configTemplate")
    public Map<String, String> getConfigTemplate()
    {
        ConfigLoader.use("0");
        String config = ConfigLoader.getFullJson().toString();

        try
        {
            return reply(true, config);
        }
        catch (Exception e)
        {
            logger.error("Failed to get default config", e);
            return reply(false, "Failed to get default config, please check log to learn more");
        }
    }

    /**
     * 获取当前配置
     * <p>数据返回格式：{"success":{@code boolean}, "message":{@code String}}
     */
    public void getEventConfig()
    {
        String config = ConfigLoader.getFullJson().toString();

        try
        {
            reply(true, config);
        }
        catch (Exception e)
        {
            logger.error("Failed to get default config", e);
            reply(false, "Failed to get default config, please check log to learn more");
        }
    }

    /**
     * 获取所有沙箱
     * <p>返回数据格式：[{"UUID":{@code String}, "content":{@code String(Json)}}, {...}, ...]
     */
    public List<Map<String, String>> getAllSandbox()
    {
        return null;//DBManager.getSandboxList();
    }

    /**
     * 保存沙箱，如果是新沙箱，只需要传入content（内容格式同resources/config.json）；如果是已存在沙箱，还需传入该沙箱的 UUID
     * <p>返回数据格式：{"success":{@code boolean}, "message":{@code String}}；
     * 其中，成功时返回的 message 内容为：{"UUID":{@code String}}
     */
    @PostMapping("saveSandbox")
    public void saveSandbox(@RequestBody(required = false) String uuid, @RequestBody String content)
    {
        boolean isNew = false;
        if (uuid == null || uuid.isEmpty())
        {
            uuid = UUID.randomUUID().toString().replace("-", "");
            isNew = true;
        }

        try
        {
            if (content == null || content.isEmpty())
            {
                logger.warn("Content of sandbox(UUID: {}) is null or empty", uuid);
            }

            // 当 Main.random_seed 为 null 时，生成随机种子
            JsonNode contentNode = objectMapper.readTree(content);
            JsonNode configsNode = contentNode.get("configs");
            if (configsNode != null)
            {
                JsonNode randomSeedNode = configsNode.get("Main.random_seed");
                if (randomSeedNode != null && randomSeedNode.isNull())
                {
                    long randomSeed = new Random().nextLong();

                    ObjectNode configsObjectNode = (ObjectNode) configsNode;
                    configsObjectNode.put("Main.random_seed", randomSeed);

                    // 将修改后的JSON转换回字符串
                    content = objectMapper.writeValueAsString(contentNode);
                }
            }

            boolean success = false;//DBManager.saveSandbox(isNew, uuid, content);

            // 为防止修改了当前预设但在使用时因 UUID 相同而跳过，需要重载当前配置
            if (!isNew && uuid.equals(ConfigLoader.getConfigUUID())&& success)
            {
                ConfigLoader.use(uuid, true);
            }

            reply(success, objectMapper.writeValueAsString(Map.of("UUID", uuid)));
        }
        catch (Exception e)
        {
            logger.error("Failed to save sandbox(UUID: {})", uuid, e);
            reply(false, "Failed to save sandbox, please check log to learn more");
        }
    }

    /**
     * 删除沙箱，需传入该沙箱的 UUID
     * <p>返回数据格式：{"success":{@code boolean}, "message":{@code String}}
     */
    @PostMapping("/remove")
    public void removeSandbox(@RequestBody String uuid)
    {
        if (uuid == null || uuid.isEmpty())
        {
            logger.error("UUID of the sandbox to be removed is null or empty");
            uuid = "Unknown";
        }

        boolean success = false;//DBManager.removeSandbox(uuid);

        // 若删除当前所应用的沙箱，则恢复为默认沙箱
        if (uuid == ConfigLoader.getConfigUUID() && success)
        {
            ConfigLoader.use("0");
        }

        reply(success, success ? "Sandbox removed successfully" : "Failed to remove sandbox");
    }
}
