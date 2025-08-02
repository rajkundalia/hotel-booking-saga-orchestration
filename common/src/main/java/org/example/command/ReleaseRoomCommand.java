package org.example.command;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ReleaseRoomCommand extends SagaCommand {
    private String reservationId;
    private String reason;
}