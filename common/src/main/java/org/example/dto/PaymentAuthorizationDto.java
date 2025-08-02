package org.example.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PaymentAuthorizationDto {
    private String authorizationId;
    private String cardNumber;
    private String cardHolderName;
    private BigDecimal amount;
    private String currency;
    private String status;
    private LocalDateTime authorizedAt;
    private Long version;
}