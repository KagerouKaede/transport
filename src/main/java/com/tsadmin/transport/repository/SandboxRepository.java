package com.tsadmin.transport.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tsadmin.transport.domain.entity.Sandbox;

public interface SandboxRepository extends JpaRepository<Sandbox, UUID>
{
    
}
