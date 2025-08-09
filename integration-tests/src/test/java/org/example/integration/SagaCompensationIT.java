package org.example.integration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.example.bookingservice.BookingServiceApplication;
import org.example.bookingservice.entity.SagaInstance;
import org.example.bookingservice.repository.SagaInstanceRepository;
import org.example.bookingservice.service.BookingService;
import org.example.common.command.CommandResult;
import org.example.common.dto.BookingRequest;
import org.example.common.dto.BookingResponse;
import org.example.common.dto.PaymentAuthorizationDto;
import org.example.common.dto.ReservationDto;
import org.example.common.enumerations.SagaState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = BookingServiceApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class SagaCompensationIT {

    // WireMock servers for our two external services
    private static final WireMockServer hotelService = new WireMockServer(8081);
    private static final WireMockServer paymentService = new WireMockServer(8082);

    @Autowired
    private BookingService bookingService;

    @Autowired
    private SagaInstanceRepository sagaRepository;

    /**
     * This method dynamically sets the properties for the test.
     * It ensures our BookingService points to the WireMock servers
     * instead of relying on fixed URLs. This is a more robust approach.
     */
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Set the URLs for the external services to our WireMock servers.
        // We use the same ports as in your original example.
        registry.add("services.hotel.url", () -> "http://localhost:8081");
        registry.add("services.payment.url", () -> "http://localhost:8082");
        registry.add("hotel.simulation.delay", () -> "100");
        registry.add("payment.simulation.delay=", () -> "100");
        registry.add("hotel.simulation.failure-rate", () -> "0.0");
        registry.add("payment.simulation.failure-rate", () -> "1.0");
    }

    /**
     * Starts the WireMock servers before each test.
     * We need to start them before we can configure their behavior (stubbing).
     */
    @BeforeEach
    void setup() {
        hotelService.start();
        paymentService.start();
        // Resetting the stubs ensures a clean slate for each test method
        hotelService.resetAll();
        paymentService.resetAll();
    }

    /**
     * Stops the WireMock servers after each test.
     * This cleans up resources and prevents port conflicts.
     */
    @AfterEach
    void teardown() {
        hotelService.stop();
        paymentService.stop();
    }

    @Test
    void createBookingSaga_PaymentFails_SagaIsCompensated() throws JsonProcessingException {
        // Given:
        // 1. Stub the hotel service to return a successful reservation response.
        // The booking service will call this URL and receive a successful response.
        hotelService.stubFor(post(urlEqualTo("/api/hotel/reserve"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(mockHotelReservationResponse())));

        // 2. We should also stub the compensation endpoint for robustness,
        // even though the failure happens on the payment step.
        hotelService.stubFor(post(urlEqualTo("/api/hotel/release"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(mockHotelReleaseResponse())));

        // 3. Stub the payment service to return a server error (HTTP 500).
        // This simulates a payment failure, triggering the compensation logic.
        paymentService.stubFor(post(urlEqualTo("/api/payment/authorize"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(mockPaymentFailureResponse())));

        // When:
        // A new booking request is created and processed by the booking service.
        BookingRequest request = createValidBookingRequest();
        BookingResponse response = bookingService.createBooking(request);

        // Then:
        // Initial assertions for the immediate response
        assertNotNull(response);
        assertNotNull(response.getSagaId());

        // Wait for the asynchronous saga compensation process to complete.
        // Awaitility is used to poll the repository until the expected state is reached.
        await().atMost(10, SECONDS).untilAsserted(() -> {
            Optional<SagaInstance> sagaOpt = sagaRepository.findById(response.getSagaId());
            assertTrue(sagaOpt.isPresent());
            assertEquals(SagaState.BOOKING_CANCELLED, sagaOpt.get().getState());
            // The hotel reservation should exist because that step succeeded.
            assertNotNull(sagaOpt.get().getReservationId());
            // The payment authorization should be null because that step failed.
            assertNull(sagaOpt.get().getAuthorizationId());
            // Verify that a compensation call was made to the hotel service to cancel the reservation.
            hotelService.verify(postRequestedFor(urlEqualTo("/api/hotel/release"))
                    .withRequestBody(containing("hotel-reservation-123")));
        });

        // Verify the final booking status returned by the service
        BookingResponse finalStatus = bookingService.getBookingStatus(response.getSagaId());
        assertEquals("BOOKING_CANCELLED", finalStatus.getStatus());
        assertEquals("Booking cancelled", finalStatus.getMessage());
    }

    private String mockHotelReservationResponse() throws JsonProcessingException {
        String mockReservationId = "hotel-reservation-123";

        // The hotel service returns a CommandResult<ReservationDto> on success
        ReservationDto reservationDto = new ReservationDto();
        reservationDto.setReservationId(mockReservationId);
        reservationDto.setHotelId(1L);
        reservationDto.setRoomType("STANDARD");
        reservationDto.setCheckIn(LocalDate.now().plusDays(1));
        reservationDto.setCheckOut(LocalDate.now().plusDays(3));
        reservationDto.setGuestName("John Doe");
        reservationDto.setRoomPrice(new BigDecimal("199.99"));

        CommandResult<ReservationDto> commandResult = CommandResult.success(reservationDto);

        // Use ObjectMapper to serialize the CommandResult object to JSON
        ObjectMapper objectMapper = new ObjectMapper();
        // Configure ObjectMapper to handle LocalDate properly, as it's not a standard feature
        objectMapper.registerModule(new JavaTimeModule());
        return objectMapper.writeValueAsString(commandResult);
    }

    private String mockPaymentFailureResponse() throws JsonProcessingException {
        // This matches your actual service code for failure:
        CommandResult<PaymentAuthorizationDto> commandResult =
                CommandResult.failure("Simulated payment service failure", "PAYMENT_SERVICE_ERROR");

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        return objectMapper.writeValueAsString(commandResult);
    }

    private String mockHotelReleaseResponse() throws JsonProcessingException {
        CommandResult<Void> commandResult = CommandResult.success(null);

        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.writeValueAsString(commandResult);
    }


    /**
     * Helper method to create a valid booking request for the test.
     */
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