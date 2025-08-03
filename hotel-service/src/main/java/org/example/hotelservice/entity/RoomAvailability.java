package org.example.hotelservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDate;

@Entity
@Table(name = "room_availability",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"hotel_id", "room_type", "date"},
                name = "uk_room_date"
        )
)
@Getter
@Setter
@ToString
public class RoomAvailability {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hotel_id", nullable = false)
    private Long hotelId;

    @Column(name = "room_type", nullable = false)
    private String roomType;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "reservation_id", nullable = false)
    private String reservationId;

    @Version
    private Long version;
}