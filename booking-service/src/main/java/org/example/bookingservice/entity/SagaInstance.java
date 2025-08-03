package org.example.bookingservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.example.common.enumerations.SagaState;
import org.hibernate.Hibernate;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "saga_instances")
@Getter
@Setter
@ToString
@RequiredArgsConstructor
@NoArgsConstructor
public class SagaInstance {
    @Id
    private String sagaId;

    @Enumerated(EnumType.STRING)
    private SagaState state;

    @Column(columnDefinition = "TEXT")
    private String sagaData;

    private String reservationId;
    private String authorizationId;

    private int retryCount = 0;
    private int maxRetries = 3;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime expiresAt;

    @Version
    private Long version;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        expiresAt = LocalDateTime.now().plusMinutes(30); // 30 minutes timeout
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public boolean canTransitionTo(SagaState newState) {
        return switch (state) {
            case STARTED -> newState == SagaState.ROOM_RESERVED ||
                    newState == SagaState.ROOM_RESERVATION_FAILED ||
                    newState == SagaState.BOOKING_CANCELLED;
            case ROOM_RESERVED -> newState == SagaState.PAYMENT_AUTHORIZED ||
                    newState == SagaState.PAYMENT_AUTHORIZATION_FAILED ||
                    newState == SagaState.COMPENSATING;
            case PAYMENT_AUTHORIZED -> newState == SagaState.BOOKING_COMPLETED;
            case ROOM_RESERVATION_FAILED, PAYMENT_AUTHORIZATION_FAILED -> newState == SagaState.BOOKING_CANCELLED;
            case COMPENSATING -> newState == SagaState.COMPENSATION_COMPLETED ||
                    newState == SagaState.COMPENSATION_FAILED;
            default -> false;
        };
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    public boolean canRetry() {
        return retryCount < maxRetries;
    }

    public void incrementRetry() {
        retryCount++;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        SagaInstance that = (SagaInstance) o;
        return sagaId != null && Objects.equals(sagaId, that.sagaId);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}