package com.github.gaohongf.redis;

public final class RedisUtils {
    private RedisUtils() {
    }

    public static String getTrueName(String name) {
        if (name.contains("#")){
            return name.substring(0, name.lastIndexOf("#"));
        } else return name;
    }
}
