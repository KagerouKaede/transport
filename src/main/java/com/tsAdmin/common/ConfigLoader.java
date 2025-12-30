package com.tsAdmin.common;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.tsAdmin.control.DBManager;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 读取通用 JSON 配置，从 resources/config.json 加载对应配置
 */
public final class ConfigLoader
{
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Logger logger = LogManager.getLogger(ConfigLoader.class);
    private static String configUUID;
    private static JsonNode configData;

    static
    {
        // 从 resources/config.json 加载配置数据
        use("0");
    }

    public static String getConfigUUID() { return configUUID; }

    public static boolean use(String uuid) { return use(uuid, false); }
    public static boolean use(String uuid, boolean reload)
    {
        try
        {
            // 当前配置已是要使用的配置且并不强制重加载，跳过加载过程
            if (!reload && configUUID == uuid)
            {
                logger.trace("Configuration(UUID: {}) applied with no change", uuid);
                return true;
            }

            configUUID = uuid;
            JsonNode fullJson = getFullJson();
            configData = fullJson.get("configs");

            if (configData == null || configData.isNull())
            {
                throw new NoSuchFieldException("No \"configs\" node found in config(UUID:" + configUUID + ")!");
            }

            logger.trace("Configuration(UUID: {}) applied successfully", uuid);
            return true;
        }
        catch (NoSuchFieldException e)
        {
            logger.error("Failed to load configuration(UUID: {})", uuid, e);
            return false;
        }
    }

    public static JsonNode getFullJson()
    {
        try
        {
            String jsonString = "";
            if (configUUID.equals("0"))
            {
                InputStream inputStream = ConfigLoader.class.getClassLoader().getResourceAsStream("config.json");
                if (inputStream == null) throw new FileNotFoundException("Default config file not found!");

                jsonString = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                inputStream.close();
            }
            else
            {
                jsonString = DBManager.getPreset(configUUID);
            }

            return objectMapper.readTree(jsonString);
        }
        catch (Exception e)
        {
            logger.error("Failed to load full json of configuration(UUID: {})", configUUID, e);
            return null;
        }
    }

    public static String getString(String key) { return getString(key, "Lost String"); }
    public static int getInt(String key) { return getInt(key, -1); }
    public static long getLong(String key) { return getLong(key, -1L); }
    public static double getDouble(String key) { return getDouble(key, -1.0); }
    public static boolean getBoolean(String key) { return getBoolean(key, false); }

    private static JsonNode getNode(String key)
    {
        JsonNode configItem = configData.get(key);
        if (configItem == null || configItem.isNull())
        {
            logger.warn("Key [{}] not found in configuration(UUID: {}), default value returned", key, configUUID);
            return null;
        }

        JsonNode valueNode = configItem.get("value");
        if (valueNode == null || valueNode.isNull())
        {
            logger.warn("Value not found for key [{}] in configuration(UUID: {}), default value returned", key, configUUID);
            return null;
        }

        return valueNode;
    }

    public static String getString(String key, String defaultValue)
    {
        try
        {
            JsonNode valueNode = getNode(key);
            if (valueNode == null) return defaultValue;

            String value = valueNode.asText();
            return (value != null && !value.isEmpty()) ? value : defaultValue;
        }
        catch (Exception e)
        {
            logger.error("Failed to get value for key [{}] in configuration(UUID: {}), default value [{}] returned", key, configUUID, defaultValue);
            return defaultValue;
        }
    }
    public static Integer getInt(String key, Integer defaultValue)
    {
        try
        {
            JsonNode valueNode = getNode(key);
            if (valueNode == null) return defaultValue;

            return valueNode.asInt(defaultValue);
        }
        catch (Exception e)
        {
            logger.error("Failed to get value for key [{}] in configuration(UUID: {}), default value [{}] returned", key, configUUID, defaultValue);
            return defaultValue;
        }
    }
    public static Long getLong(String key, Long defaultValue)
    {
        try
        {
            JsonNode valueNode = getNode(key);
            if (valueNode == null) return defaultValue;

            return valueNode.asLong(defaultValue);
        }
        catch (Exception e)
        {
            logger.error("Failed to get value for key [{}] in configuration(UUID: {}), default value [{}] returned", key, configUUID, defaultValue);
            return defaultValue;
        }
    }
    public static Double getDouble(String key, Double defaultValue)
    {
        try
        {
            JsonNode valueNode = getNode(key);
            if (valueNode == null) return defaultValue;

            return valueNode.asDouble(defaultValue);
        }
        catch (Exception e)
        {
            logger.error("Failed to get value for key [{}] in configuration(UUID: {}), default value [{}] returned", key, configUUID, defaultValue);
            return defaultValue;
        }
    }
    public static Boolean getBoolean(String key, Boolean defaultValue)
    {
        try
        {
            JsonNode valueNode = getNode(key);
            if (valueNode == null) return defaultValue;

            return valueNode.asBoolean(defaultValue);
        }
        catch (Exception e)
        {
            logger.error("Failed to get value for key [{}] in configuration(UUID: {}), default value [{}] returned", key, configUUID, defaultValue);
            return defaultValue;
        }
    }
}


