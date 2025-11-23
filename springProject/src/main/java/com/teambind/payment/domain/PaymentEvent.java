package com.teambind.payment.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "payment_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_id")
    private Long eventId;

    @Column(name = "aggregate_id", nullable = false, length = 50)
    private String aggregateId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private EventType eventType;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EventStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Builder
    private PaymentEvent(Long eventId, String aggregateId, EventType eventType, String payload,
                         EventStatus status, LocalDateTime createdAt, LocalDateTime publishedAt,
                         Integer retryCount, String errorMessage) {
        this.eventId = eventId;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.status = status;
        this.createdAt = createdAt;
        this.publishedAt = publishedAt;
        this.retryCount = retryCount;
        this.errorMessage = errorMessage;
    }

    public static PaymentEvent create(String aggregateId, EventType eventType, String payload) {
        validateAggregateId(aggregateId);
        validateEventType(eventType);
        validatePayload(payload);

        return PaymentEvent.builder()
                .aggregateId(aggregateId)
                .eventType(eventType)
                .payload(payload)
                .status(EventStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .retryCount(0)
                .build();
    }

    public void markAsPublished() {
        if (this.status == EventStatus.PUBLISHED) {
            throw new IllegalStateException("이미 발행된 이벤트입니다");
        }

        this.status = EventStatus.PUBLISHED;
        this.publishedAt = LocalDateTime.now();
    }

    public void markAsFailed(String errorMessage) {
        validateErrorMessage(errorMessage);

        this.status = EventStatus.FAILED;
        this.errorMessage = errorMessage;
        this.retryCount++;
    }

    public void resetForRetry() {
        if (this.status == EventStatus.PUBLISHED) {
            throw new IllegalStateException("이미 발행된 이벤트는 재시도할 수 없습니다");
        }

        this.status = EventStatus.PENDING;
        this.errorMessage = null;
    }

    public boolean isPending() {
        return this.status == EventStatus.PENDING;
    }

    public boolean isPublished() {
        return this.status == EventStatus.PUBLISHED;
    }

    public boolean isFailed() {
        return this.status == EventStatus.FAILED;
    }

    public boolean canRetry(int maxRetryCount) {
        return this.retryCount < maxRetryCount && this.status == EventStatus.FAILED;
    }

    private static void validateAggregateId(String aggregateId) {
        if (aggregateId == null || aggregateId.isBlank()) {
            throw new IllegalArgumentException("Aggregate ID는 필수입니다");
        }
    }

    private static void validateEventType(EventType eventType) {
        if (eventType == null) {
            throw new IllegalArgumentException("Event Type은 필수입니다");
        }
    }

    private static void validatePayload(String payload) {
        if (payload == null || payload.isBlank()) {
            throw new IllegalArgumentException("Payload는 필수입니다");
        }
    }

    private static void validateErrorMessage(String errorMessage) {
        if (errorMessage == null || errorMessage.isBlank()) {
            throw new IllegalArgumentException("Error message는 필수입니다");
        }
    }
}