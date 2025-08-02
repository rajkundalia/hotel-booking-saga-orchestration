package org.example.common.command;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
public class ReserveRoomCommand extends SagaCommand {
    private Long hotelId;
    private String roomType;
    private LocalDate checkIn;
    private LocalDate checkOut;
    private String guestName;
    private BigDecimal roomPrice;
}