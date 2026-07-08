package com.tool.config.datasource;

public final class DataSourceContextHolder {

    private static final ThreadLocal<String> CONTEXT = new ThreadLocal<String>();

    private DataSourceContextHolder() {
    }

    public static void set(String dataSourceKey) {
        CONTEXT.set(dataSourceKey);
    }

    public static String get() {
        return CONTEXT.get();
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
