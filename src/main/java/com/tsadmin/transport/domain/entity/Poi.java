package com.tsadmin.transport.domain.entity;

import java.util.UUID;

import com.tsadmin.transport.common.share.Coordinate;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table
public class Poi
{
    @Id
    @Column(columnDefinition = "UUID")
    protected UUID uuid;
    @Column(nullable = false)
    protected String name;
    @Embedded
    protected Coordinate position;

    protected Poi() {}
    public Poi(UUID uuid, String name, Coordinate position)
    {
        this.uuid = uuid;
        this.name = name;
        this.position = position;
    }

    // Getter
    public UUID getUUID() { return uuid; }
    public String getName() { return name; }
    public Coordinate getPosition() { return position; }

    // Setter
    public void setName(String name) { this.name = name; }
}
