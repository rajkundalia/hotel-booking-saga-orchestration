package org.example.hotelservice.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "idempotency_records")
@Data
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
}