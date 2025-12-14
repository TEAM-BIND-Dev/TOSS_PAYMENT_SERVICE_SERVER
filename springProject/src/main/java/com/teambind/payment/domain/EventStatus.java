package com.teambind.payment.domain;

public enum EventStatus {
	PENDING,     // 발행 대기 (DB 저장됨)
	PUBLISHED,   // Kafka 발행 완료
	FAILED       // 발행 실패
}
