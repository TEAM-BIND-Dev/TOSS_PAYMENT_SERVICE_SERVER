package com.teambind.payment.adapter.out.kafka.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.teambind.payment.domain.Payment;

import java.time.LocalDateTime;

public record PaymentCancelledEvent(
		// 결제 ID
		String paymentId,
		
		// 예약 ID
		String reservationId,
		
		// 주문 ID
		String orderId,
		
		// 결제 금액
		Long amount,
		
		// 취소 시각
		@JsonSerialize(using = LocalDateTimeSerializer.class)
		@JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
		LocalDateTime cancelledAt
) {
	public static PaymentCancelledEvent from(Payment payment) {
		return new PaymentCancelledEvent(
				payment.getPaymentId(),
				payment.getReservationId(),
				payment.getOrderId(),
				payment.getAmount().getValue().longValue(),
				payment.getCancelledAt()
		);
	}
}
