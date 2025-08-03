package org.example.bookingservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.bookingservice.entity.SagaInstance;
import org.example.bookingservice.repository.SagaInstanceRepository;
import org.example.common.dto.BookingRequest;
import org.example.common.dto.BookingResponse;
import org.example.common.enumerations.SagaState;
import org.example.common.utils.CorrelationIdUtils;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingService {

    private final SagaOrchestrator sagaOrchestrator;
    private final SagaInstanceRepository sagaRepository;

    public BookingResponse createBooking(BookingRequest request) {
        // Set correlation ID for tracing
        CorrelationIdUtils.generateAndSetCorrelationId();
        String correlationId = CorrelationIdUtils.getCorrelationId();

        log.info("Creating booking request for hotel: {}, guest: {}",
                request.getHotelId(), request.getGuestName());

        try {
            String sagaId = sagaOrchestrator.startBookingSaga(request);
            return BookingResponse.success(correlationId, sagaId);

        } catch (Exception e) {
            log.error("Failed to create booking", e);
            return BookingResponse.failure(null, "Failed to process booking request");
        } finally {
            CorrelationIdUtils.clear();
        }
    }

    public BookingResponse getBookingStatus(String sagaId) {
        Optional<SagaInstance> sagaOpt = sagaRepository.findById(sagaId);

        if (sagaOpt.isEmpty()) {
            return BookingResponse.failure(sagaId, "Booking not found");
        }

        SagaInstance saga = sagaOpt.get();

        BookingResponse response = new BookingResponse();
        response.setBookingId(saga.getReservationId());
        response.setSagaId(sagaId);
        response.setStatus(saga.getState().name());
        response.setMessage(getStatusMessage(saga.getState()));
        response.setTimestamp(saga.getUpdatedAt());

        return response;
    }

    private String getStatusMessage(SagaState state) {
        return switch (state) {
            case STARTED -> "Booking request received";
            case ROOM_RESERVED -> "Room reserved, processing payment";
            case PAYMENT_AUTHORIZED -> "Payment authorized, completing booking";
            case BOOKING_COMPLETED -> "Booking completed successfully";
            case ROOM_RESERVATION_FAILED -> "Room reservation failed";
            case PAYMENT_AUTHORIZATION_FAILED -> "Payment authorization failed";
            case COMPENSATING -> "Processing cancellation";
            case BOOKING_CANCELLED -> "Booking cancelled";
            case COMPENSATION_COMPLETED -> "Cancellation completed";
            case COMPENSATION_FAILED -> "Cancellation failed - manual intervention required";
        };
    }
}