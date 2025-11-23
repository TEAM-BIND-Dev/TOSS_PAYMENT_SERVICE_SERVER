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

    // 이벤트 ID (기본키, 자동 증가)
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_id")
    private Long eventId;

    // 집합 루트 ID (결제 ID 또는 환불 ID)
    @Column(name = "aggregate_id", nullable = false, length = 50)
    private String aggregateId;

    // 이벤트 타입 (결제 완료, 결제 취소, 환불 완료)
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private EventType eventType;

    // 이벤트 페이로드 (JSON 형식)
    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    // 이벤트 상태 (대기, 발행 완료, 실패)
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EventStatus status;

    // 이벤트 생성 시각
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // Kafka 발행 완료 시각
    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    // 재시도 횟수
    @Column(name = "retry_count", nullable = false)
    private Integer retryCount;

    // 발행 실패 에러 메시지
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