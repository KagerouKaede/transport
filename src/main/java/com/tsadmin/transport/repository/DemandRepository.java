package com.tsadmin.transport.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tsadmin.transport.domain.entity.Demand;

public interface DemandRepository extends JpaRepository<Demand, UUID>
{
    
}
