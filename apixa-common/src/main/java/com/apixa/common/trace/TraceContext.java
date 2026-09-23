package com.apixa.common.trace;

import java.util.UUID;

public final class TraceContext {
    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();
    private TraceContext() {}
    public static String currentOrNew() {
        String traceId = CURRENT.get();
        if (traceId == null) { traceId = UUID.randomUUID().toString(); CURRENT.set(traceId); }
        return traceId;
    }
    public static String get() { return CURRENT.get(); }
    public static void set(String traceId) { CURRENT.set(traceId); }
    public static void clear() { CURRENT.remove(); }
}
