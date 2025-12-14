# ADR-006: Exception Handling Standardization

## Status

**Accepted** - 2025-11-23

## Context

초기 구현에서는 일반적인 Java 예외(`IllegalArgumentException`, `RuntimeException` 등)를 사용했습니다. 이는 다음과 같은 문제를 야기했습니다:

1. **에러 분류 어려움**: 예외 타입만으로는 도메인 에러인지, 검증 에러인지, 외부 API 에러인지 구분하기 어려움
2. **불일관한 에러 응답**: HTTP 상태 코드와 에러 메시지가 일관성 없이 처리됨
3. **디버깅의 어려움**: 예외 발생 지점과 원인을 추적하기 어려움
4. **에러 코드 부재**: 클라이언트에서 에러를 식별하고 처리할 수 있는 코드가 없음
5. **테스트 복잡성**: 일반 예외로 인해 테스트 검증이 불명확함

### 기존 코드 예시

```java
// PaymentConfirmService.java (Before)
Payment payment = paymentRepository.findById(paymentId)
    .orElseThrow(() -> new IllegalArgumentException("결제 정보를 찾을 수 없습니다: " + paymentId));
```

```java
// RefundService.java (Before)
} catch (Exception e) {
    throw new RuntimeException("환불 처리 중 오류가 발생했습니다: " + e.getMessage(), e);
}
```

## Decision

공통 모듈의 예외 처리 인프라를 활용하여 도메인별 예외 계층을 구축하기로 결정했습니다.

### 핵심 결정 사항

#### 1. CustomException 기반 예외 계층 구축

모든 도메인 예외는 `CustomException` 추상 클래스를 상속합니다:

```java
public abstract class CustomException extends RuntimeException {
    private final ErrorCode errorCode;
    private final HttpStatus httpStatus;

    public abstract String getExceptionType();
}
```

#### 2. 도메인별 예외 클래스 생성

각 도메인/레이어에 특화된 예외 클래스를 생성합니다:

- **PaymentException**: 결제 도메인 로직 예외 (exceptionType: "PAYMENT_DOMAIN")
- **RefundException**: 환불 도메인 로직 예외 (exceptionType: "REFUND_DOMAIN")
- **TossApiException**: Toss API 호출 예외 (exceptionType: "EXTERNAL_API")

#### 3. ErrorCode Enum으로 중앙 관리

모든 에러 코드는 `ErrorCode` enum에서 관리합니다:

```java
public enum ErrorCode {
    // 결제 관련 (PAYMENT_0XX)
    PAYMENT_NOT_FOUND("PAYMENT_001", "Payment not found", HttpStatus.NOT_FOUND),
    PAYMENT_ALREADY_COMPLETED("PAYMENT_002", "Payment already completed", HttpStatus.CONFLICT),

    // 환불 관련 (REFUND_0XX)
    REFUND_NOT_FOUND("REFUND_001", "Refund not found", HttpStatus.NOT_FOUND),
    REFUND_NOT_ALLOWED("REFUND_002", "Refund not allowed", HttpStatus.BAD_REQUEST),

    // 외부 API (TOSS_0XX)
    TOSS_API_ERROR("TOSS_001", "Toss API error", HttpStatus.BAD_GATEWAY),
    TOSS_API_TIMEOUT("TOSS_002", "Toss API timeout", HttpStatus.GATEWAY_TIMEOUT);
}
```

#### 4. 팩토리 메서드 패턴 적용

각 예외 클래스에 정적 팩토리 메서드를 제공하여 사용 편의성을 높입니다:

```java
public class PaymentException extends CustomException {
    public static PaymentException notFound(String paymentId) {
        return new PaymentException(
            ErrorCode.PAYMENT_NOT_FOUND,
            "Payment not found: " + paymentId
        );
    }

    public static PaymentException amountMismatch(Long expected, Long actual) {
        return new PaymentException(
            ErrorCode.PAYMENT_AMOUNT_MISMATCH,
            String.format("Payment amount mismatch - expected: %d, actual: %d", expected, actual)
        );
    }
}
```

