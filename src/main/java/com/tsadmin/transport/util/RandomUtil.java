package com.tsadmin.transport.util;

import java.util.concurrent.ThreadLocalRandom;

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