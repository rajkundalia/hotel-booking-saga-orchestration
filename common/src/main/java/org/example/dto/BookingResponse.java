package org.example.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BookingResponse {
    private String bookingId;
    private String sagaId;
    private String status;
    private String message;
    private LocalDateTime timestamp;

    public static BookingResponse success(String bookingId, String sagaId) {
        BookingResponse response = new BookingResponse();
        response.setBookingId(bookingId);
        response.setSagaId(sagaId);
        response.setStatus("PROCESSING");
        response.setMessage("Booking is being processed");
        response.setTimestamp(LocalDateTime.now());
        return response;
    }

    public static BookingResponse failure(String sagaId, String message) {
        BookingResponse response = new BookingResponse();
        response.setSagaId(sagaId);
        response.setStatus("FAILED");
        response.setMessage(message);
        response.setTimestamp(LocalDateTime.now());
        return response;
    }
}