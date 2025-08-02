package org.example.hotelservice.repository;

import org.example.hotelservice.entity.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.time.LocalDate;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Reservation r WHERE r.reservationId = :reservationId")
    Optional<Reservation> findByIdForUpdate(@Param("reservationId") String reservationId);

    @Query("SELECT COUNT(r) FROM Reservation r " +
            "WHERE r.hotelId = :hotelId AND r.roomType = :roomType " +
            "AND r.status IN ('PENDING', 'CONFIRMED') " +
            "AND ((r.checkIn <= :checkOut AND r.checkOut >= :checkIn))")
    long countConflictingReservations(@Param("hotelId") Long hotelId,
                                      @Param("roomType") String roomType,
                                      @Param("checkIn") LocalDate checkIn,
                                      @Param("checkOut") LocalDate checkOut);
}