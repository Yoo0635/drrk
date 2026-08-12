package com.drrk.main.consumer.inference;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.rabbitmq.client.Channel;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class InferenceWindowListener {

	private static final Logger log = LoggerFactory.getLogger(InferenceWindowListener.class);
	private static final int MAX_PROCESSING_ATTEMPTS = 3;

	private final InferenceWindowMessageParser parser;
	private final InferenceWindowIngestionService ingestionService;

	public InferenceWindowListener(
			InferenceWindowMessageParser parser,
			InferenceWindowIngestionService ingestionService
	) {
		this.parser = parser;
		this.ingestionService = ingestionService;
	}

	@RabbitListener(
			queues = InferenceRabbitConfiguration.INFERENCE_QUEUE,
			containerFactory = "inferenceRabbitListenerContainerFactory",
			autoStartup = "${inference.consumer.auto-startup:true}"
	)
	public void consume(Message amqpMessage, Channel channel) throws IOException {
		String amqpMessageId = amqpMessage.getMessageProperties().getMessageId();
		long deliveryTag = amqpMessage.getMessageProperties().getDeliveryTag();
		String payload = new String(amqpMessage.getBody(), UTF_8);

		log.info("[CONSUME RECEIVED] messageId={} deliveryTag={}", amqpMessageId, deliveryTag);

		InferenceWindowMessage message;
		try {
			message = parser.parse(payload);
			validateMessageId(amqpMessageId, message.messageId());
		} catch (InvalidInferenceMessageException exception) {
			log.warn(
					"[CONSUME REJECTED] messageId={} deliveryTag={} reason={}",
					amqpMessageId,
					deliveryTag,
					exception.getMessage()
			);
			channel.basicReject(deliveryTag, false);
			log.warn(
					"[CONSUME DLQ] messageId={} reason=PERMANENT_CONTRACT_ERROR",
					amqpMessageId
			);
			return;
		}

		processWithBoundedRetry(message, payload, deliveryTag, channel);
	}

	private void processWithBoundedRetry(
			InferenceWindowMessage message,
			String payload,
			long deliveryTag,
			Channel channel
	) throws IOException {
		for (int attempt = 1; attempt <= MAX_PROCESSING_ATTEMPTS; attempt++) {
			try {
				InferenceIngestionResult result = ingestionService.ingest(message, payload);
				channel.basicAck(deliveryTag, false);
				logSuccess(message, result, attempt);
				return;
			} catch (RuntimeException exception) {
				if (attempt == MAX_PROCESSING_ATTEMPTS) {
					log.error(
							"[CONSUME FAILED] messageId={} spaceId={} ts={} attempts={} reason={}",
							message.messageId(),
							message.spaceId(),
							message.ts(),
							attempt,
							exception.getMessage(),
							exception
					);
					channel.basicReject(deliveryTag, false);
					log.error(
							"[CONSUME DLQ] messageId={} spaceId={} ts={} reason=PROCESSING_ATTEMPTS_EXHAUSTED",
							message.messageId(),
							message.spaceId(),
							message.ts()
					);
					return;
				}
				log.warn(
						"[CONSUME RETRY] messageId={} attempt={} maxAttempts={} reason={}",
						message.messageId(),
						attempt,
						MAX_PROCESSING_ATTEMPTS,
						exception.getMessage()
				);
			}
		}
	}

	private static void validateMessageId(String amqpMessageId, String payloadMessageId) {
		if (amqpMessageId == null || !amqpMessageId.equals(payloadMessageId)) {
			throw new InvalidInferenceMessageException("AMQP messageId must match JSON message_id");
		}
	}

	private static void logSuccess(
			InferenceWindowMessage message,
			InferenceIngestionResult result,
			int attempts
	) {
		if (result == InferenceIngestionResult.DUPLICATE) {
			log.info(
					"[CONSUME DUPLICATE] messageId={} spaceId={} ts={} attempts={}",
					message.messageId(),
					message.spaceId(),
					message.ts(),
					attempts
			);
			return;
		}
		log.info(
				"[CONSUME SUCCESS] messageId={} spaceId={} ts={} attempts={}",
				message.messageId(),
				message.spaceId(),
				message.ts(),
				attempts
		);
	}
}
