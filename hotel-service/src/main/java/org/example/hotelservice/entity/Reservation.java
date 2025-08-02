package org.example.hotelservice.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.example.hotelservice.enumeration.ReservationStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "reservations")
@Data
public class Reservation {
    @Id
    private String reservationId;

    private Long hotelId;
    private String roomType;
    private LocalDate checkIn;
    private LocalDate checkOut;
    private String guestName;
    private BigDecimal roomPrice;

    @Enumerated(EnumType.STRING)
    private ReservationStatus status;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Version
    private Long version;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}