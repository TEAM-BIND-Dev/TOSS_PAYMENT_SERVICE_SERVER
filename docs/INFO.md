# 토스 페이먼츠 결제 서비스 프로젝트 정보

> v0.0.1-SNAPSHOT | TeamBind | Java 21

---

## 프로젝트 개요

### 비즈니스 목적

공간 예약 플랫폼의 **결제/환불 처리를 담당하는 독립적인 MSA 서비스**입니다.

Toss Payments API와 통합하여 안전하고 신뢰성 있는 결제 프로세스를 제공하며, MSA 환경에서 발생할 수 있는 분산 트랜잭션 문제를 **Outbox Pattern**으로 해결합니다.

### 서비스 책임

**결제 서비스가 하는 것:**
- Toss Payments API를 통한 결제/환불 처리
- 결제 준비 (예약 이벤트 수신 시)
- 클라이언트 금액 변조 방지 (사전 저장 금액과 비교)
- 체크인 날짜 기반 차등 환불율 계산
- 결제/환불 결과 이벤트 발행 (Kafka)

**결제 서비스가 하지 않는 것:**
- 예약 정보 관리 (예약 서비스 책임)
- 상품 정보 관리 (상품 서비스 책임)
- 사용자 인증/인가 (인증 서비스 책임)
- 알림 발송 (알림 서비스 책임)

---

## 기술 스펙

### 핵심 기술

| 분류 | 기술 | 버전 | 선택 이유 |
|------|------|------|----------|
| Language | Java | 21 LTS | Pattern Matching, Records, Sealed Classes, Virtual Threads |
| Framework | Spring Boot | 3.5.7 | 생산성, 풍부한 생태계 |
| Database | MariaDB | 11.x | 안정성, 트랜잭션 지원 |
| Message Queue | Kafka | 3.6 | 이벤트 기반 아키텍처, At-least-once 전달 보장 |
| Payment Gateway | Toss Payments | v1 | 국내 점유율 1위, 개발자 친화적 API |

### Java 21 활용

**Pattern Matching for switch**
```java
return switch ((int) daysUntilCheckIn) {
    case int days when days >= 5 -> BigDecimal.valueOf(1.00);
    case 4 -> BigDecimal.valueOf(0.70);
    default -> BigDecimal.ZERO;
};
```

**Record Classes (DTO)**
```java
public record PaymentConfirmRequest(
    String paymentKey,
    String orderId,
    BigDecimal amount
) {
    public PaymentConfirmRequest {
        if (paymentKey == null) throw new IllegalArgumentException();
    }
}
```

---

## 핵심 비즈니스 규칙

### 환불 정책

**시간 기반 차등 환불율**
- 5일 전 이상: 100% 환불
- 4일 전: 70% 환불
- 3일 전: 50% 환불
- 2일 전: 30% 환불
- 1일 전: 10% 환불
- 당일: 환불 불가

**결제 후 10분 무료 취소**
- 결제 완료 후 10분 이내 취소 시 수수료 면제 (100% 환불)

---

## 문서 구조

- [INDEX.md](INDEX.md) - 문서 인덱스
- [ARCHITECTURE.md](ARCHITECTURE.md) - 아키텍처 상세
- [API_SPECIFICATION.md](API_SPECIFICATION.md) - API 명세
- [adr/](adr/) - 아키텍처 결정 기록 (ADR)

**최종 업데이트**: 2025-11-21