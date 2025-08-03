package org.example.hotelservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.common.command.CommandResult;
import org.example.common.command.ReleaseRoomCommand;
import org.example.common.command.ReserveRoomCommand;
import org.example.common.dto.ReservationDto;
import org.example.hotelservice.entity.IdempotencyRecord;
import org.example.hotelservice.entity.Reservation;
import org.example.hotelservice.entity.RoomAvailability;
import org.example.hotelservice.enumeration.ReservationStatus;
import org.example.hotelservice.repository.IdempotencyRepository;
import org.example.hotelservice.repository.ReservationRepository;
import org.example.hotelservice.repository.RoomAvailabilityRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class HotelService {

    private final ReservationRepository reservationRepository;
    private final RoomAvailabilityRepository roomAvailabilityRepository;
    private final IdempotencyRepository idempotencyRepository;
    private final ObjectMapper objectMapper;
    private final Random random = new Random();

    @Value("${hotel.simulation.delay:0}")
    private int simulationDelay;

    @Value("${hotel.simulation.failure-rate:0.0}")
    private double failureRate;

    public CommandResult<ReservationDto> reserveRoom(ReserveRoomCommand command) {
        log.info("Processing room reservation for saga: {}", command.getSagaId());

        // Check idempotency first (outside transaction)
        Optional<IdempotencyRecord> existingRecord =
                idempotencyRepository.findById(command.getIdempotencyKey());

        if (existingRecord.isPresent()) {
            log.info("Idempotent request detected for key: {}", command.getIdempotencyKey());
            try {
                ReservationDto cachedResult = objectMapper.readValue(
                        existingRecord.get().getResultData(), ReservationDto.class);
                return CommandResult.success(cachedResult);
            } catch (Exception e) {
                log.error("Failed to deserialize cached result", e);
            }
        }
        
        // Simulate delay and failures
        simulateDelay();
        if (shouldSimulateFailure()) {
            return CommandResult.failure("Simulated hotel service failure", "HOTEL_SERVICE_ERROR");
        }

        try {
            return attemptReservation(command);
        } catch (DataIntegrityViolationException e) {
            log.warn("Room not available for saga: {} - constraint violation", command.getSagaId());
            return CommandResult.failure("Room not available for the requested dates", "ROOM_NOT_AVAILABLE");
        } catch (Exception e) {
            log.error("Failed to reserve room for saga: " + command.getSagaId(), e);
            return CommandResult.failure("Internal server error", "INTERNAL_ERROR");
        }
    }

    @Transactional
    private CommandResult<ReservationDto> attemptReservation(ReserveRoomCommand command) {
        // Create reservation first
        Reservation reservation = new Reservation();
        reservation.setReservationId(UUID.randomUUID().toString());
        reservation.setHotelId(command.getHotelId());
        reservation.setRoomType(command.getRoomType());
        reservation.setCheckIn(command.getCheckIn());
        reservation.setCheckOut(command.getCheckOut());
        reservation.setGuestName(command.getGuestName());
        reservation.setRoomPrice(command.getRoomPrice());
        reservation.setStatus(ReservationStatus.PENDING);

        reservation = reservationRepository.save(reservation);

        // Create availability records for each date
        List<RoomAvailability> availabilityRecords = new ArrayList<>();
        LocalDate currentDate = command.getCheckIn();

        while (currentDate.isBefore(command.getCheckOut())) {
            RoomAvailability availability = new RoomAvailability();
            availability.setHotelId(command.getHotelId());
            availability.setRoomType(command.getRoomType());
            availability.setDate(currentDate);
            availability.setReservationId(reservation.getReservationId());
            availabilityRecords.add(availability);
            currentDate = currentDate.plusDays(1);
        }

        // This will fail with DataIntegrityViolationException if room is already booked
        roomAvailabilityRepository.saveAll(availabilityRecords);

        ReservationDto result = mapToDto(reservation);

        // Store idempotency record
        storeIdempotencyRecord(command.getIdempotencyKey(), result);

        log.info("Room reserved successfully: {}", reservation.getReservationId());
        return CommandResult.success(result);
    }

    @Transactional
    public CommandResult<Void> releaseRoom(ReleaseRoomCommand command) {
        log.info("Processing room release for reservation: {}", command.getReservationId());

        // Check idempotency
        Optional<IdempotencyRecord> existingRecord =
                idempotencyRepository.findById(command.getIdempotencyKey());

        if (existingRecord.isPresent()) {
            log.info("Idempotent request detected for key: {}", command.getIdempotencyKey());
            return CommandResult.success(null);
        }

        try {
            Optional<Reservation> reservationOpt =
                    reservationRepository.findById(command.getReservationId());

            if (reservationOpt.isEmpty()) {
                return CommandResult.failure("Reservation not found", "RESERVATION_NOT_FOUND");
            }

            Reservation reservation = reservationOpt.get();

            if (reservation.getStatus() == ReservationStatus.RELEASED) {
                log.info("Reservation already released: {}", command.getReservationId());
                storeIdempotencyRecord(command.getIdempotencyKey(), null);
                return CommandResult.success(null);
            }

            // Update reservation status
            reservation.setStatus(ReservationStatus.RELEASED);
            reservationRepository.save(reservation);

            // Remove availability records
            roomAvailabilityRepository.deleteByReservationId(command.getReservationId());

            // Store idempotency record
            storeIdempotencyRecord(command.getIdempotencyKey(), null);

            log.info("Room released successfully: {}", command.getReservationId());
            return CommandResult.success(null);

        } catch (Exception e) {
            log.error("Failed to release room: " + command.getReservationId(), e);
            return CommandResult.failure("Internal server error", "INTERNAL_ERROR");
        }
    }

    private void simulateDelay() {
        if (simulationDelay > 0) {
            try {
                Thread.sleep(simulationDelay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private boolean shouldSimulateFailure() {
        return random.nextDouble() < failureRate;
    }

    private void storeIdempotencyRecord(String key, Object result) {
        try {
            IdempotencyRecord record = new IdempotencyRecord();
            record.setIdempotencyKey(key);
            record.setResultData(result != null ? objectMapper.writeValueAsString(result) : "null");
            idempotencyRepository.save(record);
        } catch (Exception e) {
            log.error("Failed to store idempotency record", e);
        }
    }

    private ReservationDto mapToDto(Reservation reservation) {
        ReservationDto dto = new ReservationDto();
        dto.setReservationId(reservation.getReservationId());
        dto.setHotelId(reservation.getHotelId());
        dto.setRoomType(reservation.getRoomType());
        dto.setCheckIn(reservation.getCheckIn());
        dto.setCheckOut(reservation.getCheckOut());
        dto.setGuestName(reservation.getGuestName());
        dto.setRoomPrice(reservation.getRoomPrice());
        dto.setStatus(reservation.getStatus().name());
        dto.setCreatedAt(reservation.getCreatedAt());
        dto.setVersion(reservation.getVersion());
        return dto;
    }
}