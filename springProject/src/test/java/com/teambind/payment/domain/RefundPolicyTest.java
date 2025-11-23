package com.teambind.payment.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

class RefundPolicyTest {

    @Test
    @DisplayName("체크인 7일 전 - 100% 환불")
    void calculateRefundAmount_fullRefund() {
        // given
        LocalDateTime checkInDate = LocalDateTime.now().plusDays(10);
        LocalDateTime refundRequestDate = LocalDateTime.now();
        Money originalAmount = Money.of(100000L);

        RefundPolicy policy = RefundPolicy.of(checkInDate, refundRequestDate);

        // when
        Money refundAmount = policy.calculateRefundAmount(originalAmount);

        // then
        assertThat(refundAmount).isEqualTo(originalAmount);
        assertThat(policy.getRefundRate()).isEqualTo(100);
        assertThat(policy.isRefundable()).isTrue();
    }

    @Test
    @DisplayName("체크인 정확히 7일 전 - 100% 환불")
    void calculateRefundAmount_exactly7Days() {
        // given
        LocalDateTime checkInDate = LocalDateTime.now().plusDays(7);
        LocalDateTime refundRequestDate = LocalDateTime.now();
        Money originalAmount = Money.of(100000L);

        RefundPolicy policy = RefundPolicy.of(checkInDate, refundRequestDate);

        // when
        Money refundAmount = policy.calculateRefundAmount(originalAmount);

        // then
        assertThat(refundAmount).isEqualTo(originalAmount);
        assertThat(policy.getRefundRate()).isEqualTo(100);
    }

    @Test
    @DisplayName("체크인 3-7일 전 - 50% 환불")
    void calculateRefundAmount_partialRefund() {
        // given
        LocalDateTime checkInDate = LocalDateTime.now().plusDays(5);
        LocalDateTime refundRequestDate = LocalDateTime.now();
        Money originalAmount = Money.of(100000L);

        RefundPolicy policy = RefundPolicy.of(checkInDate, refundRequestDate);

        // when
        Money refundAmount = policy.calculateRefundAmount(originalAmount);

        // then
        assertThat(refundAmount.getValue()).isEqualByComparingTo(BigDecimal.valueOf(50000));
        assertThat(policy.getRefundRate()).isEqualTo(50);
        assertThat(policy.isRefundable()).isTrue();
    }

    @Test
    @DisplayName("체크인 정확히 3일 전 - 50% 환불")
    void calculateRefundAmount_exactly3Days() {
        // given
        LocalDateTime checkInDate = LocalDateTime.now().plusDays(3);
        LocalDateTime refundRequestDate = LocalDateTime.now();
        Money originalAmount = Money.of(100000L);

        RefundPolicy policy = RefundPolicy.of(checkInDate, refundRequestDate);

        // when
        Money refundAmount = policy.calculateRefundAmount(originalAmount);

        // then
        assertThat(refundAmount.getValue()).isEqualByComparingTo(BigDecimal.valueOf(50000));
        assertThat(policy.getRefundRate()).isEqualTo(50);
    }

    @Test
    @DisplayName("체크인 3일 미만 - 환불 불가")
    void calculateRefundAmount_noRefund() {
        // given
        LocalDateTime checkInDate = LocalDateTime.now().plusDays(2);
        LocalDateTime refundRequestDate = LocalDateTime.now();
        Money originalAmount = Money.of(100000L);

        RefundPolicy policy = RefundPolicy.of(checkInDate, refundRequestDate);

        // when & then
        assertThatThrownBy(() -> policy.calculateRefundAmount(originalAmount))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("환불이 불가능합니다");

        assertThat(policy.getRefundRate()).isEqualTo(0);
        assertThat(policy.isRefundable()).isFalse();
    }

    @Test
    @DisplayName("체크인 당일 - 환불 불가")
    void calculateRefundAmount_checkInToday() {
        // given
        LocalDateTime checkInDate = LocalDateTime.now();
        LocalDateTime refundRequestDate = LocalDateTime.now();
        Money originalAmount = Money.of(100000L);

        RefundPolicy policy = RefundPolicy.of(checkInDate, refundRequestDate);

        // when & then
        assertThatThrownBy(() -> policy.calculateRefundAmount(originalAmount))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("환불이 불가능합니다");
    }

    @Test
    @DisplayName("체크인 날짜가 null인 경우 예외 발생")
    void of_nullCheckInDate() {
        // given
        LocalDateTime refundRequestDate = LocalDateTime.now();

        // when & then
        assertThatThrownBy(() -> RefundPolicy.of(null, refundRequestDate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Check-in date는 필수입니다");
    }

    @Test
    @DisplayName("환불 요청 날짜가 null인 경우 예외 발생")
    void of_nullRefundRequestDate() {
        // given
        LocalDateTime checkInDate = LocalDateTime.now().plusDays(7);

        // when & then
        assertThatThrownBy(() -> RefundPolicy.of(checkInDate, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Refund request date는 필수입니다");
    }

    @Test
    @DisplayName("다양한 금액에 대한 50% 환불 계산")
    void calculateRefundAmount_variousAmounts() {
        // given
        LocalDateTime checkInDate = LocalDateTime.now().plusDays(5);
        LocalDateTime refundRequestDate = LocalDateTime.now();
        RefundPolicy policy = RefundPolicy.of(checkInDate, refundRequestDate);

        // when & then
        assertThat(policy.calculateRefundAmount(Money.of(50000L)).getValue())
                .isEqualByComparingTo(BigDecimal.valueOf(25000));

        assertThat(policy.calculateRefundAmount(Money.of(200000L)).getValue())
                .isEqualByComparingTo(BigDecimal.valueOf(100000));

        assertThat(policy.calculateRefundAmount(Money.of(123456L)).getValue())
                .isEqualByComparingTo(BigDecimal.valueOf(61728));
    }
}