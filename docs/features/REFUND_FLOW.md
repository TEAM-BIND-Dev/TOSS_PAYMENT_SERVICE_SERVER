# 환불 플로우

> Version: v0.0.1-SNAPSHOT | Java 21 | Spring Boot 3.5.7

## 개요

Toss Payment Service는 체크인 날짜 기준 환불 정책을 적용한 부분/전액 환불을 지원합니다. RefundPolicy를 통해 환불 금액을 자동 계산하고, Toss Payments API를 통해 실제
환불을 처리합니다.

## 환불 생명주기

```
환불 요청 → 정책 적용 → 환불 승인 → Toss API 호출 → 환불 완료
              ↓            ↓           ↓                  ↓
          환불 불가      검증 실패    API 실패         실패
```

---

## 환불 정책

### 정책 규칙

| 체크인까지 남은 기간   | 환불율  | 환불 금액 (원금 100,000원 기준) |
|---------------|------|------------------------|
| 7일 이상         | 100% | 100,000원 (전액)          |
| 3일 이상 ~ 7일 미만 | 50%  | 50,000원 (50%)          |
| 3일 미만         | 0%   | 환불 불가                  |

### RefundPolicy 클래스

```java
public class RefundPolicy {
    private static final int FULL_REFUND_DAYS = 7;      // 100% 환불
    private static final int PARTIAL_REFUND_DAYS = 3;   // 50% 환불
    private static final BigDecimal PARTIAL_REFUND_RATE = BigDecimal.valueOf(0.5);

    public Money calculateRefundAmount(Money originalAmount) {
        long daysUntilCheckIn = ChronoUnit.DAYS.between(
                refundRequestDate.toLocalDate(),
                checkInDate.toLocalDate()
        );

        if (daysUntilCheckIn >= FULL_REFUND_DAYS) {
            return originalAmount;  // 100% 환불
        } else if (daysUntilCheckIn >= PARTIAL_REFUND_DAYS) {
            return originalAmount.multiply(PARTIAL_REFUND_RATE);  // 50% 환불
        } else {
            throw new IllegalStateException("환불 불가");
        }
    }
}
```

---

## 환불 처리 플로우

### 시퀀스 다이어그램

