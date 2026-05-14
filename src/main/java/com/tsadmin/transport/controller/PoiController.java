package com.tsadmin.transport.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tsadmin.transport.service.core.PoiService;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequestMapping("/api/poi")
public class PoiController
{
    @Autowired
    private PoiService poiServ;

    @PostMapping("/register")
    public String registerPoi(@RequestBody String entity)
    {
        try
        {
            String ret = poiServ.registerPoi(entity).toString();
            return ret;
        }
        catch (Exception e)
        {
            return e.toString();
        }
    }

    @PostMapping("/updatePosition")
    public void movePoi(@RequestBody String entity)
    {
        try
        {
            poiServ.movePoi(entity);
        }
        catch (Exception e)
        {
            // TODO: handle exception
        }
        
    }

    @PostMapping("/cancelAccount")
    public void cancelAccount(@RequestBody String entity)
    {
        try
        {
            UUID uuid = UUID.fromString(entity);
            poiServ.removePoi(uuid);
        }
        catch (Exception e)
        {
            // TODO: handle exception
        }
    }
}
