package com.drrk.main.consumer.congestion;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.drrk.messaging.congestion.CongestionCalculatedMessage;
import com.drrk.messaging.congestion.CongestionRabbitNames;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;

public class CongestionResultListener {

	private static final Logger log = LoggerFactory.getLogger(CongestionResultListener.class);
	private static final int MAX_RETRY_COUNT = 3;

	private final CongestionCalculatedMessageParser parser;
	private final CongestionResultHandler handler;
	private final CongestionRetryPublisher retryPublisher;
	private final CongestionDeliveryPublisher deliveryPublisher;
	private final CongestionReliabilityMetrics reliabilityMetrics;

	public CongestionResultListener(
			CongestionCalculatedMessageParser parser,
			CongestionResultHandler handler,
			CongestionRetryPublisher retryPublisher,
			CongestionDeliveryPublisher deliveryPublisher,
			CongestionReliabilityMetrics reliabilityMetrics
	) {
		this.parser = parser;
		this.handler = handler;
		this.retryPublisher = retryPublisher;
		this.deliveryPublisher = deliveryPublisher;
		this.reliabilityMetrics = reliabilityMetrics;
	}

	@RabbitListener(
			queues = CongestionRabbitNames.MAIN_QUEUE,
			containerFactory = "congestionRabbitListenerContainerFactory",
			autoStartup = "${congestion.consumer.auto-startup:true}"
	)
	public void consume(Message amqpMessage, Channel channel) throws IOException {
		long deliveryTag = amqpMessage.getMessageProperties().getDeliveryTag();
		String amqpMessageId = amqpMessage.getMessageProperties().getMessageId();
		CongestionCalculatedMessage message;
		try {
			message = parser.parse(new String(amqpMessage.getBody(), UTF_8));
			validateMessageId(amqpMessageId, message.messageId());
		} catch (InvalidCongestionMessageException exception) {
			reliabilityMetrics.deadLettered("contract_error");
			log.warn("[CONSUME DLQ] messageId={} reason=CONTRACT_ERROR detail={}",
					amqpMessageId, exception.getMessage());
			channel.basicReject(deliveryTag, false);
			return;
		}
		process(message, amqpMessage, deliveryTag, channel);
	}

	private void process(
			CongestionCalculatedMessage message,
			Message amqpMessage,
			long deliveryTag,
			Channel channel
	) throws IOException {
		int completedRetries = retryCount(amqpMessage);
		try {
			handler.handle(message);
			publishDelivery(message, completedRetries);
			if (completedRetries > 0) {
				reliabilityMetrics.recovered(completedRetries);
			}
			channel.basicAck(deliveryTag, false);
		} catch (RuntimeException exception) {
			if (!isTransientRedisFailure(exception)) {
				reliabilityMetrics.deadLettered("permanent_failure");
				log.error("[CONSUME DLQ] messageId={} reason=PERMANENT_PROCESSING_FAILURE failureType={}",
						message.messageId(), exception.getClass().getName());
				channel.basicReject(deliveryTag, false);
				return;
			}

			if (completedRetries >= MAX_RETRY_COUNT) {
				reliabilityMetrics.deadLettered("retries_exhausted");
				log.error("[CONSUME DLQ] messageId={} retryCount={} reason=RETRIES_EXHAUSTED",
						message.messageId(), completedRetries);
				channel.basicReject(deliveryTag, false);
				return;
			}

			int nextRetryCount = completedRetries + 1;
			try {
				retryPublisher.publish(amqpMessage, nextRetryCount, exception);
				reliabilityMetrics.retryPublished(nextRetryCount);
				channel.basicAck(deliveryTag, false);
				log.warn("[CONSUME RETRY] messageId={} retryCount={} failureType={}",
						message.messageId(), nextRetryCount, exception.getClass().getName());
			} catch (RuntimeException publishException) {
				log.error("[CONSUME RETRY PUBLISH FAILED] messageId={} retryCount={} detail={}",
						message.messageId(), nextRetryCount, publishException.getMessage());
				channel.basicNack(deliveryTag, false, true);
			}
		}
	}

	private void publishDelivery(CongestionCalculatedMessage message, int retryCount) {
		if (!CongestionCalculatedMessage.hasScore(message.status())) {
			return;
		}
		CongestionDeliveryStatus status = retryCount == 0
				? CongestionDeliveryStatus.LIVE
				: CongestionDeliveryStatus.RECOVERED_LATE;
		try {
			deliveryPublisher.publish(message, status, retryCount);
		} catch (RuntimeException exception) {
			log.error("[CONGESTION SSE PUBLISH FAILED] messageId={} retryCount={} detail={}",
					message.messageId(), retryCount, exception.getMessage());
		}
	}

	private int retryCount(Message message) {
		Object value = message.getMessageProperties().getHeader(CongestionRetryPublisher.RETRY_COUNT_HEADER);
		return value instanceof Number number ? number.intValue() : 0;
	}

	private boolean isTransientRedisFailure(Throwable failure) {
		Throwable current = failure;
		while (current != null) {
			if (current instanceof RedisConnectionFailureException || current instanceof QueryTimeoutException) {
				return true;
			}
			current = current.getCause();
		}
		return false;
	}

	private void validateMessageId(String amqpMessageId, String payloadMessageId) {
		if (amqpMessageId == null || !amqpMessageId.equals(payloadMessageId)) {
			throw new InvalidCongestionMessageException("AMQP messageId must match JSON messageId");
		}
	}
}
