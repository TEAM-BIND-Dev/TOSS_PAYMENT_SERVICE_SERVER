# 결제 플로우

> Version: v0.0.1-SNAPSHOT | Java 21 | Spring Boot 3.5.7

## 개요

Toss Payment Service는 이벤트 기반 결제 준비와 Toss Payments API를 활용한 결제 승인을 제공합니다. 결제 취소도 지원하며, 모든 과정에서 트랜잭션 안전성과 멱등성을 보장합니다.

## 결제 생명주기

```
예약 확정 → 결제 준비 → 결제 승인 → 결제 완료
                ↓           ↓           ↓
              실패        실패        취소
```

---

## 1. 결제 준비 (Payment Prepare)

### 개요
예약 서비스에서 발행한 `ReservationConfirmedEvent`를 수신하여 결제를 준비합니다.

### 시퀀스 다이어그램

```
Reservation Service    Kafka    Payment Consumer    PaymentPrepareService    Payment Repository
       │                │              │                     │                       │
       │ Publish Event  │              │                     │                       │
       │───────────────>│              │                     │                       │
       │                │ Consume      │                     │                       │
       │                │─────────────>│                     │                       │
       │                │              │ preparePayment()    │                       │
       │                │              │────────────────────>│                       │
       │                │              │                     │ findByReservationId() │
       │                │              │                     │──────────────────────>│
       │                │              │                     │                       │
       │                │              │                     │<──────────────────────│
       │                │              │                     │ Optional.empty()      │
       │                │              │                     │                       │
       │                │              │                     │ Payment.prepare()     │
       │                │              │                     │─────────┐            │
       │                │              │                     │         │            │
       │                │              │                     │<────────┘            │
       │                │              │                     │ save(payment)        │
       │                │              │                     │──────────────────────>│
       │                │              │                     │                       │
       │                │              │                     │<──────────────────────│
       │                │              │<────────────────────│                       │
       │                │              │ Payment (PREPARED)  │                       │
       │                │              │                     │                       │
```

### 이벤트 구조

**ReservationConfirmedEvent**
```json
{
  "reservationId": "reservation-123",
  "userId": "user-456",
  "accommodationId": "hotel-789",
  "checkInDate": "2025-12-01T15:00:00",
  "checkOutDate": "2025-12-03T11:00:00",
  "totalAmount": 100000,
  "confirmedAt": "2025-11-23T10:30:00"
}
```

### 구현 코드

**Kafka Consumer**
```java
@KafkaListener(topics = "reservation-confirmed", groupId = "payment-service")
public void handleReservationConfirmed(ReservationConfirmedEvent event) {
    log.info("예약 확정 이벤트 수신 - reservationId: {}", event.reservationId());

    Payment payment = paymentPrepareService.preparePayment(
        event.reservationId(),
        event.totalAmount(),
        event.checkInDate()
    );

    log.info("결제 준비 완료 - paymentId: {}", payment.getPaymentId());
}
```

**PaymentPrepareService**
```java
@Transactional
public Payment preparePayment(String reservationId, Long amount, LocalDateTime checkInDate) {
    // 멱등성 체크 - 이미 처리된 예약인지 확인
    return paymentRepository.findByReservationId(reservationId)
        .map(existingPayment -> {
            log.info("이미 처리된 예약입니다 - paymentId: {}", existingPayment.getPaymentId());
            return existingPayment;
        })
        .orElseGet(() -> {
            // 새로운 결제 준비
            Payment payment = Payment.prepare(
                reservationId,
                Money.of(amount),
                checkInDate
            );

            Payment savedPayment = paymentRepository.save(payment);
            log.info("결제 준비 완료 - paymentId: {}, status: {}",
                    savedPayment.getPaymentId(), savedPayment.getStatus());

            return savedPayment;
        });
}
```

### 결제 준비 결과

