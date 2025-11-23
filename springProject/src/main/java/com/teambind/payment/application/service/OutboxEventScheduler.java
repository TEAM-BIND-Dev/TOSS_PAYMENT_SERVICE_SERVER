package com.teambind.payment.application.service;

import com.teambind.payment.application.port.out.PaymentEventRepository;
import com.teambind.payment.domain.PaymentEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxEventScheduler {

    private final PaymentEventRepository paymentEventRepository;
    private final PaymentEventPublisher paymentEventPublisher;

    private static final int BATCH_SIZE = 100;
    private static final int MAX_RETRY_COUNT = 5;

    @Scheduled(fixedDelay = 5000) // 5초마다 실행
    @SchedulerLock(name = "processPendingEvents", lockAtMostFor = "4s", lockAtLeastFor = "1s")
    @Transactional
    public void processPendingEvents() {
        try {
            List<PaymentEvent> pendingEvents = paymentEventRepository.findPendingEvents(BATCH_SIZE);

            if (!pendingEvents.isEmpty()) {
                log.info("PENDING 이벤트 처리 시작 - count: {}", pendingEvents.size());

                for (PaymentEvent event : pendingEvents) {
                    try {
                        paymentEventPublisher.sendEventToKafka(event);
                    } catch (Exception e) {
                        log.error("이벤트 발행 실패 - eventId: {}, error: {}",
                                event.getEventId(), e.getMessage(), e);
                    }
                }

                log.info("PENDING 이벤트 처리 완료 - count: {}", pendingEvents.size());
            }
        } catch (Exception e) {
            log.error("PENDING 이벤트 처리 중 오류 발생", e);
        }
    }

    @Scheduled(fixedDelay = 30000) // 30초마다 실행
    @SchedulerLock(name = "retryFailedEvents", lockAtMostFor = "29s", lockAtLeastFor = "5s")
    @Transactional
    public void retryFailedEvents() {
        try {
            List<PaymentEvent> failedEvents = paymentEventRepository.findFailedEventsForRetry(
                    MAX_RETRY_COUNT,
                    BATCH_SIZE
            );

            if (!failedEvents.isEmpty()) {
                log.info("FAILED 이벤트 재시도 시작 - count: {}", failedEvents.size());

                for (PaymentEvent event : failedEvents) {
                    try {
                        event.resetForRetry();
                        paymentEventRepository.save(event);
                        paymentEventPublisher.sendEventToKafka(event);
                    } catch (Exception e) {
                        log.error("이벤트 재시도 실패 - eventId: {}, retryCount: {}, error: {}",
                                event.getEventId(), event.getRetryCount(), e.getMessage(), e);
                    }
                }

                log.info("FAILED 이벤트 재시도 완료 - count: {}", failedEvents.size());
            }
        } catch (Exception e) {
            log.error("FAILED 이벤트 재시도 중 오류 발생", e);
        }
    }
}