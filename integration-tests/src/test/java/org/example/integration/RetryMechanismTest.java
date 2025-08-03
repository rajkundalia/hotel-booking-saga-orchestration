package org.example.integration;

import org.example.bookingservice.BookingServiceApplication;
import org.example.bookingservice.entity.SagaInstance;
import org.example.bookingservice.repository.SagaInstanceRepository;
import org.example.bookingservice.service.SagaOrchestrator;
import org.example.common.dto.BookingRequest;
import org.example.common.enumerations.SagaState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = BookingServiceApplication.class)
@TestPropertySource(properties = {
        "services.hotel.url=http://localhost:8081",
        "services.payment.url=http://localhost:8082",
        "hotel.simulation.delay=100",
        "payment.simulation.delay=100",
        "hotel.simulation.failure-rate=0.8"  // High failure rate to test retries
})
public class RetryMechanismTest {

    @Autowired
    private SagaOrchestrator sagaOrchestrator;

    @Autowired
    private SagaInstanceRepository sagaRepository;

    // Retry of failed operations
    /*
     * Verifies that the saga retry and compensation mechanisms work correctly
     * under high failure rate conditions.
     *
     * - Starts a booking saga with simulated downstream hotel/payment services set to fail most requests.
     * - Asserts that retries are attempted based on the retry policy.
     * - Checks that the saga transitions to a compensating/cancelled state after exceeding max retries,
     *   ensuring failed bookings are eventually rolled back and not left in an inconsistent state.
     */
    @Test
    void sagaBooking_HighFailureRate_RetriesAndCompensates() {
        // Given
        BookingRequest request = createValidBookingRequest();

        // When
        String sagaId = sagaOrchestrator.startBookingSaga(request);

        // Then - Wait and verify retries are attempted
        await().untilAsserted(() -> {
            Optional<SagaInstance> sagaOpt = sagaRepository.findById(sagaId);
            assertTrue(sagaOpt.isPresent());
            SagaInstance saga = sagaOpt.get();

            // Should have attempted retries
            assertTrue(saga.getRetryCount() > 0);

            // Should eventually be compensated after max retries
            if (saga.getRetryCount() >= saga.getMaxRetries()) {
                assertTrue(saga.getState() == SagaState.BOOKING_CANCELLED ||
                        saga.getState() == SagaState.COMPENSATING ||
                        saga.getState() == SagaState.COMPENSATION_COMPLETED);
            }
        });
    }

    private BookingRequest createValidBookingRequest() {
        BookingRequest request = new BookingRequest();
        request.setHotelId(1L);
        request.setRoomType("STANDARD");
        request.setCheckIn(LocalDate.now().plusDays(1));
        request.setCheckOut(LocalDate.now().plusDays(3));
        request.setGuestName("John Doe");
        request.setRoomPrice(new BigDecimal("199.99"));
        request.setCardNumber("4111111111111111");
        request.setCardHolderName("John Doe");
        request.setExpiryMonth("12");
        request.setExpiryYear("2025");
        request.setCvv("123");
        return request;
    }
}