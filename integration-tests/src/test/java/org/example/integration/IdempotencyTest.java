package org.example.integration;

import org.example.common.command.CommandResult;
import org.example.common.command.ReserveRoomCommand;
import org.example.common.dto.ReservationDto;
import org.example.hotelservice.HotelServiceApplication;
import org.example.hotelservice.entity.IdempotencyRecord;
import org.example.hotelservice.repository.IdempotencyRepository;
import org.example.hotelservice.service.HotelService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = HotelServiceApplication.class)
@TestPropertySource(properties = {
        "hotel.simulation.delay=0",
        "hotel.simulation.failure-rate=0.0"
})
public class IdempotencyTest {

    @Autowired
    private HotelService hotelService;

    @Autowired
    private IdempotencyRepository idempotencyRepository;

    /**
     * Tests that executing the same ReserveRoomCommand twice with the same idempotency key
     * will return the cached result, ensuring idempotency is implemented correctly.
     *
     * The test:
     * - Issues a ReserveRoomCommand for the first time, checks for a successful reservation,
     *   and verifies the creation of an IdempotencyRecord.
     *
     * - Repeats the command execution with the same idempotency key, and asserts the
     *   reservation is successful and the reservation ID remains unchanged, confirming
     *   the result is retrieved from the cache rather than creating a duplicate reservation.
     */
    @Test
    void reserveRoomCommand_DuplicateKey_ReturnsCachedResult() {
        // Given
        ReserveRoomCommand command = createReserveRoomCommand();

        // When - Execute command first time
        CommandResult<ReservationDto> result1 = hotelService.reserveRoom(command);

        // Then
        assertTrue(result1.isSuccess());
        assertNotNull(result1.getData());
        String reservationId1 = result1.getData().getReservationId();

        // Verify idempotency record exists
        Optional<IdempotencyRecord> record = idempotencyRepository.findById(command.getIdempotencyKey());
        assertTrue(record.isPresent());

        // When - Execute same command again
        CommandResult<ReservationDto> result2 = hotelService.reserveRoom(command);

        // Then - Should return cached result
        assertTrue(result2.isSuccess());
        assertNotNull(result2.getData());
        assertEquals(reservationId1, result2.getData().getReservationId());
    }

    private ReserveRoomCommand createReserveRoomCommand() {
        ReserveRoomCommand command = new ReserveRoomCommand();
        command.setSagaId("test-saga-123");
        command.setIdempotencyKey("test-saga-123-reserve-room-001");
        command.setTimestamp(LocalDateTime.now());
        command.setHotelId(1L);
        command.setRoomType("STANDARD");
        command.setCheckIn(LocalDate.now().plusDays(1));
        command.setCheckOut(LocalDate.now().plusDays(3));
        command.setGuestName("Test Guest");
        command.setRoomPrice(new BigDecimal("199.99"));
        return command;
    }
}