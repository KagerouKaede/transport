package com.tsadmin.transport.domain.share;

/** 计时器 */
public class Timer
{
    public static final int TICK_SPEED = 20;//ConfigLoader.getInt("Timer.tick_speed", 20);
    private int time = 0;

    public void setTime(int time) { this.time = time; }
    public int getTime() { return time; }

    /** @return {@code true}如果计时器归零 */
    public boolean timeUp() { return time == 0; }

    /** 计时器滴答一次，即向前进一周期 */
    public void tick()
    {
        time = Math.max(time - Math.max(TICK_SPEED, 0), 0);
    }
}