```
Client   RefundController   RefundService   PaymentRepo   RefundRepo   RefundPolicy   TossRefundClient   Toss API   EventPublisher   Kafka
  │            │                 │               │             │              │                │              │              │           │
  │ POST /refund               │               │             │              │                │              │              │           │
  │───────────>│                 │               │             │              │                │              │              │           │
  │            │ processRefund() │               │             │              │                │              │              │           │
  │            │────────────────>│               │             │              │                │              │              │           │
  │            │                 │ findById()    │             │              │                │              │              │           │
  │            │                 │──────────────>│             │              │                │              │              │           │
  │            │                 │ Payment       │             │              │                │              │              │           │
  │            │                 │<──────────────│             │              │                │              │              │           │
  │            │                 │                             │              │                │              │              │           │
  │            │                 │ validateRefundable()        │              │                │              │              │           │
  │            │                 │─────────┐                  │              │                │              │              │           │
  │            │                 │<────────┘                  │              │                │              │              │           │
  │            │                 │                             │              │                │              │              │           │
  │            │                 │ RefundPolicy.of()           │              │                │              │              │           │
  │            │                 │───────────────────────────────────────────────────────────>│              │              │           │
  │            │                 │                             │              │ calculateRefundAmount()       │              │           │
  │            │                 │                             │              │────────────┐  │              │              │           │
  │            │                 │                             │              │<───────────┘  │              │              │           │
  │            │                 │ refundAmount                │              │<──────────────│              │              │           │
  │            │                 │<───────────────────────────────────────────────────────────│              │              │           │
  │            │                 │                             │              │                │              │              │           │
  │            │                 │ Refund.request()            │              │                │              │              │           │
  │            │                 │─────────┐                  │              │                │              │              │           │
  │            │                 │<────────┘                  │              │                │              │              │           │
  │            │                 │                             │              │                │              │              │           │
  │            │                 │ refund.approve()            │              │                │              │              │           │
  │            │                 │─────────┐                  │              │                │              │              │           │
  │            │                 │<────────┘                  │              │                │              │              │           │
  │            │                 │                             │              │                │              │              │           │
  │            │                 │ save(refund)                │              │                │              │              │           │
  │            │                 │────────────────────────────────────────────>│                │              │              │           │
  │            │                 │ Refund (APPROVED)           │              │                │              │              │           │
  │            │                 │<────────────────────────────────────────────│                │              │              │           │
  │            │                 │                             │              │                │              │              │           │
  │            │                 │ cancelPayment()             │              │                │              │              │           │
  │            │                 │───────────────────────────────────────────────────────────────────────────>│              │           │
  │            │                 │                             │              │                │ POST /cancel │              │           │
  │            │                 │                             │              │                │──────────────>│              │           │
  │            │                 │                             │              │                │ 200 OK       │              │           │
  │            │                 │                             │              │                │<──────────────│              │           │
  │            │                 │ TossRefundResponse          │              │                │<──────────────│              │           │
  │            │                 │<───────────────────────────────────────────────────────────────────────────│              │           │
  │            │                 │                             │              │                │              │              │           │
  │            │                 │ refund.complete()           │              │                │              │              │           │
  │            │                 │─────────┐                  │              │                │              │              │           │
  │            │                 │<────────┘                  │              │                │              │              │           │
  │            │                 │                             │              │                │              │              │           │
  │            │                 │ payment.cancel()            │              │                │              │              │           │
  │            │                 │─────────┐                  │              │                │              │              │           │
  │            │                 │<────────┘                  │              │                │              │              │           │
  │            │                 │                             │              │                │              │              │           │
  │            │                 │ save(payment)               │              │                │              │              │           │
  │            │                 │──────────────>│             │              │                │              │              │           │
  │            │                 │ save(refund)                │              │                │              │              │           │
  │            │                 │────────────────────────────────────────────>│                │              │              │           │
  │            │                 │                             │              │                │              │              │           │
  │            │                 │ publishRefundCompletedEvent()               │                │              │              │           │
  │            │                 │────────────────────────────────────────────────────────────────────────────────────────────>│           │
  │            │                 │                             │              │                │              │              │ Publish  │
  │            │                 │                             │              │                │              │              │─────────>│
  │            │                 │ publishPaymentCancelledEvent()              │                │              │              │           │
  │            │                 │────────────────────────────────────────────────────────────────────────────────────────────>│           │
  │            │                 │                             │              │                │              │              │ Publish  │
  │            │                 │                             │              │                │              │              │─────────>│
  │            │<────────────────│                             │              │                │              │              │           │
  │<───────────│ Refund          │                             │              │                │              │              │           │
  │ 200 OK     │                 │                             │              │                │              │              │           │
```

---

## API 명세

### 환불 요청

**Request**

```http
POST /api/v1/refunds
Content-Type: application/json

{
  "paymentId": "PAY-A1B2C3D4",
  "reason": "고객 요청에 의한 환불"
}
```

**Response (성공 - 100% 환불)**

```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "refundId": "REF-X9Y8Z7W6",
  "paymentId": "PAY-A1B2C3D4",
  "originalAmount": 100000,
  "refundAmount": 100000,
  "refundRate": 100,
  "currency": "KRW",
  "status": "COMPLETED",
  "reason": "고객 요청에 의한 환불",
  "transactionId": "refund-transaction-123",
  "requestedAt": "2025-11-23T11:00:00",
  "approvedAt": "2025-11-23T11:00:01",
  "completedAt": "2025-11-23T11:00:05"
}
```

**Response (성공 - 50% 환불)**

```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "refundId": "REF-X9Y8Z7W6",
  "paymentId": "PAY-A1B2C3D4",
  "originalAmount": 100000,
  "refundAmount": 50000,
  "refundRate": 50,
  "currency": "KRW",
  "status": "COMPLETED",
  "reason": "고객 요청에 의한 환불",
  "transactionId": "refund-transaction-123",
  "requestedAt": "2025-11-23T11:00:00",
  "approvedAt": "2025-11-23T11:00:01",
  "completedAt": "2025-11-23T11:00:05"
}
```

---

## 구현 코드

### RefundService

