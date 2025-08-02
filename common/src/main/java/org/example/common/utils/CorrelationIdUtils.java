package org.example.common.utils;

import org.slf4j.MDC;

import java.util.UUID;

public class CorrelationIdUtils {

    private static final String CORRELATION_ID_KEY = "correlationId";

    public static void setCorrelationId(String correlationId) {
        MDC.put(CORRELATION_ID_KEY, correlationId);
    }

    public static String getCorrelationId() {
        return MDC.get(CORRELATION_ID_KEY);
    }

    public static void generateAndSetCorrelationId() {
        setCorrelationId(UUID.randomUUID().toString());
    }

    public static void clear() {
        MDC.remove(CORRELATION_ID_KEY);
    }
}