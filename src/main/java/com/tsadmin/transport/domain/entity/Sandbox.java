package com.tsadmin.transport.domain.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table
public class Sandbox
{
    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(columnDefinition = "UUID")
    private UUID uuid;
    @Column(nullable = false)
    private String name;
    @Column(columnDefinition = "TEXT")
    private String content;
    private LocalDateTime updateTime;

    public Sandbox() {}

    // Getter
    public UUID getUUID() { return uuid; }
    public String getName() { return name; }
    public String getFullText() { return content; }
    public String getUpdateTime() { return updateTime.format(null); }

    // Setter
    public void setName(String name)
    {
        this.name = name;
        updateTime = LocalDateTime.now();
    }
    public void setConf(String fullJson)
    {
        this.content = fullJson;
        updateTime = LocalDateTime.now();
    }
}