**Payment Entity**
```java
Payment {
    paymentId: "PAY-A1B2C3D4"
    reservationId: "reservation-123"
    amount: Money { value: 100000, currency: "KRW" }
    status: PREPARED
    checkInDate: 2025-12-01T15:00:00
    idempotencyKey: "IDEM-reservation-123-X9Y8Z7W6"
    createdAt: 2025-11-23T10:30:15
}
```

### 멱등성 보장

1. **reservationId 기준 중복 체크**
   - 동일한 reservationId로 여러 번 이벤트가 수신되어도 하나의 결제만 생성
   - `findByReservationId()`로 기존 결제 확인

2. **idempotencyKey 생성**
   - 형식: `IDEM-{reservationId}-{UUID 8자리}`
   - DB 유니크 제약조건으로 중복 방지

---

## 2. 결제 승인 (Payment Confirm)

### 개요
클라이언트가 Toss 결제 위젯에서 결제를 완료하면, 백엔드에서 Toss API를 호출하여 최종 승인을 진행합니다.

### 시퀀스 다이어그램

```
Client    PaymentController    PaymentConfirmService    PaymentRepository    TossPaymentClient    Toss API    EventPublisher    Kafka
  │             │                      │                       │                     │               │              │            │
  │ POST /confirm                      │                       │                     │               │              │            │
  │────────────>│                      │                       │                     │               │              │            │
  │             │ confirmPayment()     │                       │                     │               │              │            │
  │             │─────────────────────>│                       │                     │               │              │            │
  │             │                      │ findById()            │                     │               │              │            │
  │             │                      │──────────────────────>│                     │               │              │            │
  │             │                      │ Payment (PREPARED)    │                     │               │              │            │
  │             │                      │<──────────────────────│                     │               │              │            │
  │             │                      │                       │                     │               │              │            │
  │             │                      │ validateAmount()      │                     │               │              │            │
  │             │                      │─────────┐            │                     │               │              │            │
  │             │                      │<────────┘            │                     │               │              │            │
  │             │                      │                       │                     │               │              │            │
  │             │                      │ confirmPayment()      │                     │               │              │            │
  │             │                      │──────────────────────────────────────────────────────────────>│            │            │
  │             │                      │                       │                     │ POST /confirm │            │            │
  │             │                      │                       │                     │───────────────>│            │            │
  │             │                      │                       │                     │ 200 OK        │            │            │
  │             │                      │                       │                     │<───────────────│            │            │
  │             │                      │ TossPaymentConfirmResponse                  │               │            │            │
  │             │                      │<──────────────────────────────────────────────────────────────│            │            │
  │             │                      │                       │                     │               │            │            │
  │             │                      │ payment.complete()    │                     │               │            │            │
  │             │                      │─────────┐            │                     │               │            │            │
  │             │                      │<────────┘            │                     │               │            │            │
  │             │                      │ save(payment)         │                     │               │            │            │
  │             │                      │──────────────────────>│                     │               │            │            │
  │             │                      │ Payment (COMPLETED)   │                     │               │            │            │
  │             │                      │<──────────────────────│                     │               │            │            │
  │             │                      │                       │                     │               │            │            │
  │             │                      │ publishPaymentCompletedEvent()              │               │            │            │
  │             │                      │────────────────────────────────────────────────────────────────────────────>│            │
  │             │                      │                       │                     │               │            │ Publish   │
  │             │                      │                       │                     │               │            │──────────>│
  │             │<─────────────────────│                       │                     │               │            │            │
  │<────────────│ Payment              │                       │                     │               │            │            │
  │ 200 OK      │                      │                       │                     │               │            │            │
```

### API 요청/응답

**Request**
```http
POST /api/v1/payments/confirm
Content-Type: application/json

{
  "paymentId": "PAY-A1B2C3D4",
  "orderId": "order-123",
  "paymentKey": "toss-payment-key-abc123",
  "amount": 100000
}
```

