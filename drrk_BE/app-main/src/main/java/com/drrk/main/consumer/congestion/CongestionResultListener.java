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

public class CongestionResultListener {

	private static final Logger log = LoggerFactory.getLogger(CongestionResultListener.class);
	private static final int MAX_PROCESSING_ATTEMPTS = 3;

	private final CongestionCalculatedMessageParser parser;
	private final CongestionResultHandler handler;

	public CongestionResultListener(
			CongestionCalculatedMessageParser parser,
			CongestionResultHandler handler
	) {
		this.parser = parser;
		this.handler = handler;
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
			log.warn("[CONSUME DLQ] messageId={} reason=CONTRACT_ERROR detail={}",
					amqpMessageId, exception.getMessage());
			channel.basicReject(deliveryTag, false);
			return;
		}
		processWithBoundedRetry(message, deliveryTag, channel);
	}

	private void processWithBoundedRetry(
			CongestionCalculatedMessage message,
			long deliveryTag,
			Channel channel
	) throws IOException {
		for (int attempt = 1; attempt <= MAX_PROCESSING_ATTEMPTS; attempt++) {
			try {
				handler.handle(message);
				channel.basicAck(deliveryTag, false);
				return;
			} catch (RuntimeException exception) {
				if (attempt == MAX_PROCESSING_ATTEMPTS) {
					log.error("[CONSUME DLQ] messageId={} attempts={} reason=PROCESSING_ATTEMPTS_EXHAUSTED",
							message.messageId(), attempt);
					channel.basicReject(deliveryTag, false);
					return;
				}
				log.warn("[CONSUME RETRY] messageId={} attempt={} maxAttempts={}",
						message.messageId(), attempt, MAX_PROCESSING_ATTEMPTS);
			}
		}
	}

	private void validateMessageId(String amqpMessageId, String payloadMessageId) {
		if (amqpMessageId == null || !amqpMessageId.equals(payloadMessageId)) {
			throw new InvalidCongestionMessageException("AMQP messageId must match JSON messageId");
		}
	}
}
