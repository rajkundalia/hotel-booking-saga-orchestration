package org.example.command;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class CancelPaymentCommand extends SagaCommand {
    private String authorizationId;
    private String reason;
}