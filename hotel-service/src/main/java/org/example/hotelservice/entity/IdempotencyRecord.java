package org.example.hotelservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.Hibernate;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "idempotency_records")
@Getter
@Setter
@ToString
@RequiredArgsConstructor
@NoArgsConstructor
public class IdempotencyRecord {
    @Id
    private String idempotencyKey;

    @Column(columnDefinition = "TEXT")
    private String resultData;

    private LocalDateTime processedAt;

    @PrePersist
    public void prePersist() {
        processedAt = LocalDateTime.now();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        IdempotencyRecord that = (IdempotencyRecord) o;
        return idempotencyKey != null && Objects.equals(idempotencyKey, that.idempotencyKey);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}