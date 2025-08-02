package org.example.common.utils;

import java.util.UUID;

public class IdempotencyUtils {

    public static String generateKey(String sagaId, String operation) {
        return sagaId + "-" + operation + "-" + UUID.randomUUID();
    }

    public static String generateKey(String sagaId, String operation, String suffix) {
        return sagaId + "-" + operation + "-" + suffix;
    }
}