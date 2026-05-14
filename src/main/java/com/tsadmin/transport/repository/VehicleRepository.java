package com.tsadmin.transport.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tsadmin.transport.domain.entity.Vehicle;

public interface VehicleRepository extends JpaRepository<Vehicle, UUID>
{
    
}
