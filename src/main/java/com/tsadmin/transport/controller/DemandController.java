package com.tsadmin.transport.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tsadmin.transport.service.core.DemandService;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequestMapping("/api/demand")
public class DemandController
{
    @Autowired
    private DemandService demServ;

    @PostMapping("/create")
    public String createDemand(@RequestBody String entity)
    {
        try
        {
            String ret = demServ.createDemand(entity).toString();
            return ret;
        }
        catch (Exception e)
        {
            return e.toString();
        }
    }

    @PostMapping("/complete")
    public void complete(@RequestBody String entity)
    {
        try
        {
            UUID uuid = UUID.fromString(entity);
            demServ.completeDemand(uuid);
        }
        catch (Exception e)
        {
            // TODO: handle exception
        }
    }

    @PostMapping("/cancel")
    public void cancelDemand(@RequestBody String entity)
    {
        try
        {
            UUID uuid = UUID.fromString(entity);
            demServ.cancelDemand(uuid);
        }
        catch (Exception e)
        {
            // TODO: handle exception
        }
    }
}
