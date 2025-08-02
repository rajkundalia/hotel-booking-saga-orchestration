package org.example.common.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class BookingRequest {
    @NotNull
    private Long hotelId;

    @NotBlank
    private String roomType;

    @NotNull
    @Future
    private LocalDate checkIn;

    @NotNull
    @Future
    private LocalDate checkOut;

    @NotBlank
    private String guestName;

    @NotNull
    @DecimalMin("0.01")
    private BigDecimal roomPrice;

    @NotBlank
    @Size(min = 16, max = 16)
    private String cardNumber;

    @NotBlank
    private String cardHolderName;

    @NotBlank
    @Pattern(regexp = "^(0[1-9]|1[0-2])$")
    private String expiryMonth;

    @NotBlank
    @Pattern(regexp = "^\\d{4}$")
    private String expiryYear;

    @NotBlank
    @Pattern(regexp = "^\\d{3,4}$")
    private String cvv;
}