package org.example.bookingservice.repository;

import org.example.bookingservice.entity.SagaInstance;
import org.example.common.enumerations.SagaState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SagaInstanceRepository extends JpaRepository<SagaInstance, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SagaInstance s WHERE s.sagaId = :sagaId")
    Optional<SagaInstance> findByIdForUpdate(@Param("sagaId") String sagaId);

    @Query("SELECT s FROM SagaInstance s WHERE s.expiresAt < :now AND s.state NOT IN :finalStates")
    List<SagaInstance> findExpiredSagas(@Param("now") LocalDateTime now,
                                        @Param("finalStates") List<SagaState> finalStates);

    @Query("SELECT s FROM SagaInstance s WHERE s.state IN :states AND s.retryCount < s.maxRetries")
    List<SagaInstance> findRetryableSagas(@Param("states") List<SagaState> states);
}