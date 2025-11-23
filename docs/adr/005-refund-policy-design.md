# ADR 005: 예약 서비스 연동 환불 정책

**날짜**: 2025-11-21  
**상태**: 승인됨  
**결정자**: TeamBind

---

## 문제 정의

환불 정책은 "체크인까지 남은 일수"에 따라 환불율이 달라집니다.  
결제 서비스가 체크인 날짜를 저장해야 하는가?

---

## 결정 사항

**결제 서비스는 체크인 날짜를 저장하지 않고, 환불 시 예약 서비스에서 조회합니다.**

---

## 근거

### MSA 책임 분리
- **예약 서비스**: 예약 정보 (체크인 날짜 포함) 관리
- **결제 서비스**: reservationId, amount만 저장

### 환불 플로우

```java
// RefundService.java
public Refund processRefund(RefundRequest request) {
    // 1. Payment 조회
    Payment payment = paymentRepository.findById(request.paymentId());
    
    // 2. 예약 서비스에서 체크인 날짜 조회
    ReservationInfo reservation = reservationClient
        .getReservation(payment.getReservationId());
    
    // 3. 환불 정책 계산
    RefundCalculation calculation = refundPolicyService.calculate(
        payment.getAmount(),
        payment.getPaidAt(),
        reservation.getCheckInDate(),  // 예약 서비스에서 가져옴
        LocalDateTime.now()
    );
    
    // 4. Toss 환불 API 호출
    ...
}
```

---

## 장점

1. **단일 책임 원칙**: 결제 서비스는 결제만 담당
2. **데이터 정합성**: 체크인 날짜 변경 시 예약 서비스만 수정
3. **느슨한 결합**: 결제 서비스가 예약 스키마에 의존하지 않음

---

## 환불 정책

- 5일 전 이상: 100% 환불
- 4일 전: 70% 환불
- 3일 전: 50% 환불
- 2일 전: 30% 환불
- 1일 전: 10% 환불
- 당일: 환불 불가
- **특별 정책**: 결제 후 10분 이내는 무조건 100% 환불

---

**최종 승인**: 2025-11-21
