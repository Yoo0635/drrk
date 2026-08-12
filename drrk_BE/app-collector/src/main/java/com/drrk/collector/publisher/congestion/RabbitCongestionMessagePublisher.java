package com.drrk.collector.publisher.congestion;

import com.drrk.messaging.congestion.CongestionCalculatedMessage;
import com.drrk.messaging.congestion.CongestionRabbitNames;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

public class RabbitCongestionMessagePublisher implements CongestionMessagePublisher {

	private final RabbitTemplate rabbitTemplate;
	private final ObjectMapper objectMapper;
	private final Duration confirmTimeout;

	public RabbitCongestionMessagePublisher(
			RabbitTemplate rabbitTemplate,
			ObjectMapper objectMapper,
			Duration confirmTimeout
	) {
		this.rabbitTemplate = rabbitTemplate;
		this.objectMapper = objectMapper;
		this.confirmTimeout = confirmTimeout;
	}

	@Override
	public void publish(CongestionCalculatedMessage payload) {
		Message message = toAmqpMessage(payload);
		CorrelationData correlationData = new CorrelationData(payload.messageId());
		rabbitTemplate.send(
				CongestionRabbitNames.EXCHANGE,
				CongestionRabbitNames.ROUTING_KEY,
				message,
				correlationData
		);
		awaitBrokerConfirmation(correlationData);
	}

	private Message toAmqpMessage(CongestionCalculatedMessage payload) {
		try {
			return MessageBuilder.withBody(objectMapper.writeValueAsBytes(payload))
					.setContentType("application/json")
					.setDeliveryMode(MessageDeliveryMode.PERSISTENT)
					.setMessageId(payload.messageId())
					.build();
		} catch (JacksonException exception) {
			throw new CongestionPublishException("Failed to serialize congestion message", exception);
		}
	}

	private void awaitBrokerConfirmation(CorrelationData correlationData) {
		try {
			CorrelationData.Confirm confirm = correlationData.getFuture()
					.get(confirmTimeout.toMillis(), TimeUnit.MILLISECONDS);
			if (!confirm.ack()) {
				throw new CongestionPublishException("Broker rejected congestion message");
			}
			ReturnedMessage returned = correlationData.getReturned();
			if (returned != null) {
				throw new CongestionPublishException("Congestion message was not routed");
			}
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new CongestionPublishException("Interrupted while awaiting broker confirmation", exception);
		} catch (ExecutionException | TimeoutException exception) {
			throw new CongestionPublishException("Failed to confirm congestion message", exception);
		}
	}
}
