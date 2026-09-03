package com.drrk.main.consumer.congestion;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

final class RabbitCongestionRetryPublisher implements CongestionRetryPublisher {

	private final RabbitTemplate rabbitTemplate;
	private final Clock clock;
	private final Duration confirmTimeout;

	RabbitCongestionRetryPublisher(RabbitTemplate rabbitTemplate, Clock clock, Duration confirmTimeout) {
		this.rabbitTemplate = rabbitTemplate;
		this.clock = clock;
		this.confirmTimeout = confirmTimeout;
	}

	@Override
	public void publish(Message failedMessage, int retryCount, Throwable failure) {
		Message retryMessage = MessageBuilder.fromMessage(failedMessage)
				.setDeliveryMode(MessageDeliveryMode.PERSISTENT)
				.setHeader(RETRY_COUNT_HEADER, retryCount)
				.setHeaderIfAbsent(FIRST_FAILED_AT_HEADER, clock.instant().toString())
				.setHeader(LAST_FAILURE_TYPE_HEADER, failure.getClass().getName())
				.build();
		CorrelationData correlationData = new CorrelationData(correlationId(failedMessage, retryCount));

		rabbitTemplate.send(
				CongestionRetryNames.EXCHANGE,
				routingKey(retryCount),
				retryMessage,
				correlationData
		);
		awaitConfirm(correlationData);
	}

	private void awaitConfirm(CorrelationData correlationData) {
		try {
			CorrelationData.Confirm confirm = correlationData.getFuture()
					.get(confirmTimeout.toMillis(), TimeUnit.MILLISECONDS);
			if (!confirm.ack()) {
				throw new AmqpException("retry publish was negatively acknowledged: " + confirm.reason());
			}
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new AmqpException("interrupted while waiting for retry publish confirm", exception);
		} catch (ExecutionException | TimeoutException exception) {
			throw new AmqpException("failed while waiting for retry publish confirm", exception);
		}
	}

	private String routingKey(int retryCount) {
		return switch (retryCount) {
			case 1 -> CongestionRetryNames.ONE_SECOND_ROUTING_KEY;
			case 2 -> CongestionRetryNames.FIVE_SECONDS_ROUTING_KEY;
			case 3 -> CongestionRetryNames.FIFTEEN_SECONDS_ROUTING_KEY;
			default -> throw new IllegalArgumentException("retryCount must be between 1 and 3");
		};
	}

	private String correlationId(Message message, int retryCount) {
		return message.getMessageProperties().getMessageId() + ":retry:" + retryCount;
	}
}