**Response**
```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "paymentId": "PAY-A1B2C3D4",
  "reservationId": "reservation-123",
  "amount": 100000,
  "currency": "KRW",
  "method": "CARD",
  "status": "COMPLETED",
  "orderId": "order-123",
  "paymentKey": "toss-payment-key-abc123",
  "transactionId": "transaction-xyz789",
  "paidAt": "2025-11-23T10:35:00"
}
```

### 구현 코드

**PaymentConfirmService**
```java
@Transactional
public Payment confirmPayment(String paymentId, String orderId, String paymentKey, Long amount) {
    log.info("결제 승인 시작 - paymentId: {}, orderId: {}, amount: {}", paymentId, orderId, amount);

    // 1. Payment 조회
    Payment payment = paymentRepository.findById(paymentId)
            .orElseThrow(() -> PaymentException.notFound(paymentId));

    // 2. 금액 검증
    payment.validateAmount(Money.of(amount));

    // 3. 토스 결제 승인 요청
    TossPaymentConfirmRequest request = new TossPaymentConfirmRequest(
            paymentKey,
            orderId,
            amount
    );

    TossPaymentConfirmResponse response = tossPaymentClient.confirmPayment(request);

    // 4. 결제 완료 처리
    PaymentMethod method = PaymentMethod.valueOf(mapTossMethodToPaymentMethod(response.method()));
    payment.complete(orderId, paymentKey, response.transactionId(), method);

    // 5. 저장
    Payment savedPayment = paymentRepository.save(payment);
    log.info("결제 승인 완료 - paymentId: {}, status: {}, method: {}",
            savedPayment.getPaymentId(), savedPayment.getStatus(), savedPayment.getMethod());

    // 6. 결제 완료 이벤트 발행
    PaymentCompletedEvent event = PaymentCompletedEvent.from(savedPayment);
    paymentEventPublisher.publishPaymentCompletedEvent(event);

    return savedPayment;
}
```

### Toss API 통신

**Request to Toss**
```json
{
  "paymentKey": "toss-payment-key-abc123",
  "orderId": "order-123",
  "amount": 100000
}
```

**Response from Toss**
```json
{
  "paymentKey": "toss-payment-key-abc123",
  "orderId": "order-123",
  "paymentType": "NORMAL",
  "transactionId": "transaction-xyz789",
  "amount": 100000,
  "method": "CARD",
  "status": "DONE",
  "approvedAt": "2025-11-23T10:35:00"
}
```

### 결제 완료 이벤트 발행

**PaymentCompletedEvent**
```json
{
  "paymentId": "PAY-A1B2C3D4",
  "reservationId": "reservation-123",
  "amount": 100000,
  "currency": "KRW",
  "method": "CARD",
  "orderId": "order-123",
  "paymentKey": "toss-payment-key-abc123",
  "transactionId": "transaction-xyz789",
  "paidAt": "2025-11-23T10:35:00"
}
```

**Outbox Pattern 적용**:
1. PaymentEvent 테이블에 이벤트 저장 (status: PENDING)
2. OutboxEventScheduler가 주기적으로 PENDING 이벤트 조회
3. Kafka로 발행 후 status를 PUBLISHED로 변경

---

## 3. 결제 취소 (Payment Cancel)

### 개요
완료된 결제를 Toss API를 통해 전액 취소합니다.

### 시퀀스 다이어그램

