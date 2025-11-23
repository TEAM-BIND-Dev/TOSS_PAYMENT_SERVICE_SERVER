package com.teambind.payment.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Refund 도메인 테스트")
class RefundTest {

    @Test
    @DisplayName("환불 요청 - 성공")
    void request_Success() {
        // Given
        String paymentId = "PAY-12345678";
        Money originalAmount = Money.of(50000);
        Money refundAmount = Money.of(45000);
        String reason = "고객 취소 요청";

        // When
        Refund refund = Refund.request(paymentId, originalAmount, refundAmount, reason);

        // Then
        assertThat(refund.getPaymentId()).isEqualTo(paymentId);
        assertThat(refund.getOriginalAmount()).isEqualTo(originalAmount);
        assertThat(refund.getRefundAmount()).isEqualTo(refundAmount);
        assertThat(refund.getReason()).isEqualTo(reason);
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.PENDING);
        assertThat(refund.getRefundId()).isNotNull();
        assertThat(refund.getRequestedAt()).isNotNull();
    }

    @Test
    @DisplayName("환불 요청 - paymentId가 null이면 예외 발생")
    void request_NullPaymentId_ThrowsException() {
        // Given
        String paymentId = null;
        Money originalAmount = Money.of(50000);
        Money refundAmount = Money.of(45000);
        String reason = "고객 취소 요청";

        // When & Then
        assertThatThrownBy(() -> Refund.request(paymentId, originalAmount, refundAmount, reason))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Payment ID는 필수입니다");
    }

    @Test
    @DisplayName("환불 요청 - originalAmount가 null이면 예외 발생")
    void request_NullOriginalAmount_ThrowsException() {
        // Given
        String paymentId = "PAY-12345678";
        Money originalAmount = null;
        Money refundAmount = Money.of(45000);
        String reason = "고객 취소 요청";

        // When & Then
        assertThatThrownBy(() -> Refund.request(paymentId, originalAmount, refundAmount, reason))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Original amount는 필수입니다");
    }

    @Test
    @DisplayName("환불 요청 - refundAmount가 null이면 예외 발생")
    void request_NullRefundAmount_ThrowsException() {
        // Given
        String paymentId = "PAY-12345678";
        Money originalAmount = Money.of(50000);
        Money refundAmount = null;
        String reason = "고객 취소 요청";

        // When & Then
        assertThatThrownBy(() -> Refund.request(paymentId, originalAmount, refundAmount, reason))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Refund amount는 필수입니다");
    }

    @Test
    @DisplayName("환불 요청 - refundAmount가 0이면 예외 발생")
    void request_ZeroRefundAmount_ThrowsException() {
        // Given
        String paymentId = "PAY-12345678";
        Money originalAmount = Money.of(50000);
        Money refundAmount = Money.ZERO;
        String reason = "고객 취소 요청";

        // When & Then
        assertThatThrownBy(() -> Refund.request(paymentId, originalAmount, refundAmount, reason))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Refund amount는 0보다 커야 합니다");
    }

    @Test
    @DisplayName("환불 요청 - refundAmount가 originalAmount보다 크면 예외 발생")
    void request_RefundAmountExceedsOriginal_ThrowsException() {
        // Given
        String paymentId = "PAY-12345678";
        Money originalAmount = Money.of(50000);
        Money refundAmount = Money.of(60000);
        String reason = "고객 취소 요청";

        // When & Then
        assertThatThrownBy(() -> Refund.request(paymentId, originalAmount, refundAmount, reason))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("환불 금액은 원래 금액을 초과할 수 없습니다");
    }

    @Test
    @DisplayName("환불 요청 - reason이 null이면 예외 발생")
    void request_NullReason_ThrowsException() {
        // Given
        String paymentId = "PAY-12345678";
        Money originalAmount = Money.of(50000);
        Money refundAmount = Money.of(45000);
        String reason = null;

        // When & Then
        assertThatThrownBy(() -> Refund.request(paymentId, originalAmount, refundAmount, reason))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("환불 사유는 필수입니다");
    }

    @Test
    @DisplayName("환불 승인 - 성공")
    void approve_Success() {
        // Given
        Refund refund = Refund.request("PAY-12345678", Money.of(50000), Money.of(45000), "고객 취소 요청");

        // When
        refund.approve();

        // Then
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.APPROVED);
        assertThat(refund.getApprovedAt()).isNotNull();
    }

    @Test
    @DisplayName("환불 승인 - PENDING 상태가 아니면 예외 발생")
    void approve_NotPendingStatus_ThrowsException() {
        // Given
        Refund refund = Refund.request("PAY-12345678", Money.of(50000), Money.of(45000), "고객 취소 요청");
        refund.approve();

        // When & Then
        assertThatThrownBy(refund::approve)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PENDING 상태에서만 승인 가능합니다");
    }

    @Test
    @DisplayName("환불 완료 - 성공")
    void complete_Success() {
        // Given
        Refund refund = Refund.request("PAY-12345678", Money.of(50000), Money.of(45000), "고객 취소 요청");
        refund.approve();
        String transactionId = "TXN-001";

        // When
        refund.complete(transactionId);

        // Then
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(refund.getTransactionId()).isEqualTo(transactionId);
        assertThat(refund.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("환불 완료 - APPROVED 상태가 아니면 예외 발생")
    void complete_NotApprovedStatus_ThrowsException() {
        // Given
        Refund refund = Refund.request("PAY-12345678", Money.of(50000), Money.of(45000), "고객 취소 요청");
        String transactionId = "TXN-001";

        // When & Then
        assertThatThrownBy(() -> refund.complete(transactionId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APPROVED 상태에서만 완료 처리 가능합니다");
    }

    @Test
    @DisplayName("환불 완료 - transactionId가 null이면 예외 발생")
    void complete_NullTransactionId_ThrowsException() {
        // Given
        Refund refund = Refund.request("PAY-12345678", Money.of(50000), Money.of(45000), "고객 취소 요청");
        refund.approve();
        String transactionId = null;

        // When & Then
        assertThatThrownBy(() -> refund.complete(transactionId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Transaction ID는 필수입니다");
    }

    @Test
    @DisplayName("환불 실패 처리 - 성공")
    void fail_Success() {
        // Given
        Refund refund = Refund.request("PAY-12345678", Money.of(50000), Money.of(45000), "고객 취소 요청");
        String failureReason = "계좌 정보 오류";

        // When
        refund.fail(failureReason);

        // Then
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.FAILED);
        assertThat(refund.getFailureReason()).isEqualTo(failureReason);
    }

    @Test
    @DisplayName("환불 실패 처리 - 이미 완료된 환불은 실패 처리 불가")
    void fail_CompletedRefund_ThrowsException() {
        // Given
        Refund refund = Refund.request("PAY-12345678", Money.of(50000), Money.of(45000), "고객 취소 요청");
        refund.approve();
        refund.complete("TXN-001");

        // When & Then
        assertThatThrownBy(() -> refund.fail("실패 사유"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("이미 완료된 환불은 실패 처리할 수 없습니다");
    }

    @Test
    @DisplayName("환불 실패 처리 - failureReason이 null이면 예외 발생")
    void fail_NullFailureReason_ThrowsException() {
        // Given
        Refund refund = Refund.request("PAY-12345678", Money.of(50000), Money.of(45000), "고객 취소 요청");
        String failureReason = null;

        // When & Then
        assertThatThrownBy(() -> refund.fail(failureReason))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("실패 사유는 필수입니다");
    }

    @Test
    @DisplayName("환불 금액 검증 - 일치하면 성공")
    void validateRefundAmount_Match_Success() {
        // Given
        Money refundAmount = Money.of(45000);
        Refund refund = Refund.request("PAY-12345678", Money.of(50000), refundAmount, "고객 취소 요청");

        // When & Then
        assertThatNoException().isThrownBy(() -> refund.validateRefundAmount(Money.of(45000)));
    }

    @Test
    @DisplayName("환불 금액 검증 - 불일치하면 예외 발생")
    void validateRefundAmount_Mismatch_ThrowsException() {
        // Given
        Refund refund = Refund.request("PAY-12345678", Money.of(50000), Money.of(45000), "고객 취소 요청");
        Money wrongAmount = Money.of(40000);

        // When & Then
        assertThatThrownBy(() -> refund.validateRefundAmount(wrongAmount))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("환불 금액이 일치하지 않습니다");
    }

    @Test
    @DisplayName("상태 확인 - isCompleted")
    void isCompleted_Success() {
        // Given
        Refund refund = Refund.request("PAY-12345678", Money.of(50000), Money.of(45000), "고객 취소 요청");
        refund.approve();
        refund.complete("TXN-001");

        // When & Then
        assertThat(refund.isCompleted()).isTrue();
        assertThat(refund.isPending()).isFalse();
        assertThat(refund.isApproved()).isFalse();
    }

    @Test
    @DisplayName("상태 확인 - isPending")
    void isPending_Success() {
        // Given
        Refund refund = Refund.request("PAY-12345678", Money.of(50000), Money.of(45000), "고객 취소 요청");

        // When & Then
        assertThat(refund.isPending()).isTrue();
        assertThat(refund.isCompleted()).isFalse();
        assertThat(refund.isApproved()).isFalse();
    }

    @Test
    @DisplayName("상태 확인 - isApproved")
    void isApproved_Success() {
        // Given
        Refund refund = Refund.request("PAY-12345678", Money.of(50000), Money.of(45000), "고객 취소 요청");
        refund.approve();

        // When & Then
        assertThat(refund.isApproved()).isTrue();
        assertThat(refund.isPending()).isFalse();
        assertThat(refund.isCompleted()).isFalse();
    }

    @Test
    @DisplayName("전체 환불 플로우 - 성공")
    void fullRefundFlow_Success() {
        // Given
        String paymentId = "PAY-12345678";
        Money originalAmount = Money.of(50000);
        Money refundAmount = Money.of(45000);
        String reason = "고객 취소 요청";

        // When - 환불 요청
        Refund refund = Refund.request(paymentId, originalAmount, refundAmount, reason);
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.PENDING);

        // When - 환불 승인
        refund.approve();
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.APPROVED);

        // When - 환불 완료
        String transactionId = "TXN-001";
        refund.complete(transactionId);

        // Then
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(refund.getTransactionId()).isEqualTo(transactionId);
        assertThat(refund.isCompleted()).isTrue();
    }
}
