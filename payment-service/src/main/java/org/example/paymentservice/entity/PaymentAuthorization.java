package org.example.paymentservice.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.example.paymentservice.enumeration.PaymentStatus;
import org.hibernate.Hibernate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "payment_authorizations")
@Getter
@Setter
@ToString
@RequiredArgsConstructor
public class PaymentAuthorization {
    @Id
    private String authorizationId;

    private String cardNumber;
    private String cardHolderName;
    private BigDecimal amount;
    private String currency;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    private LocalDateTime authorizedAt;
    private LocalDateTime updatedAt;

    /*
        - The @Version annotation in JPA implements optimistic locking.
        - When a PaymentAuthorization entity is read, the version number is also read.
        - When the entity is updated, the JPA provider checks if the version number in the database still matches
          the version number that was originally read. If they match, the update is performed and the version number
          is incremented.
        - If they don't match, it means another transaction has modified the entity, and an OptimisticLockException
          is thrown, preventing a "lost update" and ensuring data integrity.
     */
    @Version
    private Long version;

    @PrePersist
    public void prePersist() {
        authorizedAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        PaymentAuthorization that = (PaymentAuthorization) o;
        return authorizationId != null && Objects.equals(authorizationId, that.authorizationId);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}