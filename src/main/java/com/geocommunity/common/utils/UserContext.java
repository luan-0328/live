package com.geocommunity.common.utils;

public class UserContext {
    private static final ThreadLocal<Long> currentUserId = new ThreadLocal<>();

    public static void set(Long userId) {
        currentUserId.set(userId);
    }

    public static Long get() {
        return currentUserId.get();
    }

    public static void clear() {
        currentUserId.remove();
    }
}
