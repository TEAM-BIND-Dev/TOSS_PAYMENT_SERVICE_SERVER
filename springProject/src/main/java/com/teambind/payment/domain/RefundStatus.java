package com.teambind.payment.domain;

public enum RefundStatus {
    PENDING,     // 환불 요청 (검증 대기)
    APPROVED,    // 환불 승인 (Toss 환불 요청 전)
    COMPLETED,   // 환불 완료 (Toss 환불 완료)
    FAILED       // 환불 실패
}
