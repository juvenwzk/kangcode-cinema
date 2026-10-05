package com.kangcode.framework.security;

/**
 * 当前登录用户上下文（ThreadLocal，请求结束由拦截器清理）
 */
public final class UserContext {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(LoginUser user) {
        HOLDER.set(user);
    }

    public static LoginUser get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }

    public static Long userId() {
        LoginUser user = HOLDER.get();
        return user == null ? null : user.getUserId();
    }

    public static String username() {
        LoginUser user = HOLDER.get();
        return user == null ? null : user.getUsername();
    }

    public static String role() {
        LoginUser user = HOLDER.get();
        return user == null ? null : user.getRole();
    }

    public static boolean isAdmin() {
        return "ADMIN".equals(role());
    }
}
