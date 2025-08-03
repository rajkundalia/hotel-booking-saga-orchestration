package org.example.integration;

import org.example.common.command.CommandResult;
import org.example.common.command.ReserveRoomCommand;
import org.example.common.dto.ReservationDto;
import org.example.hotelservice.entity.Reservation;
import org.example.hotelservice.enumeration.ReservationStatus;
import org.example.hotelservice.repository.ReservationRepository;
import org.example.hotelservice.service.HotelService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@DirtiesContext
public class ConcurrencyTest {

    @Autowired
    private HotelService hotelService;

    @Autowired
    private ReservationRepository reservationRepository;

    @Test
    public void reserveRoomCommand_TwoConcurrentRequestsForSameRoom_OnlyOneSucceeds() throws ExecutionException, InterruptedException {
        // Given - Two concurrent reservation requests for same room
        ReserveRoomCommand command1 = createReserveRoomCommand("saga-1", "key-1");
        ReserveRoomCommand command2 = createReserveRoomCommand("saga-2", "key-2");

        // When - Execute concurrently
        CompletableFuture<CommandResult<ReservationDto>> future1 =
                CompletableFuture.supplyAsync(() -> hotelService.reserveRoom(command1));
        CompletableFuture<CommandResult<ReservationDto>> future2 =
                CompletableFuture.supplyAsync(() -> hotelService.reserveRoom(command2));

        CommandResult<ReservationDto> result1 = future1.get();
        CommandResult<ReservationDto> result2 = future2.get();

        // Then - Only one should succeed
        assertTrue((result1.isSuccess() && !result2.isSuccess()) ||
                (!result1.isSuccess() && result2.isSuccess()));

        if (!result1.isSuccess()) {
            assertEquals("ROOM_NOT_AVAILABLE", result1.getErrorCode());
        }
        if (!result2.isSuccess()) {
            assertEquals("ROOM_NOT_AVAILABLE", result2.getErrorCode());
        }
    }

    @Test
    public void reservationUpdate_ConflictingSaves_ThrowsOptimisticLockingFailureException() {
        // Given - Create a reservation
        Reservation reservation = new Reservation();
        reservation.setReservationId("test-reservation-123");
        reservation.setHotelId(1L);
        reservation.setRoomType("STANDARD");
        reservation.setCheckIn(LocalDate.now().plusDays(1));
        reservation.setCheckOut(LocalDate.now().plusDays(3));
        reservation.setGuestName("Test Guest");
        reservation.setRoomPrice(new BigDecimal("199.99"));
        reservation.setStatus(ReservationStatus.PENDING);
        reservation = reservationRepository.save(reservation);

        // When - Load same entity in two transactions and modify both
        Reservation reservation1 = reservationRepository.findById(reservation.getReservationId()).orElseThrow();
        Reservation reservation2 = reservationRepository.findById(reservation.getReservationId()).orElseThrow();

        reservation1.setStatus(ReservationStatus.CONFIRMED);
        reservationRepository.save(reservation1);

        reservation2.setStatus(ReservationStatus.CANCELLED);

        // Then - Second update should fail with OptimisticLockingFailureException
        assertThrows(OptimisticLockingFailureException.class, () -> reservationRepository.save(reservation2));
    }

    private ReserveRoomCommand createReserveRoomCommand(String sagaId, String idempotencyKey) {
        ReserveRoomCommand command = new ReserveRoomCommand();
        command.setSagaId(sagaId);
        command.setIdempotencyKey(idempotencyKey);
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