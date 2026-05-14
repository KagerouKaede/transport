package com.tsadmin.transport.service.simulation.domain;

import com.tsadmin.transport.common.share.Timer;

import jakarta.persistence.Transient;

public class VehSimuAttr
{
    @Transient
    private Timer stateTimer;                           // 状态计时器

    public Timer getStateTimer() { return stateTimer; }

    /** 计时器滴答一次，即向前进一周期 并记录时间 */
    public void tick()
    { 
        stateTimer.tick();
    }
}
