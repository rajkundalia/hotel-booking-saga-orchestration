package org.example.integration;

import org.example.bookingservice.BookingServiceApplication;
import org.example.bookingservice.entity.SagaInstance;
import org.example.bookingservice.repository.SagaInstanceRepository;
import org.example.bookingservice.service.SagaTimeoutService;
import org.example.common.enumerations.SagaState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.time.LocalDateTime;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = BookingServiceApplication.class)
@DirtiesContext
public class TimeoutHandlingTest {

    @Autowired
    private SagaInstanceRepository sagaRepository;

    @Autowired
    private SagaTimeoutService timeoutService;

    @Test
    void handleTimeouts_ExpiredSagasExist_SagaIsRetriedOrCompensated() {
        // Given - Create an expired saga
        SagaInstance saga = new SagaInstance();
        saga.setSagaId("expired-saga-123");
        saga.setState(SagaState.STARTED);
        saga.setSagaData("{}");
        saga = sagaRepository.save(saga);

        saga.setExpiresAt(LocalDateTime.now().minusMinutes(1)); // Already expired
        SagaInstance finalSaga = sagaRepository.save(saga);

        // When - Run timeout handler
        timeoutService.handleTimeouts();

        // Then - Saga should be retried or compensated
        await().untilAsserted(() -> {
            SagaInstance updatedSaga = sagaRepository.findById(finalSaga.getSagaId()).orElseThrow();
            assertTrue(updatedSaga.getRetryCount() > 0);
        });
    }

    @Test
    void retryFailedSagas_RetryableNonExpiredSagasExist_SagaIsRetried() {
        // Given - Create a retryable saga that is NOT expired
        SagaInstance saga = new SagaInstance();
        saga.setSagaId("retryable-saga-456");
        saga.setState(SagaState.ROOM_RESERVATION_FAILED);
        saga.setSagaData("{}");
        saga.setRetryCount(1);
        SagaInstance finalSaga = sagaRepository.save(saga);

        // When - Run retryFailedSagas
        timeoutService.retryFailedSagas();

        // Then - Saga should be retried (retryCount > 0)
        await().untilAsserted(() -> {
            SagaInstance updatedSaga = sagaRepository.findById(finalSaga.getSagaId()).orElseThrow();
            assertTrue(updatedSaga.getRetryCount() > 1);
        });
    }
}