```java
@Transactional
public Refund processRefund(String paymentId, String reason) {
    log.info("환불 처리 시작 - paymentId: {}, reason: {}", paymentId, reason);

    // 1. Payment 조회 및 환불 가능 여부 확인
    Payment payment = paymentRepository.findById(paymentId)
            .orElseThrow(() -> PaymentException.notFound(paymentId));

    payment.validateRefundable();

    // 2. 환불 정책에 따라 환불 금액 계산
    RefundPolicy policy = RefundPolicy.of(payment.getCheckInDate(), LocalDateTime.now());
    Money refundAmount = policy.calculateRefundAmount(payment.getAmount());

    log.info("환불 금액 계산 완료 - paymentId: {}, originalAmount: {}, refundAmount: {}, refundRate: {}%",
            paymentId, payment.getAmount(), refundAmount, policy.getRefundRate());

    // 3. Refund 엔티티 생성 및 저장
    Refund refund = Refund.request(paymentId, payment.getAmount(), refundAmount, reason);
    refund.approve();
    Refund savedRefund = refundRepository.save(refund);

    try {
        // 4. 토스 환불 API 호출
        TossRefundRequest request = new TossRefundRequest(
                reason,
                refundAmount.getValue().longValue()
        );

        TossRefundResponse response = tossRefundClient.cancelPayment(payment.getPaymentKey(), request);

        // 5. 환불 완료 처리
        refund.complete(response.transactionId());

        // 6. Payment 취소 처리
        payment.cancel();

        paymentRepository.save(payment);
        Refund completedRefund = refundRepository.save(refund);

        log.info("환불 처리 완료 - refundId: {}, transactionId: {}, refundAmount: {}",
                completedRefund.getRefundId(), completedRefund.getTransactionId(), refundAmount);

        // 7. 환불 완료 이벤트 발행
        RefundCompletedEvent refundEvent = RefundCompletedEvent.from(completedRefund);
        paymentEventPublisher.publishRefundCompletedEvent(refundEvent);

        // 8. 결제 취소 이벤트 발행
        PaymentCancelledEvent cancelledEvent = PaymentCancelledEvent.from(payment);
        paymentEventPublisher.publishPaymentCancelledEvent(cancelledEvent);

        return completedRefund;

    } catch (Exception e) {
        log.error("환불 처리 실패 - paymentId: {}, error: {}", paymentId, e.getMessage(), e);
        refund.fail(e.getMessage());
        refundRepository.save(refund);
        throw new RefundException(
                ErrorCode.REFUND_PROCESSING_FAILED,
                "Refund processing failed for payment: " + paymentId,
                e
        );
    }
}
```

---

## 환불 시나리오별 처리

### 시나리오 1: 100% 전액 환불 (7일 이상)

**조건**:

- 현재: 2025-11-23
- 체크인: 2025-12-01 (9일 후)

**처리**:

```java
RefundPolicy policy = RefundPolicy.of(
    LocalDateTime.of(2025, 12, 1, 15, 0),  // checkInDate
    LocalDateTime.of(2025, 11, 23, 10, 0)  // refundRequestDate
);

Money originalAmount = Money.of(100000);
Money refundAmount = policy.calculateRefundAmount(originalAmount);
// refundAmount = 100,000원 (100%)
```

**Toss API 요청**:

```json
{
  "cancelReason": "고객 요청에 의한 환불",
  "cancelAmount": 100000
}
```

---

### 시나리오 2: 50% 부분 환불 (3~7일)

**조건**:

- 현재: 2025-11-23
- 체크인: 2025-11-28 (5일 후)

**처리**:

```java
RefundPolicy policy = RefundPolicy.of(
    LocalDateTime.of(2025, 11, 28, 15, 0),  // checkInDate
    LocalDateTime.of(2025, 11, 23, 10, 0)  // refundRequestDate
);

Money originalAmount = Money.of(100000);
Money refundAmount = policy.calculateRefundAmount(originalAmount);
// refundAmount = 50,000원 (50%)
```

**Toss API 요청**:

```json
{
  "cancelReason": "고객 요청에 의한 환불",
  "cancelAmount": 50000
}
```

---

### 시나리오 3: 환불 불가 (3일 미만)

**조건**:

- 현재: 2025-11-23
- 체크인: 2025-11-25 (2일 후)

**처리**:

```java
RefundPolicy policy = RefundPolicy.of(
    LocalDateTime.of(2025, 11, 25, 15, 0),  // checkInDate
    LocalDateTime.of(2025, 11, 23, 10, 0)  // refundRequestDate
);

Money originalAmount = Money.of(100000);
Money refundAmount = policy.calculateRefundAmount(originalAmount);
// IllegalStateException: 체크인 2일 전에는 환불이 불가능합니다
```

**응답**:

