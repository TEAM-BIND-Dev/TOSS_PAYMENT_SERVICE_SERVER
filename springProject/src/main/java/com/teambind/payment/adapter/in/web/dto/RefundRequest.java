package com.teambind.payment.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;

public record RefundRequest(
        // 결제 ID
        @NotBlank(message = "결제 ID는 필수입니다")
        String paymentId,

        // 환불 사유
        @NotBlank(message = "환불 사유는 필수입니다")
        String reason
) {
}