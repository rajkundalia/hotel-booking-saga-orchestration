package org.example.integration;

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

@SpringBootTest
@DirtiesContext
class TimeoutHandlingTest {

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
}
