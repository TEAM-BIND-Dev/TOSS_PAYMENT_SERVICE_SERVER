package com.teambind.payment.adapter.in.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teambind.payment.adapter.out.kafka.dto.PaymentCancelledEvent;
import com.teambind.payment.adapter.out.kafka.dto.PaymentCompletedEvent;
import com.teambind.payment.adapter.out.kafka.dto.RefundCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventConsumer {
	
	private final ObjectMapper objectMapper;
	
	@KafkaListener(
			topics = "${kafka.topics.payment-completed}",
			groupId = "${spring.kafka.consumer.group-id}",
			containerFactory = "kafkaListenerContainerFactory"
	)
	public void consumePaymentCompleted(
			@Payload Map<String, Object> message,
			@Header(KafkaHeaders.RECEIVED_KEY) String key,
			@Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
			@Header(KafkaHeaders.OFFSET) Long offset,
			Acknowledgment acknowledgment
	) {
		try {
			log.info("Received PaymentCompletedEvent - topic: {}, offset: {}, key: {}", topic, offset, key);
			
			PaymentCompletedEvent event = objectMapper.convertValue(message, PaymentCompletedEvent.class);
			
			log.info("Processing payment completed - paymentId: {}, reservationId: {}, amount: {}",
					event.paymentId(), event.reservationId(), event.amount());
			
			// TODO: Notify reservation service
			// - Update reservation status to CONFIRMED
			// - Trigger confirmation email
			
			log.info("Payment completed processed successfully - paymentId: {}", event.paymentId());
			
			acknowledgment.acknowledge();
			
		} catch (Exception e) {
			log.error("Failed to process PaymentCompletedEvent - topic: {}, offset: {}, error: {}",
					topic, offset, e.getMessage(), e);
			// Do not ACK on failure - will be reprocessed
		}
	}
	
	@KafkaListener(
			topics = "${kafka.topics.payment-cancelled}",
			groupId = "${spring.kafka.consumer.group-id}",
			containerFactory = "kafkaListenerContainerFactory"
	)
	public void consumePaymentCancelled(
			@Payload Map<String, Object> message,
			@Header(KafkaHeaders.RECEIVED_KEY) String key,
			@Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
			@Header(KafkaHeaders.OFFSET) Long offset,
			Acknowledgment acknowledgment
	) {
		try {
			log.info("Received PaymentCancelledEvent - topic: {}, offset: {}, key: {}", topic, offset, key);
			
			PaymentCancelledEvent event = objectMapper.convertValue(message, PaymentCancelledEvent.class);
			
			log.info("Processing payment cancelled - paymentId: {}, reservationId: {}, amount: {}",
					event.paymentId(), event.reservationId(), event.amount());
			
			// TODO: Notify reservation service
			// - Update reservation status to CANCELLED
			// - Trigger cancellation email
			
			log.info("Payment cancelled processed successfully - paymentId: {}", event.paymentId());
			
			acknowledgment.acknowledge();
			
		} catch (Exception e) {
			log.error("Failed to process PaymentCancelledEvent - topic: {}, offset: {}, error: {}",
					topic, offset, e.getMessage(), e);
			// Do not ACK on failure - will be reprocessed
		}
	}
	
	@KafkaListener(
			topics = "${kafka.topics.refund-completed}",
			groupId = "${spring.kafka.consumer.group-id}",
			containerFactory = "kafkaListenerContainerFactory"
	)
	public void consumeRefundCompleted(
			@Payload Map<String, Object> message,
			@Header(KafkaHeaders.RECEIVED_KEY) String key,
			@Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
			@Header(KafkaHeaders.OFFSET) Long offset,
			Acknowledgment acknowledgment
	) {
		try {
			log.info("Received RefundCompletedEvent - topic: {}, offset: {}, key: {}", topic, offset, key);
			
			RefundCompletedEvent event = objectMapper.convertValue(message, RefundCompletedEvent.class);
			
			log.info("Processing refund completed - refundId: {}, paymentId: {}, refundAmount: {}",
					event.refundId(), event.paymentId(), event.refundAmount());
			
			// TODO: Notify reservation service
			// - Update reservation status to REFUNDED
			// - Trigger refund confirmation email
			
			log.info("Refund completed processed successfully - refundId: {}", event.refundId());
			
			acknowledgment.acknowledge();
			
		} catch (Exception e) {
			log.error("Failed to process RefundCompletedEvent - topic: {}, offset: {}, error: {}",
					topic, offset, e.getMessage(), e);
			// Do not ACK on failure - will be reprocessed
		}
	}
}
