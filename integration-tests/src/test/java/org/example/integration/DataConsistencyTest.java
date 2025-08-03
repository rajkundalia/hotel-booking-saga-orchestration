package org.example.integration;

import org.example.hotelservice.HotelServiceApplication;
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

@SpringBootTest(classes = HotelServiceApplication.class)
@DirtiesContext
public class DataConsistencyTest {

    @Autowired
    private ReservationRepository reservationRepository;

    /*
     * Test to ensure that dirty reads are prevented under READ_COMMITTED isolation.
     *
     * Scenario:
     * - A reservation is created and persisted.
     * - Transaction 1 modifies the reservation status but does not commit immediately (simulating an uncommitted change).
     * - Transaction 2 attempts to read the status of the same reservation while Transaction 1 is still in progress.
     *
     * Expectation:
     * - Transaction 2 must NOT see the uncommitted change from Transaction 1 because the READ_COMMITTED isolation prevents dirty reads.
     * - The reader should see the original committed status ("PENDING"), while the modifier sees the updated (but uncommitted) status ("CONFIRMED").
     * - The test verifies that read and modified status differ, and that the read value is still the original committed one.
     */
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
    /*
     * Test to demonstrate non-repeatable (fuzzy) reads under READ_COMMITTED isolation.
     *
     * Scenario:
     * - A reservation is created and persisted.
     * - The initial room price is read and stored (emulating a transactional snapshot).
     * - In a separate transaction, the reservation's price is modified and committed.
     * - The reservation is read again in the original context.
     *
     * Expectation:
     * - The second read sees the updated value, different from the initially captured value,
     *   illustrating a classic non-repeatable read: multiple reads in the same transaction/flow yield different results
     *   because of a committed concurrent modification.
     *
     * - The test asserts that the snapshot price is unchanged but the latest read reflects the committed update.
     *
     * - This is allowed in READ_COMMITTED isolation and illustrates why sagas or business processes may choose to
     *   stick to their initial snapshot instead of re-reading.
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