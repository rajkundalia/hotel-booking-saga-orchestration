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
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = BookingServiceApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class SagaOrchestrationHappyPathIT {

    private static final WireMockServer hotelService = new WireMockServer(8081);
    private static final WireMockServer paymentService = new WireMockServer(8082);

    @Autowired
    private BookingService bookingService;

    @Autowired
    private SagaInstanceRepository sagaRepository;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("services.hotel.url", () -> "http://localhost:8081");
        registry.add("services.payment.url", () -> "http://localhost:8082");
        registry.add("hotel.simulation.delay", () -> "100");
        registry.add("payment.simulation.delay", () -> "100");
        registry.add("hotel.simulation.failure-rate", () -> "0.0");
        registry.add("payment.simulation.failure-rate", () -> "0.0");
    }

    @BeforeEach
    void setup() {
        hotelService.start();
        paymentService.start();
        hotelService.resetAll();
        paymentService.resetAll();
    }

    @AfterEach
    void teardown() {
        hotelService.stop();
        paymentService.stop();
    }

    @Test
    void createBookingSaga_ValidRequest_CompletesSuccessfully() throws Exception {
        // Stub hotel reservation service
        hotelService.stubFor(post(urlEqualTo("/api/hotel/reserve"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(mockHotelReservationResponse())));

        // Stub hotel release (not called in happy path, but can be stubbed)
        hotelService.stubFor(post(urlEqualTo("/api/hotel/release"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(mockHotelReleaseResponse())));

        // Stub payment authorization for SUCCESS
        paymentService.stubFor(post(urlEqualTo("/api/payment/authorize"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(mockPaymentAuthorizationResponse())));

        // Optionally stub payment void/cancel endpoint, but not needed for happy path

        // When
        BookingRequest request = createValidBookingRequest();
        BookingResponse response = bookingService.createBooking(request);

        // Then
        assertNotNull(response);
        assertNotNull(response.getSagaId());
        assertEquals("PROCESSING", response.getStatus());

        // Wait for saga completion
        await().untilAsserted(() -> {
            Optional<SagaInstance> sagaOpt = sagaRepository.findById(response.getSagaId());
            assertTrue(sagaOpt.isPresent());
            assertEquals(SagaState.BOOKING_COMPLETED, sagaOpt.get().getState());
            assertNotNull(sagaOpt.get().getReservationId());
            assertNotNull(sagaOpt.get().getAuthorizationId());
        });

        // Verify final booking status
        BookingResponse finalStatus = bookingService.getBookingStatus(response.getSagaId());
        assertEquals("BOOKING_COMPLETED", finalStatus.getStatus());
        assertEquals("Booking completed successfully", finalStatus.getMessage());
    }

    private String mockHotelReservationResponse() throws JsonProcessingException {
        String mockReservationId = "hotel-reservation-123";

        ReservationDto reservationDto = new ReservationDto();
        reservationDto.setReservationId(mockReservationId);
        reservationDto.setHotelId(1L);
        reservationDto.setRoomType("STANDARD");
        reservationDto.setCheckIn(LocalDate.now().plusDays(1));
        reservationDto.setCheckOut(LocalDate.now().plusDays(3));
        reservationDto.setGuestName("John Doe");
        reservationDto.setRoomPrice(new BigDecimal("199.99"));

        CommandResult<ReservationDto> commandResult = CommandResult.success(reservationDto);

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        return objectMapper.writeValueAsString(commandResult);
    }

    private String mockPaymentAuthorizationResponse() throws JsonProcessingException {
        PaymentAuthorizationDto paymentDto = new PaymentAuthorizationDto();
        paymentDto.setAuthorizationId("auth-456");
        paymentDto.setAmount(new BigDecimal("199.99"));
        paymentDto.setCardHolderName("John Doe");
        paymentDto.setStatus("AUTHORIZED");

        CommandResult<PaymentAuthorizationDto> commandResult = CommandResult.success(paymentDto);

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        return objectMapper.writeValueAsString(commandResult);
    }

    private String mockHotelReleaseResponse() throws JsonProcessingException {
        CommandResult<Void> commandResult = CommandResult.success(null);

        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.writeValueAsString(commandResult);
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