package com.tsadmin.transport.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tsadmin.transport.service.core.VehicleService;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequestMapping("/api/vehicle")
public class VehicleController
{
    @Autowired
    private VehicleService vehServ;

    @PostMapping("/register")
    public String registerVehicle(@RequestBody String entity)
    {
        try
        {
            String ret = vehServ.registerVehicle(entity).toString();
            return ret;
        }
        catch (Exception e)
        {
            return e.toString();
        }
    }

    @PostMapping("/updatePosition")
    public void moveVehicle(@RequestBody String entity)
    {
        try
        {
            vehServ.moveVehicle(entity);
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
            vehServ.removeVehicle(uuid);
        }
        catch (Exception e)
        {
            // TODO: handle exception
        }
    }
}
