package com.techstore.notificaciones.outbox;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.TenantId;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "notification_outbox")
@Getter
@Setter
@NoArgsConstructor
public class NotificationOutbox {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @TenantId @Column(name = "tenant_id", nullable = false)
    private Long tenantId;
    @Enumerated(EnumType.STRING) @Column(name = "event_type", nullable = false, length = 40)
    private NotificationEventType eventType;
    @Enumerated(EnumType.STRING) @Column(name = "consumer_channel", nullable = false, length = 20)
    private OutboxChannel consumerChannel;
    @Column(name = "aggregate_type", nullable = false, length = 40)
    private String aggregateType;
    @Column(name = "aggregate_id", nullable = false)
    private Long aggregateId;
    @Column(name = "recipient_user_id")
    private Long recipientUserId;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private OutboxStatus status;
    @Column(nullable = false)
    private int attempts;
    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;
    @Column(name = "claimed_at")
    private LocalDateTime claimedAt;
    @Column(name = "processed_at")
    private LocalDateTime processedAt;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    @Column(name = "last_error_sanitized", length = 500)
    private String lastErrorSanitized;
    @Column(name = "idempotency_key", nullable = false, length = 220)
    private String idempotencyKey;
}