```http
HTTP/1.1 400 Bad Request
Content-Type: application/json

{
  "timestamp": "2025-11-23T10:00:00",
  "status": 400,
  "code": "REFUND_005",
  "message": "체크인 2일 전에는 환불이 불가능합니다. 최소 3일 전에 환불을 요청해야 합니다.",
  "path": "/api/v1/refunds",
  "exceptionType": "REFUND_DOMAIN"
}
```

---

## 예외 처리

### 환불 실패 시나리오

#### 1. 결제를 찾을 수 없음

```json
{
  "timestamp": "2025-11-23T11:00:00",
  "status": 404,
  "code": "PAYMENT_001",
  "message": "Payment not found: PAY-INVALID",
  "path": "/api/v1/refunds",
  "exceptionType": "PAYMENT_DOMAIN"
}
```

#### 2. 완료되지 않은 결제

```json
{
  "timestamp": "2025-11-23T11:00:00",
  "status": 400,
  "code": "PAYMENT_005",
  "message": "완료된 결제만 환불 가능합니다. 현재 상태: PREPARED",
  "path": "/api/v1/refunds",
  "exceptionType": "PAYMENT_DOMAIN"
}
```

#### 3. 체크인 날짜 이후

```json
{
  "timestamp": "2025-12-02T11:00:00",
  "status": 400,
  "code": "REFUND_005",
  "message": "체크인 날짜 이후에는 환불이 불가능합니다",
  "path": "/api/v1/refunds",
  "exceptionType": "PAYMENT_DOMAIN"
}
```

#### 4. 환불 기간 만료 (3일 미만)

```json
{
  "timestamp": "2025-11-23T11:00:00",
  "status": 400,
  "code": "REFUND_005",
  "message": "체크인 2일 전에는 환불이 불가능합니다. 최소 3일 전에 환불을 요청해야 합니다.",
  "path": "/api/v1/refunds",
  "exceptionType": "REFUND_DOMAIN"
}
```

#### 5. Toss API 오류

```json
{
  "timestamp": "2025-11-23T11:00:00",
  "status": 502,
  "code": "REFUND_006",
  "message": "Refund processing failed for payment: PAY-A1B2C3D4",
  "path": "/api/v1/refunds",
  "exceptionType": "REFUND_DOMAIN"
}
```

---

## Refund Entity 상태 관리

### 상태 전이 다이어그램

```
PENDING ──approve()──> APPROVED ──complete()──> COMPLETED
    │                      │
    └──fail()──> FAILED    └──fail()──> FAILED
```

### 상태별 의미

| 상태        | 의미                    | 진입 시점             |
|-----------|-----------------------|-------------------|
| PENDING   | 환불 요청 생성              | Refund.request()  |
| APPROVED  | 환불 승인 (Toss API 호출 전) | refund.approve()  |
| COMPLETED | 환불 완료                 | refund.complete() |
| FAILED    | 환불 실패                 | refund.fail()     |

### 상태별 비즈니스 규칙

**PENDING**:

- Refund 엔티티 생성 직후 상태
- approve() 메서드만 호출 가능

**APPROVED**:

- Toss API 호출 직전 상태
- complete() 또는 fail() 메서드만 호출 가능

**COMPLETED**:

- 최종 상태 (더 이상 변경 불가)
- transactionId 필수

**FAILED**:

- 실패 상태
- failureReason 필수

---

## 이벤트 발행

### RefundCompletedEvent

```json
{
  "refundId": "REF-X9Y8Z7W6",
  "paymentId": "PAY-A1B2C3D4",
  "reservationId": "reservation-123",
  "originalAmount": 100000,
  "refundAmount": 50000,
  "currency": "KRW",
  "transactionId": "refund-transaction-123",
  "completedAt": "2025-11-23T11:00:05"
}
```

### PaymentCancelledEvent

```json
{
  "paymentId": "PAY-A1B2C3D4",
  "reservationId": "reservation-123",
  "cancelledAt": "2025-11-23T11:00:05"
}
```

**Outbox Pattern 적용**:

1. PaymentEvent 테이블에 두 개의 이벤트 저장 (status: PENDING)
	- REFUND_COMPLETED
	- PAYMENT_CANCELLED
2. OutboxEventScheduler가 주기적으로 PENDING 이벤트 조회
3. Kafka로 발행 후 status를 PUBLISHED로 변경

---

## 트랜잭션 관리

### 환불 처리 트랜잭션

**트랜잭션 범위**: RefundService.processRefund()

**포함 작업**:

