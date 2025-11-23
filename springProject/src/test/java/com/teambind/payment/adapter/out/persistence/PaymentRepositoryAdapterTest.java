package com.teambind.payment.adapter.out.persistence;

import com.teambind.payment.domain.Money;
import com.teambind.payment.domain.Payment;
import com.teambind.payment.domain.PaymentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(PaymentRepositoryAdapter.class)
@DisplayName("PaymentRepositoryAdapter 테스트")
@org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase(replace = org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE)
@org.springframework.test.context.TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class PaymentRepositoryAdapterTest {

    @Autowired
    private PaymentRepositoryAdapter paymentRepository;

    @Test
    @DisplayName("결제 저장 및 조회 - 성공")
    void save_AndFindById_Success() {
        // Given
        Payment payment = Payment.prepare("RSV-001", Money.of(50000), LocalDateTime.now().plusDays(7));

        // When
        Payment savedPayment = paymentRepository.save(payment);
        Optional<Payment> foundPayment = paymentRepository.findById(savedPayment.getPaymentId());

        // Then
        assertThat(foundPayment).isPresent();
        assertThat(foundPayment.get().getPaymentId()).isEqualTo(savedPayment.getPaymentId());
        assertThat(foundPayment.get().getReservationId()).isEqualTo("RSV-001");
        assertThat(foundPayment.get().getAmount()).isEqualTo(Money.of(50000));
        assertThat(foundPayment.get().getStatus()).isEqualTo(PaymentStatus.PREPARED);
    }

    @Test
    @DisplayName("예약 ID로 결제 조회 - 성공")
    void findByReservationId_Success() {
        // Given
        Payment payment = Payment.prepare("RSV-002", Money.of(100000), LocalDateTime.now().plusDays(7));
        paymentRepository.save(payment);

        // When
        Optional<Payment> foundPayment = paymentRepository.findByReservationId("RSV-002");

        // Then
        assertThat(foundPayment).isPresent();
        assertThat(foundPayment.get().getReservationId()).isEqualTo("RSV-002");
        assertThat(foundPayment.get().getAmount()).isEqualTo(Money.of(100000));
    }

    @Test
    @DisplayName("멱등성 키로 결제 조회 - 성공")
    void findByIdempotencyKey_Success() {
        // Given
        Payment payment = Payment.prepare("RSV-003", Money.of(75000), LocalDateTime.now().plusDays(7));
        paymentRepository.save(payment);
        String idempotencyKey = payment.getIdempotencyKey();

        // When
        Optional<Payment> foundPayment = paymentRepository.findByIdempotencyKey(idempotencyKey);

        // Then
        assertThat(foundPayment).isPresent();
        assertThat(foundPayment.get().getIdempotencyKey()).isEqualTo(idempotencyKey);
        assertThat(foundPayment.get().getReservationId()).isEqualTo("RSV-003");
    }

    @Test
    @DisplayName("존재하지 않는 결제 조회 - Empty 반환")
    void findById_NotFound_ReturnsEmpty() {
        // When
        Optional<Payment> foundPayment = paymentRepository.findById("NOT-EXISTS");

        // Then
        assertThat(foundPayment).isEmpty();
    }

    @Test
    @DisplayName("결제 상태 업데이트 - 성공")
    void updatePaymentStatus_Success() {
        // Given
        Payment payment = Payment.prepare("RSV-004", Money.of(50000), LocalDateTime.now().plusDays(7));
        Payment savedPayment = paymentRepository.save(payment);

        // When
        savedPayment.complete("ORDER-001", "KEY-001", "TXN-001", com.teambind.payment.domain.PaymentMethod.CARD);
        Payment updatedPayment = paymentRepository.save(savedPayment);

        // Then
        Optional<Payment> foundPayment = paymentRepository.findById(updatedPayment.getPaymentId());
        assertThat(foundPayment).isPresent();
        assertThat(foundPayment.get().getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(foundPayment.get().getOrderId()).isEqualTo("ORDER-001");
        assertThat(foundPayment.get().getPaymentKey()).isEqualTo("KEY-001");
        assertThat(foundPayment.get().getPaidAt()).isNotNull();
    }
}