package org.example.paymentservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.common.command.AuthorizePaymentCommand;
import org.example.common.command.CancelPaymentCommand;
import org.example.common.command.CommandResult;
import org.example.common.dto.PaymentAuthorizationDto;
import org.example.paymentservice.entity.IdempotencyRecord;
import org.example.paymentservice.entity.PaymentAuthorization;
import org.example.paymentservice.enumeration.PaymentStatus;
import org.example.paymentservice.repository.IdempotencyRepository;
import org.example.paymentservice.repository.PaymentAuthorizationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Random;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentAuthorizationRepository paymentRepository;
    private final IdempotencyRepository idempotencyRepository;
    private final ObjectMapper objectMapper;
    private final Random random = new Random();

    @Value("${payment.simulation.delay:0}")
    private int simulationDelay;

    @Value("${payment.simulation.failure-rate:0.0}")
    private double failureRate;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CommandResult<PaymentAuthorizationDto> authorizePayment(AuthorizePaymentCommand command) {
        log.info("Processing payment authorization for saga: {}", command.getSagaId());

        // Check idempotency
        Optional<IdempotencyRecord> existingRecord =
                idempotencyRepository.findById(command.getIdempotencyKey());

        if (existingRecord.isPresent()) {
            log.info("Idempotent request detected for key: {}", command.getIdempotencyKey());
            try {
                PaymentAuthorizationDto cachedResult = objectMapper.readValue(
                        existingRecord.get().getResultData(), PaymentAuthorizationDto.class);
                return CommandResult.success(cachedResult);
            } catch (Exception e) {
                log.error("Failed to deserialize cached result", e);
            }
        }

        // Simulate delay and failures
        simulateDelay();
        if (shouldSimulateFailure()) {
            return CommandResult.failure("Simulated payment service failure", "PAYMENT_SERVICE_ERROR");
        }

        try {
            // Validate card (simplified validation)
            if (!isValidCard(command.getCardNumber())) {
                return CommandResult.failure("Invalid card number", "INVALID_CARD");
            }

            // Check for insufficient funds (simulate randomly)
            if (random.nextDouble() < 0.1) { // 10% chance of insufficient funds
                return CommandResult.failure("Insufficient funds", "INSUFFICIENT_FUNDS");
            }

            // Create payment authorization
            PaymentAuthorization authorization = new PaymentAuthorization();
            authorization.setAuthorizationId(UUID.randomUUID().toString());
            authorization.setCardNumber(maskCardNumber(command.getCardNumber()));
            authorization.setCardHolderName(command.getCardHolderName());
            authorization.setAmount(command.getAmount());
            authorization.setCurrency(command.getCurrency());
            authorization.setStatus(PaymentStatus.AUTHORIZED);

            authorization = paymentRepository.save(authorization);

            PaymentAuthorizationDto result = mapToDto(authorization);

            // Store idempotency record
            storeIdempotencyRecord(command.getIdempotencyKey(), result);

            log.info("Payment authorized successfully: {}", authorization.getAuthorizationId());
            return CommandResult.success(result);

        } catch (OptimisticLockingFailureException e) {
            log.warn("Optimistic locking failure for saga: {}", command.getSagaId());
            return CommandResult.failure("Concurrent modification detected", "OPTIMISTIC_LOCK_FAILURE");
        } catch (Exception e) {
            log.error("Failed to authorize payment for saga: " + command.getSagaId(), e);
            return CommandResult.failure("Internal server error", "INTERNAL_ERROR");
        }
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CommandResult<Void> cancelPayment(CancelPaymentCommand command) {
        log.info("Processing payment cancellation for authorization: {}", command.getAuthorizationId());

        // Check idempotency
        Optional<IdempotencyRecord> existingRecord =
                idempotencyRepository.findById(command.getIdempotencyKey());

        if (existingRecord.isPresent()) {
            log.info("Idempotent request detected for key: {}", command.getIdempotencyKey());
            return CommandResult.success(null);
        }

        try {
            Optional<PaymentAuthorization> authorizationOpt =
                    paymentRepository.findByIdForUpdate(command.getAuthorizationId());

            if (authorizationOpt.isEmpty()) {
                return CommandResult.failure("Authorization not found", "AUTHORIZATION_NOT_FOUND");
            }

            PaymentAuthorization authorization = authorizationOpt.get();

            if (authorization.getStatus() == PaymentStatus.CANCELLED) {
                log.info("Authorization already cancelled: {}", command.getAuthorizationId());
                storeIdempotencyRecord(command.getIdempotencyKey(), null);
                return CommandResult.success(null);
            }

            if (authorization.getStatus() != PaymentStatus.AUTHORIZED) {
                return CommandResult.failure(
                        "Cannot cancel authorization in status: " + authorization.getStatus(),
                        "INVALID_STATUS");
            }

            authorization.setStatus(PaymentStatus.CANCELLED);
            paymentRepository.save(authorization);

            // Store idempotency record
            storeIdempotencyRecord(command.getIdempotencyKey(), null);

            log.info("Payment cancelled successfully: {}", command.getAuthorizationId());
            return CommandResult.success(null);

        } catch (Exception e) {
            log.error("Failed to cancel payment: " + command.getAuthorizationId(), e);
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

    private boolean isValidCard(String cardNumber) {
        // Simplified Luhn algorithm check
        if (cardNumber == null || cardNumber.length() != 16) {
            return false;
        }

        try {
            Long.parseLong(cardNumber);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private String maskCardNumber(String cardNumber) {
        if (cardNumber == null || cardNumber.length() < 4) {
            return "****";
        }
        return "**** **** **** " + cardNumber.substring(cardNumber.length() - 4);
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

    private PaymentAuthorizationDto mapToDto(PaymentAuthorization authorization) {
        PaymentAuthorizationDto dto = new PaymentAuthorizationDto();
        dto.setAuthorizationId(authorization.getAuthorizationId());
        dto.setCardNumber(authorization.getCardNumber());
        dto.setCardHolderName(authorization.getCardHolderName());
        dto.setAmount(authorization.getAmount());
        dto.setCurrency(authorization.getCurrency());
        dto.setStatus(authorization.getStatus().name());
        dto.setAuthorizedAt(authorization.getAuthorizedAt());
        dto.setVersion(authorization.getVersion());
        return dto;
    }
}