```
Client    PaymentController    PaymentCancelService    PaymentRepository    TossRefundClient    Toss API    EventPublisher    Kafka
  │             │                      │                       │                     │               │              │            │
  │ POST /cancel                       │                       │                     │               │              │            │
  │────────────>│                      │                       │                     │               │              │            │
  │             │ cancelPayment()      │                       │                     │               │              │            │
  │             │─────────────────────>│                       │                     │               │              │            │
  │             │                      │ findById()            │                     │               │              │            │
  │             │                      │──────────────────────>│                     │               │              │            │
  │             │                      │ Payment (COMPLETED)   │                     │               │              │            │
  │             │                      │<──────────────────────│                     │               │              │            │
  │             │                      │                       │                     │               │              │            │
  │             │                      │ cancelPayment()       │                     │               │              │            │
  │             │                      │──────────────────────────────────────────────────────────────>│            │            │
  │             │                      │                       │                     │ POST /cancel  │            │            │
  │             │                      │                       │                     │───────────────>│            │            │
  │             │                      │                       │                     │ 200 OK        │            │            │
  │             │                      │                       │                     │<───────────────│            │            │
  │             │                      │ TossRefundResponse    │                     │               │            │            │
  │             │                      │<──────────────────────────────────────────────────────────────│            │            │
  │             │                      │                       │                     │               │            │            │
  │             │                      │ payment.cancel()      │                     │               │            │            │
  │             │                      │─────────┐            │                     │               │            │            │
  │             │                      │<────────┘            │                     │               │            │            │
  │             │                      │ save(payment)         │                     │               │              │            │
  │             │                      │──────────────────────>│                     │               │              │            │
  │             │                      │ Payment (CANCELLED)   │                     │               │              │            │
  │             │                      │<──────────────────────│                     │               │              │            │
  │             │                      │                       │                     │               │              │            │
  │             │                      │ publishPaymentCancelledEvent()              │               │              │            │
  │             │                      │────────────────────────────────────────────────────────────────────────────>│            │
  │             │                      │                       │                     │               │              │ Publish   │
  │             │                      │                       │                     │               │              │──────────>│
  │             │<─────────────────────│                       │                     │               │              │            │
  │<────────────│ Payment              │                       │                     │               │              │            │
  │ 200 OK      │                      │                       │                     │               │              │            │
```

### API 요청/응답

**Request**
```http
POST /api/v1/payments/{paymentId}/cancel
Content-Type: application/json

{
  "reason": "고객 요청에 의한 결제 취소"
}
```

**Response**
```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "paymentId": "PAY-A1B2C3D4",
  "status": "CANCELLED",
  "cancelledAt": "2025-11-23T11:00:00"
}
```

### 구현 코드

**PaymentCancelService**
```java
@Transactional
public Payment cancelPayment(String paymentId, String reason) {
    log.info("결제 취소 시작 - paymentId: {}, reason: {}", paymentId, reason);

    // 1. Payment 조회
    Payment payment = paymentRepository.findById(paymentId)
            .orElseThrow(() -> PaymentException.notFound(paymentId));

    try {
        // 2. 토스 결제 취소 API 호출 (전액 취소)
        TossRefundRequest request = new TossRefundRequest(
                reason,
                payment.getAmount().getValue().longValue()
        );

        TossRefundResponse response = tossRefundClient.cancelPayment(payment.getPaymentKey(), request);

        // 3. Payment 취소 처리
        payment.cancel();

        Payment canceledPayment = paymentRepository.save(payment);

        log.info("결제 취소 완료 - paymentId: {}, transactionId: {}, status: {}",
                canceledPayment.getPaymentId(), response.transactionId(), canceledPayment.getStatus());

        // 4. 결제 취소 이벤트 발행
        PaymentCancelledEvent event = PaymentCancelledEvent.from(canceledPayment);
        paymentEventPublisher.publishPaymentCancelledEvent(event);

        return canceledPayment;

    } catch (Exception e) {
        log.error("결제 취소 실패 - paymentId: {}, error: {}", paymentId, e.getMessage(), e);
        payment.fail("결제 취소 실패: " + e.getMessage());
        paymentRepository.save(payment);
        throw new TossApiException(
                ErrorCode.TOSS_API_ERROR,
                "Payment cancellation failed for: " + paymentId,
                e
        );
    }
}
```

---

## 예외 처리

### 결제 준비 실패

