package com.drrk.collector.consumer.inference;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.drrk.collector.congestion.LatestCongestionInputStore;
import com.drrk.collector.congestion.ModelMeasurementSnapshot;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

public class InferenceWindowListener {

	private static final Logger log = LoggerFactory.getLogger(InferenceWindowListener.class);

	private final InferenceWindowMessageParser parser;
	private final LatestCongestionInputStore store;

	public InferenceWindowListener(InferenceWindowMessageParser parser, LatestCongestionInputStore store) {
		this.parser = parser;
		this.store = store;
	}

	@RabbitListener(
			queues = InferenceRabbitNames.QUEUE,
			containerFactory = "inferenceRabbitListenerContainerFactory",
			autoStartup = "${inference.consumer.auto-startup:true}"
	)
	public void consume(Message amqpMessage, Channel channel) throws IOException {
		long deliveryTag = amqpMessage.getMessageProperties().getDeliveryTag();
		String amqpMessageId = amqpMessage.getMessageProperties().getMessageId();
		try {
			ModelMeasurementSnapshot snapshot = parser.parse(new String(amqpMessage.getBody(), UTF_8), amqpMessageId);
			validateMessageId(amqpMessageId, snapshot.messageId());
			boolean replaced = store.replaceModelIfNewer(snapshot);
			channel.basicAck(deliveryTag, false);
			if (replaced) {
				log.info("[MODEL RECEIVED] messageId={} spaceId={} measuredAt={}",
						snapshot.messageId(), snapshot.spaceId(), snapshot.measuredAt());
			} else if (!store.accepts(snapshot.spaceId())) {
				log.warn("[MODEL IGNORED] messageId={} spaceId={} reason=SPACE_ID_MISMATCH expected={} "
								+ "hint=CONGESTION_SENSOR_SPACE_ID 값을 확인하세요",
						snapshot.messageId(), snapshot.spaceId(), store.acceptedSensorSpaceId());
			} else {
				log.info("[MODEL IGNORED] messageId={} spaceId={} measuredAt={} reason=NOT_NEWER",
						snapshot.messageId(), snapshot.spaceId(), snapshot.measuredAt());
			}
		} catch (InvalidInferenceMessageException exception) {
			log.warn("[CONSUME DLQ] source=MODEL messageId={} reason=CONTRACT_ERROR", amqpMessageId);
			channel.basicReject(deliveryTag, false);
		}
	}

	private void validateMessageId(String amqpMessageId, String payloadMessageId) {
		if (amqpMessageId == null || !amqpMessageId.equals(payloadMessageId)) {
			throw new InvalidInferenceMessageException("AMQP messageId must match JSON message_id");
		}
	}
}