1. Payment 조회 및 검증
2. RefundPolicy를 통한 환불 금액 계산
3. Refund 엔티티 생성 및 승인
4. Toss API 호출 (외부 시스템)
5. Refund 완료 처리
6. Payment 취소 처리
7. 두 엔티티 저장
8. 두 개의 PaymentEvent 저장 (Outbox)

**롤백 조건**:

- Payment 조회 실패
- 환불 가능 검증 실패
- Toss API 호출 실패
- 엔티티 저장 실패

**참고**: Toss API 호출 실패 시, Refund는 FAILED 상태로 저장되고 Payment는 COMPLETED 상태 유지

---

## 테스트 케이스

### 1. 100% 환불 성공 테스트

```java
@Test
@DisplayName("환불 처리 성공 - 100% 환불 (7일 이상)")
void processRefund_success_fullRefund() {
    // given
    LocalDateTime checkInDate = LocalDateTime.now().plusDays(10);
    Payment payment = Payment.prepare("reservation-123", Money.of(100000L), checkInDate);
    payment.complete("order-123", "payment-key-123", "transaction-123", PaymentMethod.CARD);

    given(paymentRepository.findById(payment.getPaymentId())).willReturn(Optional.of(payment));
    given(refundRepository.save(any(Refund.class))).willAnswer(invocation -> invocation.getArgument(0));
    given(tossRefundClient.cancelPayment(eq(payment.getPaymentKey()), any(TossRefundRequest.class)))
            .willReturn(new TossRefundResponse(...));

    // when
    Refund result = refundService.processRefund(payment.getPaymentId(), "고객 요청");

    // then
    assertThat(result.getStatus()).isEqualTo(RefundStatus.COMPLETED);
    assertThat(result.getRefundAmount()).isEqualTo(Money.of(100000L));
    assertThat(result.getOriginalAmount()).isEqualTo(Money.of(100000L));
}
```

### 2. 50% 환불 성공 테스트

```java
@Test
@DisplayName("환불 처리 성공 - 50% 환불 (3-7일)")
void processRefund_success_partialRefund() {
    // given
    LocalDateTime checkInDate = LocalDateTime.now().plusDays(5);
    Payment payment = Payment.prepare("reservation-456", Money.of(100000L), checkInDate);
    payment.complete("order-456", "payment-key-456", "transaction-456", PaymentMethod.CARD);

    // when
    Refund result = refundService.processRefund(payment.getPaymentId(), "고객 요청");

    // then
    assertThat(result.getRefundAmount().getValue().longValue()).isEqualTo(50000L);
    assertThat(result.getOriginalAmount().getValue().longValue()).isEqualTo(100000L);
    assertThat(result.getStatus()).isEqualTo(RefundStatus.COMPLETED);
}
```

### 3. 환불 불가 테스트

```java
@Test
@DisplayName("환불 처리 실패 - 환불 불가 기간 (3일 미만)")
void processRefund_fail_withinThreeDays() {
    // given
    LocalDateTime checkInDate = LocalDateTime.now().plusDays(2);
    Payment payment = Payment.prepare("reservation-999", Money.of(100000L), checkInDate);
    payment.complete("order-999", "payment-key-999", "transaction-999", PaymentMethod.CARD);

    given(paymentRepository.findById(payment.getPaymentId())).willReturn(Optional.of(payment));

    // when & then
    assertThatThrownBy(() -> refundService.processRefund(payment.getPaymentId(), "고객 요청"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("환불이 불가능합니다");
}
```

---

## 성능 고려사항

### 1. 환불 정책 계산

- RefundPolicy는 Value Object로 계산 비용이 낮음
- 캐싱 불필요

### 2. Toss API 호출

- HTTP 타임아웃 설정 (연결: 3초, 읽기: 10초)
- 재시도 로직 (최대 3회)
- Circuit Breaker 패턴 적용 권장

### 3. 트랜잭션 관리

- 환불 처리는 단일 트랜잭션
- Payment와 Refund 모두 저장 성공 시에만 커밋
- Toss API 실패 시 자동 롤백

---

## 관련 문서

- [Domain Model](../implementation/DOMAIN_MODEL.md)
- [Payment Flow](PAYMENT_FLOW.md)
- [Event Publishing](EVENT_PUBLISHING.md)
- [Exception Handling](../implementation/EXCEPTION_HANDLING.md)
- [ADR-005: Refund Policy Design](../adr/005-refund-policy-design.md)

---

**최종 업데이트**: 2025-11-23
**작성자**: TeamBind
**PR**: #30