#### 5. 표준화된 에러 응답 구조

`ErrorResponse` DTO를 통해 일관된 응답 구조를 제공합니다:

```json
{
    "timestamp": "2025-11-23T10:30:00",
    "status": 404,
    "code": "PAYMENT_001",
    "message": "Payment not found: PAY-ABC12345",
    "path": "/api/v1/payments/PAY-ABC12345",
    "exceptionType": "PAYMENT_DOMAIN",
    "errors": []
}
```

#### 6. GlobalExceptionHandler로 중앙 처리

`@RestControllerAdvice`를 사용한 중앙 집중식 예외 처리:

```java
@ExceptionHandler(CustomException.class)
public ResponseEntity<ErrorResponse> handleCustomException(
    CustomException ex, HttpServletRequest request) {

    ErrorResponse errorResponse = ErrorResponse.of(ex, request.getRequestURI());
    return ResponseEntity.status(ex.getHttpStatus()).body(errorResponse);
}
```

### 마이그레이션 전략

#### Phase 1: ErrorCode 확장

- 결제 도메인 에러 코드 추가 (PAYMENT_001~007)
- 환불 도메인 에러 코드 추가 (REFUND_001~006)
- Toss API 에러 코드 추가 (TOSS_001~003)

#### Phase 2: 도메인 예외 클래스 생성

- PaymentException with factory methods
- RefundException with factory methods
- TossApiException with factory methods

#### Phase 3: 서비스 레이어 마이그레이션

- PaymentConfirmService: IllegalArgumentException → PaymentException
- RefundService: IllegalArgumentException, RuntimeException → PaymentException, RefundException
- PaymentCancelService: RuntimeException → TossApiException

#### Phase 4: 테스트 코드 업데이트

- 예외 타입 검증 변경
- 메시지 검증 변경 (한글 → 영문)

## Consequences

### Positive

1. **에러 분류 명확화**
	- exceptionType을 통해 도메인/레이어별 에러 구분 가능
	- 모니터링 및 로깅 개선

2. **일관된 에러 응답**
	- 모든 API 엔드포인트에서 동일한 에러 응답 구조
	- HTTP 상태 코드와 ErrorCode의 자동 매핑

3. **개발 생산성 향상**
	- 팩토리 메서드로 예외 생성 간소화
	- IDE 자동완성으로 사용 가능한 에러 쉽게 파악

4. **테스트 명확성**
   ```java
   // Before
   assertThatThrownBy(() -> service.confirm(...))
       .isInstanceOf(IllegalArgumentException.class)
       .hasMessageContaining("결제 정보를 찾을 수 없습니다");

   // After
   assertThatThrownBy(() -> service.confirm(...))
       .isInstanceOf(PaymentException.class)
       .hasMessageContaining("Payment not found");
   ```

5. **클라이언트 에러 처리 개선**
	- 에러 코드 기반 처리 가능
	- i18n 대응 가능 (코드로 메시지 매핑)

6. **디버깅 용이성**
	- 스택 트레이스에서 명확한 예외 타입
	- 원인 예외(cause) 추적 가능

### Negative

1. **초기 구현 비용**
	- ErrorCode 확장
	- 예외 클래스 생성
	- 기존 코드 마이그레이션
	- 테스트 코드 수정

2. **예외 클래스 증가**
	- 도메인/레이어별 예외 클래스 필요
	- 유지보수 포인트 증가

3. **학습 곡선**
	- 신규 개발자가 예외 체계 이해 필요
	- 팩토리 메서드 사용법 학습

### Mitigation

1. **명확한 문서화**
	- [EXCEPTION_HANDLING.md](../implementation/EXCEPTION_HANDLING.md) 작성
	- 팩토리 메서드 사용 예시 제공
	- 에러 코드 네이밍 규칙 정립

2. **점진적 마이그레이션**
	- 신규 기능부터 적용
	- 기존 코드는 우선순위에 따라 마이그레이션

