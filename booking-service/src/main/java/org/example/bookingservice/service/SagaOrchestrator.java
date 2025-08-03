package org.example.bookingservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.bookingservice.entity.SagaInstance;
import org.example.bookingservice.feignclient.HotelServiceClient;
import org.example.bookingservice.feignclient.PaymentServiceClient;
import org.example.bookingservice.repository.SagaInstanceRepository;
import org.example.common.command.AuthorizePaymentCommand;
import org.example.common.command.CancelPaymentCommand;
import org.example.common.command.CommandResult;
import org.example.common.command.ReleaseRoomCommand;
import org.example.common.command.ReserveRoomCommand;
import org.example.common.dto.BookingRequest;
import org.example.common.dto.PaymentAuthorizationDto;
import org.example.common.dto.ReservationDto;
import org.example.common.enumerations.SagaState;
import org.example.common.utils.IdempotencyUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SagaOrchestrator {

    private final SagaInstanceRepository sagaRepository;
    private final HotelServiceClient hotelClient;
    private final PaymentServiceClient paymentClient;
    private final ObjectMapper objectMapper;

    @Transactional
    public String startBookingSaga(BookingRequest request) {
        String sagaId = UUID.randomUUID().toString();
        log.info("Starting booking saga: {}", sagaId);

        try {
            // Create saga instance
            SagaInstance saga = new SagaInstance();
            saga.setSagaId(sagaId);
            saga.setState(SagaState.STARTED);
            saga.setSagaData(objectMapper.writeValueAsString(request));

            saga = sagaRepository.save(saga);

            // Execute first step
            executeReserveRoom(saga);

            return sagaId;

        } catch (Exception e) {
            log.error("Failed to start booking saga: " + sagaId, e);
            throw new RuntimeException("Failed to start booking saga", e);
        }
    }

    @Transactional
    public void executeReserveRoom(SagaInstance saga) {
        log.info("Executing room reservation for saga: {}", saga.getSagaId());

        try {
            BookingRequest request = objectMapper.readValue(saga.getSagaData(), BookingRequest.class);

            ReserveRoomCommand command = new ReserveRoomCommand();
            command.setSagaId(saga.getSagaId());
            command.setIdempotencyKey(IdempotencyUtils.generateKey(saga.getSagaId(), "reserve-room"));
            command.setTimestamp(LocalDateTime.now());
            command.setHotelId(request.getHotelId());
            command.setRoomType(request.getRoomType());
            command.setCheckIn(request.getCheckIn());
            command.setCheckOut(request.getCheckOut());
            command.setGuestName(request.getGuestName());
            command.setRoomPrice(request.getRoomPrice());

            CommandResult<ReservationDto> result = hotelClient.reserveRoom(command);

            if (result.isSuccess()) {
                updateSagaState(saga, SagaState.ROOM_RESERVED);
                saga.setReservationId(result.getData().getReservationId());
                sagaRepository.save(saga);

                // Continue to payment authorization
                executeAuthorizePayment(saga);
            } else {
                log.error("Room reservation failed for saga {}: {}", saga.getSagaId(), result.getErrorMessage());
                updateSagaState(saga, SagaState.ROOM_RESERVATION_FAILED);
                sagaRepository.save(saga);

                // End saga with failure
                updateSagaState(saga, SagaState.BOOKING_CANCELLED);
                sagaRepository.save(saga);
            }

        } catch (Exception e) {
            log.error("Error executing room reservation for saga: " + saga.getSagaId(), e);
            handleSagaError(saga, e);
        }
    }

    @Transactional
    public void executeAuthorizePayment(SagaInstance saga) {
        log.info("Executing payment authorization for saga: {}", saga.getSagaId());

        try {
            BookingRequest request = objectMapper.readValue(saga.getSagaData(), BookingRequest.class);

            AuthorizePaymentCommand command = new AuthorizePaymentCommand();
            command.setSagaId(saga.getSagaId());
            command.setIdempotencyKey(IdempotencyUtils.generateKey(saga.getSagaId(), "authorize-payment"));
            command.setTimestamp(LocalDateTime.now());
            command.setCardNumber(request.getCardNumber());
            command.setCardHolderName(request.getCardHolderName());
            command.setExpiryMonth(request.getExpiryMonth());
            command.setExpiryYear(request.getExpiryYear());
            command.setCvv(request.getCvv());
            command.setAmount(request.getRoomPrice());
            command.setCurrency("USD");

            CommandResult<PaymentAuthorizationDto> result = paymentClient.authorizePayment(command);

            if (result.isSuccess()) {
                updateSagaState(saga, SagaState.PAYMENT_AUTHORIZED);
                saga.setAuthorizationId(result.getData().getAuthorizationId());
                sagaRepository.save(saga);

                // Complete booking
                updateSagaState(saga, SagaState.BOOKING_COMPLETED);
                sagaRepository.save(saga);
                log.info("Booking completed successfully for saga: {}", saga.getSagaId());
            } else {
                log.error("Payment authorization failed for saga {}: {}", saga.getSagaId(), result.getErrorMessage());
                updateSagaState(saga, SagaState.PAYMENT_AUTHORIZATION_FAILED);
                sagaRepository.save(saga);

                // Start compensation
                executeCompensation(saga);
            }

        } catch (Exception e) {
            log.error("Error executing payment authorization for saga: " + saga.getSagaId(), e);
            handleSagaError(saga, e);
        }
    }

    @Transactional
    public void executeCompensation(SagaInstance saga) {
        log.info("Executing compensation for saga: {}", saga.getSagaId());

        updateSagaState(saga, SagaState.COMPENSATING);
        sagaRepository.save(saga);

        boolean compensationSuccess = true;

        // Cancel payment if it was authorized
        if (saga.getAuthorizationId() != null) {
            try {
                CancelPaymentCommand command = new CancelPaymentCommand();
                command.setSagaId(saga.getSagaId());
                command.setIdempotencyKey(IdempotencyUtils.generateKey(saga.getSagaId(), "cancel-payment"));
                command.setTimestamp(LocalDateTime.now());
                command.setAuthorizationId(saga.getAuthorizationId());
                command.setReason("Booking saga compensation");

                CommandResult<Void> result = paymentClient.cancelPayment(command);
                if (!result.isSuccess()) {
                    log.error("Payment cancellation failed for saga {}: {}", saga.getSagaId(), result.getErrorMessage());
                    compensationSuccess = false;
                }
            } catch (Exception e) {
                log.error("Error cancelling payment for saga: " + saga.getSagaId(), e);
                compensationSuccess = false;
            }
        }

        // Release room if it was reserved
        if (saga.getReservationId() != null) {
            try {
                ReleaseRoomCommand command = new ReleaseRoomCommand();
                command.setSagaId(saga.getSagaId());
                command.setIdempotencyKey(IdempotencyUtils.generateKey(saga.getSagaId(), "release-room"));
                command.setTimestamp(LocalDateTime.now());
                command.setReservationId(saga.getReservationId());
                command.setReason("Booking saga compensation");

                CommandResult<Void> result = hotelClient.releaseRoom(command);
                if (!result.isSuccess()) {
                    log.error("Room release failed for saga {}: {}", saga.getSagaId(), result.getErrorMessage());
                    compensationSuccess = false;
                }
            } catch (Exception e) {
                log.error("Error releasing room for saga: " + saga.getSagaId(), e);
                compensationSuccess = false;
            }
        }

        // Update final state
        if (compensationSuccess) {
            updateSagaState(saga, SagaState.COMPENSATION_COMPLETED);
            updateSagaState(saga, SagaState.BOOKING_CANCELLED);
            log.info("Compensation completed successfully for saga: {}", saga.getSagaId());
        } else {
            updateSagaState(saga, SagaState.COMPENSATION_FAILED);
            log.error("Compensation failed for saga: {}", saga.getSagaId());
        }

        sagaRepository.save(saga);
    }

    @Transactional
    public void retrySaga(String sagaId) {
        log.info("Retrying saga: {}", sagaId);

        Optional<SagaInstance> sagaOpt = sagaRepository.findByIdForUpdate(sagaId);
        if (sagaOpt.isEmpty()) {
            log.warn("Saga not found for retry: {}", sagaId);
            return;
        }

        SagaInstance saga = sagaOpt.get();

        if (!saga.canRetry()) {
            log.warn("Saga {} cannot be retried (retryCount: {}, maxRetries: {})",
                    sagaId, saga.getRetryCount(), saga.getMaxRetries());
            executeCompensation(saga);
            return;
        }

        saga.incrementRetry();
        sagaRepository.save(saga);

        try {
            switch (saga.getState()) {
                case STARTED, ROOM_RESERVATION_FAILED -> executeReserveRoom(saga);
                case ROOM_RESERVED, PAYMENT_AUTHORIZATION_FAILED -> executeAuthorizePayment(saga);
                case COMPENSATING, COMPENSATION_FAILED -> executeCompensation(saga);
                default -> log.warn("Cannot retry saga in state: {}", saga.getState());
            }
        } catch (Exception e) {
            log.error("Error retrying saga: " + sagaId, e);
            handleSagaError(saga, e);
        }
    }

    private void updateSagaState(SagaInstance saga, SagaState newState) {
        if (saga.canTransitionTo(newState)) {
            log.info("Saga {} transitioning from {} to {}", saga.getSagaId(), saga.getState(), newState);
            saga.setState(newState);
        } else {
            log.warn("Invalid state transition for saga {}: {} -> {}",
                    saga.getSagaId(), saga.getState(), newState);
        }
    }

    private void handleSagaError(SagaInstance saga, Exception e) {
        if (saga.canRetry()) {
            log.info("Will retry saga {} due to error", saga.getSagaId());
            saga.incrementRetry();
            sagaRepository.save(saga);
        } else {
            log.error("Saga {} exhausted retries, starting compensation", saga.getSagaId());
            executeCompensation(saga);
        }
    }
}