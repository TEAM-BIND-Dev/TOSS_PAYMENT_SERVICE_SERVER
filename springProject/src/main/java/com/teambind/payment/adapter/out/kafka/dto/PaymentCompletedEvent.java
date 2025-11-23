package com.teambind.payment.adapter.out.kafka.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.teambind.payment.domain.Payment;

import java.time.LocalDateTime;

public record PaymentCompletedEvent(
        // 결제 ID
        String paymentId,

        // 예약 ID
        String reservationId,

        // 주문 ID
        String orderId,

        // 결제 키
        String paymentKey,

        // 결제 금액
        Long amount,

        // 결제 수단
        String method,

        // 결제 완료 시각
        @JsonSerialize(using = LocalDateTimeSerializer.class)
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime paidAt
) {
    public static PaymentCompletedEvent from(Payment payment) {
        return new PaymentCompletedEvent(
                payment.getPaymentId(),
                payment.getReservationId(),
                payment.getOrderId(),
                payment.getPaymentKey(),
                payment.getAmount().getValue().longValue(),
                payment.getMethod() != null ? payment.getMethod().name() : null,
                payment.getPaidAt()
        );
    }
}