3. **코드 리뷰 체크리스트**
	- 일반 예외 사용 금지
	- 팩토리 메서드 사용 권장
	- 원인 예외 포함 확인

## Implementation

### Before & After 비교

#### PaymentConfirmService

**Before**:

```java
Payment payment = paymentRepository.findById(paymentId)
    .orElseThrow(() -> new IllegalArgumentException(
        "결제 정보를 찾을 수 없습니다: " + paymentId
    ));
```

**After**:

```java
Payment payment = paymentRepository.findById(paymentId)
    .orElseThrow(() -> PaymentException.notFound(paymentId));
```

#### RefundService

**Before**:

```java
} catch (Exception e) {
    log.error("환불 처리 실패", e);
    refund.fail(e.getMessage());
    refundRepository.save(refund);
    throw new RuntimeException("환불 처리 중 오류가 발생했습니다: " + e.getMessage(), e);
}
```

**After**:

```java
} catch (Exception e) {
    log.error("환불 처리 실패 - paymentId: {}, error: {}",
        paymentId, e.getMessage(), e);
    refund.fail(e.getMessage());
    refundRepository.save(refund);
    throw new RefundException(
        ErrorCode.REFUND_PROCESSING_FAILED,
        "Refund processing failed for payment: " + paymentId,
        e
    );
}
```

### 에러 응답 예시

**Before** (IllegalArgumentException):

```http
HTTP/1.1 500 Internal Server Error
Content-Type: application/json

{
  "timestamp": "2025-11-23T10:30:00",
  "status": 500,
  "error": "Internal Server Error",
  "message": "결제 정보를 찾을 수 없습니다: PAY-ABC12345",
  "path": "/api/v1/payments/confirm"
}
```

**After** (PaymentException):

```http
HTTP/1.1 404 Not Found
Content-Type: application/json

{
  "timestamp": "2025-11-23T10:30:00",
  "status": 404,
  "code": "PAYMENT_001",
  "message": "Payment not found: PAY-ABC12345",
  "path": "/api/v1/payments/confirm",
  "exceptionType": "PAYMENT_DOMAIN"
}
```

## Alternatives Considered

### Alternative 1: Spring의 기본 예외만 사용

**장점**:

- 추가 구현 불필요
- Spring 표준 따름

**단점**:

- 도메인 에러 표현 어려움
- 에러 코드 없음
- HTTP 상태 코드 제어 어려움

**결론**: 비즈니스 도메인 표현에 부족하여 기각

### Alternative 2: ResponseEntity만으로 에러 처리

**장점**:

- 예외 없이 응답 객체로 처리
- 명시적 제어

**단점**:

- 코드 복잡도 증가
- 트랜잭션 롤백 처리 어려움
- 예외의 장점 상실

**결론**: Spring의 선언적 트랜잭션 활용을 위해 기각

### Alternative 3: 예외별로 별도 ErrorCode 없이 처리

**장점**:

- 구현 간소화

**단점**:

- 클라이언트에서 에러 식별 어려움
- i18n 대응 불가
- 에러 분석 어려움

**결론**: 확장성과 유지보수성을 위해 기각

## Related

- [ADR-001: Java 21 Adoption](001-java-21-adoption.md) - Modern exception handling features
- [ADR-002: Event-Driven Payment Preparation](002-event-driven-payment-preparation.md) - Event handling exceptions
- [Implementation: Exception Handling](../implementation/EXCEPTION_HANDLING.md)
- [PR #33: Exception handling standardization](https://github.com/DDINGJOO/TOSS_PAYMENT_SERVICE_SERVER/pull/33)

## References

- [Spring Boot Exception Handling Best Practices](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-exceptionhandler.html)
- [RFC 7807: Problem Details for HTTP APIs](https://datatracker.ietf.org/doc/html/rfc7807)
- Domain-Driven Design by Eric Evans (Exception handling in domain layer)

---

**작성자**: TeamBind
**작성일**: 2025-11-23
**PR**: #33
