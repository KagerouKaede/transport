package com.tsadmin.transport.common.util;

import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Component;

@Component
public final class RandomUtil
{
    public static int nextInt(int bound) { return nextInt(0, bound); }
    public static int nextInt(int origin, int bound)
    {
        return ThreadLocalRandom.current().nextInt(origin, bound);
    }

    public static double nextDouble()
    {
        return ThreadLocalRandom.current().nextDouble();
    }
}