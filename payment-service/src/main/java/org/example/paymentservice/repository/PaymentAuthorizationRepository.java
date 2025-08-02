package org.example.paymentservice.repository;

import org.example.paymentservice.entity.PaymentAuthorization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface PaymentAuthorizationRepository extends JpaRepository<PaymentAuthorization, String> {

    // Pessimistic write locks the PaymentAuthorization row to prevent other transactions from reading or
    // modifying it until the current transaction is complete.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PaymentAuthorization p WHERE p.authorizationId = :authorizationId")
    Optional<PaymentAuthorization> findByIdForUpdate(@Param("authorizationId") String authorizationId);
}