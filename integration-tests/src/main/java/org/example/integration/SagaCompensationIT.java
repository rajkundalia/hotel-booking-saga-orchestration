package org.example.integration;

import org.example.bookingservice.BookingServiceApplication;
import org.example.bookingservice.entity.SagaInstance;
import org.example.bookingservice.repository.SagaInstanceRepository;
import org.example.bookingservice.service.BookingService;
import org.example.common.dto.BookingRequest;
import org.example.common.dto.BookingResponse;
import org.example.common.enumerations.SagaState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = BookingServiceApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "services.hotel.url=http://localhost:8081",
        "services.payment.url=http://localhost:8082",
        "hotel.simulation.delay=100",
        "payment.simulation.delay=100",
        "hotel.simulation.failure-rate=0.0",
        "payment.simulation.failure-rate=1.0"  // Force payment failure
})
class SagaCompensationIT {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private SagaInstanceRepository sagaRepository;

    @Test
    void createBookingSaga_PaymentFails_SagaIsCompensated() {
        // Given
        BookingRequest request = createValidBookingRequest();

        // When
        BookingResponse response = bookingService.createBooking(request);

        // Then
        assertNotNull(response);
        assertNotNull(response.getSagaId());

        // Wait for saga compensation
        await().untilAsserted(() -> {
            Optional<SagaInstance> sagaOpt = sagaRepository.findById(response.getSagaId());
            assertTrue(sagaOpt.isPresent());
            assertEquals(SagaState.BOOKING_CANCELLED, sagaOpt.get().getState());
            assertNotNull(sagaOpt.get().getReservationId()); // Room was reserved
            assertNull(sagaOpt.get().getAuthorizationId()); // Payment failed
        });

        // Verify final booking status
        BookingResponse finalStatus = bookingService.getBookingStatus(response.getSagaId());
        assertEquals("BOOKING_CANCELLED", finalStatus.getStatus());
        assertEquals("Booking cancelled", finalStatus.getMessage());
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