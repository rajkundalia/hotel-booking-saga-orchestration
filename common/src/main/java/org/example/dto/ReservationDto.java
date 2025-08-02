package org.example.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class ReservationDto {
    private String reservationId;
    private Long hotelId;
    private String roomType;
    private LocalDate checkIn;
    private LocalDate checkOut;
    private String guestName;
    private BigDecimal roomPrice;
    private String status;
    private LocalDateTime createdAt;
    private Long version;
}