package org.example.hotelservice.repository;

import org.example.hotelservice.entity.RoomAvailability;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

public interface RoomAvailabilityRepository extends JpaRepository<RoomAvailability, Long> {

    @Modifying
    @Query("DELETE FROM RoomAvailability ra WHERE ra.reservationId = :reservationId")
    void deleteByReservationId(@Param("reservationId") String reservationId);

    @Query("SELECT COUNT(ra) FROM RoomAvailability ra " +
            "WHERE ra.hotelId = :hotelId AND ra.roomType = :roomType " +
            "AND ra.date >= :checkIn AND ra.date < :checkOut")
    long countConflictingDates(@Param("hotelId") Long hotelId,
                               @Param("roomType") String roomType,
                               @Param("checkIn") LocalDate checkIn,
                               @Param("checkOut") LocalDate checkOut);
}