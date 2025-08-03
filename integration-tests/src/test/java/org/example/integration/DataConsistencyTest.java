package org.example.integration;

import org.example.hotelservice.entity.Reservation;
import org.example.hotelservice.enumeration.ReservationStatus;
import org.example.hotelservice.repository.ReservationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@SpringBootTest
@DirtiesContext
public class DataConsistencyTest {

    @Autowired
    private ReservationRepository reservationRepository;

    // Prevent Dirty Reads
    @Test
    public void reservation_UncommittedUpdate_IsNotVisibleToConcurrentReader() throws ExecutionException, InterruptedException {
        // Given - Create a reservation
        Reservation reservation = createTestReservation("dirty-read-test");
        reservation = reservationRepository.save(reservation);

        final String reservationId = reservation.getReservationId();

        // When - Simulate dirty read scenario
        CompletableFuture<String> transaction1 = CompletableFuture.supplyAsync(() -> modifyReservationWithoutCommit(reservationId));

        CompletableFuture<String> transaction2 = CompletableFuture.supplyAsync(() -> readReservationStatus(reservationId));

        String modifiedStatus = transaction1.get();
        String readStatus = transaction2.get();

        // Then - Reader should not see uncommitted changes
        assertNotEquals(modifiedStatus, readStatus);
        assertEquals("PENDING", readStatus); // Should see original committed value
    }

    /*
        A fuzzy read, also known as a non-repeatable read, occurs when a transaction reads the same row or data
        item more than once and receives different values each time because another transaction has modified
        and committed changes to that data in the meantime
     */
    @Test
    void reservationPrice_ConcurrentModification_LeadsToNonRepeatableRead() {
        // Given - Create a reservation
        Reservation reservation = createTestReservation("fuzzy-read-test");
        reservation = reservationRepository.save(reservation);

        // Simulate snapshot isolation by capturing initial state
        BigDecimal initialPrice = reservation.getRoomPrice();
        String reservationId = reservation.getReservationId();

        // When - Another transaction modifies the price
        Reservation anotherRef = reservationRepository.findById(reservationId).orElseThrow();
        anotherRef.setRoomPrice(new BigDecimal("299.99"));
        reservationRepository.save(anotherRef);

        // Then - Our saga should use the snapshot price, not the updated one
        Reservation ourRef = reservationRepository.findById(reservationId).orElseThrow();
        assertNotEquals(initialPrice, ourRef.getRoomPrice());

        // In a real saga, we would use the captured snapshot price
        // throughout the saga execution, not re-read from database
        assertEquals(new BigDecimal("199.99"), initialPrice);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    private String modifyReservationWithoutCommit(String reservationId) {
        try {
            Reservation reservation = reservationRepository.findById(reservationId).orElseThrow();
            reservation.setStatus(ReservationStatus.CONFIRMED);
            // Simulate some processing time
            Thread.sleep(100);
            reservationRepository.save(reservation);
            return "CONFIRMED";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "ERROR";
        }
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    private String readReservationStatus(String reservationId) {
        try {
            // Small delay to ensure we read during the other transaction
            Thread.sleep(50);
            Reservation reservation = reservationRepository.findById(reservationId).orElseThrow();
            return reservation.getStatus().name();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "ERROR";
        }
    }

    private Reservation createTestReservation(String prefix) {
        Reservation reservation = new Reservation();
        reservation.setReservationId(prefix + "-" + System.currentTimeMillis());
        reservation.setHotelId(1L);
        reservation.setRoomType("STANDARD");
        reservation.setCheckIn(LocalDate.now().plusDays(1));
        reservation.setCheckOut(LocalDate.now().plusDays(3));
        reservation.setGuestName("Test Guest");
        reservation.setRoomPrice(new BigDecimal("199.99"));
        reservation.setStatus(ReservationStatus.PENDING);
        return reservation;
    }
}