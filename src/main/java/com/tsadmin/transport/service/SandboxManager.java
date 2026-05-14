package com.tsadmin.transport.service;

import jakarta.persistence.EntityNotFoundException;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.tsadmin.transport.common.util.JsonUtil;
import com.tsadmin.transport.common.util.JsonUtil.JsonMap;
import com.tsadmin.transport.domain.entity.Sandbox;
import com.tsadmin.transport.repository.SandboxRepository;

@Service
public class SandboxManager
{
    @Autowired
    private SandboxRepository sbRepo;

    public JsonMap getConfigMap(UUID uuid)
    {
        Sandbox sandbox = sbRepo.findById(uuid).orElseThrow(() -> new EntityNotFoundException());
        return JsonUtil.toMap(sandbox.getFullText());
    }

    /**
     * 创建新沙箱并保存到数据库
     * @param fullJson 沙箱配置 Json 对象
     * @return 新沙箱的{@code UUID}
     */
    public UUID createSandbox(String fullJson)
    {
        Sandbox sb = new Sandbox();
        modifySandbox(sb, fullJson);
        Sandbox saved = sbRepo.save(sb);
        return saved.getUUID();
    }

    /**
     * 保存对已存在沙箱的修改
     * @param uuid 沙箱的{@code UUID}
     * @param fullJson 沙箱新配置 Json 对象
     * @return 修改成功与否
     */
    public void saveSandbox(UUID uuid, String fullJson)
    {
        Sandbox sandbox = sbRepo.findById(uuid).orElseThrow(() -> new EntityNotFoundException());
        modifySandbox(sandbox, fullJson);
    }

    public void removeSandbox(UUID uuid)
    {
        sbRepo.deleteById(uuid);
    }

    /**
     * 根据传入的 Json 对象修改沙箱
     * @param sb 要修改的沙箱
     * @param fullJson 新的沙箱配置 Json 对象
     */
    private void modifySandbox(Sandbox sb, String fullJson)
    {
        JsonMap jsonMap = JsonUtil.toMap(fullJson);
        String name = JsonUtil.getObject(jsonMap, "sandbox_name", String.class);
        sb.setName(name);
        sb.setConf(fullJson);
    }

    // @Transactional
    // public boolean runSandbox(UUID uuid)
    // {
    //     if (runningTasks.containsKey(uuid)) return false;

    //     // 从沙箱配置中读取更新间隔
    //     ConfigMap conf = getConfigMap(uuid);
    //     int intervalSec = ConfigUtil.getConfig(conf, "configs/Main.update_interval", Integer.class);
    //     Duration interval = Duration.ofSeconds(intervalSec);

    //     // 创建任务
    //     Runnable task = () -> {
    //         try
    //         {
    //             poiManager.updatePoisForSandbox(uuid);
    //         }
    //         catch (Exception e)
    //         {
    //             throw e;
    //             // TODO: log.error("Sandbox {} update error", uuid, e);
    //         }
    //     };
    //     ScheduledFuture<?> future = taskScheduler.scheduleWithFixedDelay(task, interval);
    //     runningTasks.put(uuid, future);
    //     return true;

    //         SandboxThread runnable = new SandboxThread(this, uuid);
    //         SandboxObject object = new SandboxObject(runnable);
    //         object.start();
    //         activeSandbox.put(uuid, object);
    //         return true;
    // }

    // public boolean stopSandbox(UUID uuid)
    // {
    //     try
    //     {
    //         SandboxObject object = activeSandbox.get(uuid);
    //         if (object == null)
    //         {
    //             // TODO: warn object not found
    //             return false;
    //         }

    //         object.stop();
    //         activeSandbox.remove(uuid);
    //         return true;
    //     }
    //     catch (Exception e)
    //     {
    //         // TODO: log error
    //         return false;
    //     }
    // }
}
