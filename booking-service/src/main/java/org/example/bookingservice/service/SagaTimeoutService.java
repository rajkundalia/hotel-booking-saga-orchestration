package org.example.bookingservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.bookingservice.entity.SagaInstance;
import org.example.bookingservice.repository.SagaInstanceRepository;
import org.example.common.enumerations.SagaState;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SagaTimeoutService {

    private final SagaInstanceRepository sagaRepository;
    private final SagaOrchestrator sagaOrchestrator;

    private static final List<SagaState> FINAL_STATES = Arrays.asList(
            SagaState.BOOKING_COMPLETED,
            SagaState.BOOKING_CANCELLED,
            SagaState.COMPENSATION_FAILED
    );

    @Scheduled(fixedDelay = 30000) // Check every 30 seconds
    @Transactional
    public void handleTimeouts() {
        log.debug("Checking for timed-out sagas");

        List<SagaInstance> expiredSagas = sagaRepository.findExpiredSagas(LocalDateTime.now(), FINAL_STATES);

        for (SagaInstance saga : expiredSagas) {
            log.warn("Saga {} has timed out, state: {}", saga.getSagaId(), saga.getState());

            try {
                sagaOrchestrator.retrySaga(saga.getSagaId());
            } catch (Exception e) {
                log.error("Error handling timeout for saga: " + saga.getSagaId(), e);
            }
        }
    }

    @Scheduled(fixedDelay = 60000) // Check every minute
    @Transactional
    public void retryFailedSagas() {
        log.debug("Checking for retryable sagas");

        List<SagaState> retryableStates = Arrays.asList(
                SagaState.ROOM_RESERVATION_FAILED,
                SagaState.PAYMENT_AUTHORIZATION_FAILED,
                SagaState.COMPENSATION_FAILED
        );

        List<SagaInstance> retryableSagas = sagaRepository.findRetryableSagas(retryableStates);

        for (SagaInstance saga : retryableSagas) {
            if (saga.isExpired()) {
                continue; // Will be handled by timeout processor
            }

            log.info("Retrying failed saga: {}", saga.getSagaId());

            try {
                sagaOrchestrator.retrySaga(saga.getSagaId());
            } catch (Exception e) {
                log.error("Error retrying saga: " + saga.getSagaId(), e);
            }
        }
    }
}