**시나리오**: 이미 처리된 예약
- **처리**: 멱등성 체크를 통해 기존 결제 반환
- **결과**: 200 OK, 기존 Payment 반환

### 결제 승인 실패

**시나리오 1**: 결제 정보를 찾을 수 없음
```json
{
  "timestamp": "2025-11-23T10:35:00",
  "status": 404,
  "code": "PAYMENT_001",
  "message": "Payment not found: PAY-INVALID",
  "path": "/api/v1/payments/confirm",
  "exceptionType": "PAYMENT_DOMAIN"
}
```

**시나리오 2**: 금액 불일치
```json
{
  "timestamp": "2025-11-23T10:35:00",
  "status": 400,
  "code": "PAYMENT_004",
  "message": "Payment amount mismatch - expected: 100000, actual: 90000",
  "path": "/api/v1/payments/confirm",
  "exceptionType": "PAYMENT_DOMAIN"
}
```

**시나리오 3**: Toss API 오류
```json
{
  "timestamp": "2025-11-23T10:35:00",
  "status": 502,
  "code": "TOSS_001",
  "message": "Payment confirmation failed for: PAY-A1B2C3D4",
  "path": "/api/v1/payments/confirm",
  "exceptionType": "EXTERNAL_API"
}
```

### 결제 취소 실패

**시나리오**: 완료되지 않은 결제 취소 시도
```json
{
  "timestamp": "2025-11-23T11:00:00",
  "status": 400,
  "code": "PAYMENT_005",
  "message": "Payment not completed: PAY-A1B2C3D4",
  "path": "/api/v1/payments/PAY-A1B2C3D4/cancel",
  "exceptionType": "PAYMENT_DOMAIN"
}
```

---

## 트랜잭션 관리

### 1. 결제 준비 트랜잭션
- **범위**: PaymentPrepareService.preparePayment()
- **격리 수준**: READ_COMMITTED
- **롤백 조건**: DB 저장 실패

### 2. 결제 승인 트랜잭션
- **범위**: PaymentConfirmService.confirmPayment()
- **격리 수준**: READ_COMMITTED
- **롤백 조건**:
  - 결제 조회 실패
  - 금액 검증 실패
  - Toss API 호출 실패
  - Payment 상태 업데이트 실패

**참고**: Toss API 호출은 외부 시스템이므로 트랜잭션에 포함되지 않지만, 실패 시 전체 트랜잭션이 롤백되어 Payment 상태는 PREPARED로 유지됩니다.

### 3. 결제 취소 트랜잭션
- **범위**: PaymentCancelService.cancelPayment()
- **격리 수준**: READ_COMMITTED
- **롤백 조건**: Toss API 호출 실패 또는 Payment 상태 업데이트 실패

---

## 성능 및 확장성 고려사항

### 1. 결제 준비 이벤트 처리
- Kafka Consumer의 동시성 설정으로 처리량 조절
- 파티션 분산을 통한 부하 분산

### 2. Toss API 호출
- HTTP 타임아웃 설정 (연결: 3초, 읽기: 10초)
- 재시도 로직 (최대 3회)
- Circuit Breaker 패턴 적용 권장

### 3. 이벤트 발행
- Outbox Pattern을 통한 안정적인 이벤트 발행
- 발행 실패 시 자동 재시도 (최대 5회)

---

## 관련 문서

- [Domain Model](../implementation/DOMAIN_MODEL.md)
- [Refund Flow](REFUND_FLOW.md)
- [Event Publishing](EVENT_PUBLISHING.md)
- [Exception Handling](../implementation/EXCEPTION_HANDLING.md)
- [ADR-002: Event-Driven Payment Preparation](../adr/002-event-driven-payment-preparation.md)
- [ADR-003: Toss Payments Integration](../adr/003-toss-payments-integration.md)

---

**최종 업데이트**: 2025-11-23
**작성자**: TeamBind
**PR**: #29
