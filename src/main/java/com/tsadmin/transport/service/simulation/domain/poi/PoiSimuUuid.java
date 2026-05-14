package com.tsadmin.transport.service.simulation.domain.poi;

import java.io.Serializable;
import java.util.UUID;

public class PoiSimuUuid implements Serializable
{
    private UUID sandboxUuid;
    private UUID poiUuid;

    protected PoiSimuUuid() {}
    public PoiSimuUuid(UUID sandboxId, UUID poiId)
    {
        this.sandboxUuid = sandboxId;
        this.poiUuid = poiId;
    }

    // @Override
    // public boolean equals(Object obj) { return super.equals(obj); }

    // @Override
    // public int hashCode() { return super.hashCode(); }

    public UUID getSandboxUuid() { return sandboxUuid; }
    public UUID getPoiUuid() { return poiUuid; }
}
