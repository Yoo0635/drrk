package com.drrk.main.consumer.congestion;

import org.springframework.amqp.core.Message;

interface CongestionRetryPublisher {

	String RETRY_COUNT_HEADER = "x-retry-count";
	String FIRST_FAILED_AT_HEADER = "x-first-failed-at";
	String LAST_FAILURE_TYPE_HEADER = "x-last-failure-type";

	void publish(Message failedMessage, int retryCount, Throwable failure);
}
