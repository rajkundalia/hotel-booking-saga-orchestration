package org.example.command;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public abstract class SagaCommand {
    private String sagaId;
    private String idempotencyKey;
    private LocalDateTime timestamp;
    private int retryCount = 0;
}