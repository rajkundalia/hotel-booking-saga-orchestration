package org.example.integration;

import org.example.bookingservice.entity.SagaInstance;
import org.example.common.enumerations.SagaState;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

public class SagaStateTransitionTest {
    
    @Test
    void sagaInstance_ValidStateTransitions_TransitionsCorrectly() {
        SagaInstance saga = new SagaInstance();
        
        // Test valid transitions from STARTED
        saga.setState(SagaState.STARTED);
        assertTrue(saga.canTransitionTo(SagaState.ROOM_RESERVED));
        assertTrue(saga.canTransitionTo(SagaState.ROOM_RESERVATION_FAILED));
        assertTrue(saga.canTransitionTo(SagaState.BOOKING_CANCELLED));
        assertFalse(saga.canTransitionTo(SagaState.PAYMENT_AUTHORIZED));
        
        // Test valid transitions from ROOM_RESERVED
        saga.setState(SagaState.ROOM_RESERVED);
        assertTrue(saga.canTransitionTo(SagaState.PAYMENT_AUTHORIZED));
        assertTrue(saga.canTransitionTo(SagaState.PAYMENT_AUTHORIZATION_FAILED));
        assertTrue(saga.canTransitionTo(SagaState.COMPENSATING));
        assertFalse(saga.canTransitionTo(SagaState.BOOKING_COMPLETED));
        
        // Test valid transitions from PAYMENT_AUTHORIZED
        saga.setState(SagaState.PAYMENT_AUTHORIZED);
        assertTrue(saga.canTransitionTo(SagaState.BOOKING_COMPLETED));
        assertFalse(saga.canTransitionTo(SagaState.COMPENSATING));
        
        // Test valid transitions from failure states
        saga.setState(SagaState.ROOM_RESERVATION_FAILED);
        assertTrue(saga.canTransitionTo(SagaState.BOOKING_CANCELLED));
        assertFalse(saga.canTransitionTo(SagaState.COMPENSATING));
        
        saga.setState(SagaState.PAYMENT_AUTHORIZATION_FAILED);
        assertTrue(saga.canTransitionTo(SagaState.BOOKING_CANCELLED));
        assertFalse(saga.canTransitionTo(SagaState.ROOM_RESERVED));
        
        // Test compensation transitions
        saga.setState(SagaState.COMPENSATING);
        assertTrue(saga.canTransitionTo(SagaState.COMPENSATION_COMPLETED));
        assertTrue(saga.canTransitionTo(SagaState.COMPENSATION_FAILED));
        assertFalse(saga.canTransitionTo(SagaState.BOOKING_COMPLETED));
    }
    
    @Test
    void sagaInstance_RetryLogic_HandlesRetryCountAndCanRetryCorrectly() {
        SagaInstance saga = new SagaInstance();
        saga.setMaxRetries(3);
        
        // Test retry capability
        assertTrue(saga.canRetry());
        assertEquals(0, saga.getRetryCount());
        
        saga.incrementRetry();
        assertEquals(1, saga.getRetryCount());
        assertTrue(saga.canRetry());
        
        saga.incrementRetry();
        saga.incrementRetry();
        assertEquals(3, saga.getRetryCount());
        assertFalse(saga.canRetry()); // Exhausted retries
    }
    
    @Test
    void sagaInstance_SagaExpiration_IsExpiredWhenExpirationTimePassed() {
        SagaInstance saga = new SagaInstance();
        // Saga is created with expiresAt set to now + 30 minutes in prePersist method and createdAt is now, to simulate it here:
        saga.setExpiresAt(LocalDateTime.now().plusMinutes(30));
        saga.setCreatedAt(LocalDateTime.now());
        assertFalse(saga.isExpired());
        
        // Simulate expiration
        saga.setExpiresAt(saga.getCreatedAt().minusMinutes(1));
        assertTrue(saga.isExpired());
    }
}