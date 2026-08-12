package com.drrk.collector.publisher.congestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.drrk.messaging.congestion.CongestionCalculatedMessage;
import com.drrk.messaging.congestion.CongestionInputReferences;
import com.drrk.messaging.congestion.CongestionRabbitNames;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import tools.jackson.databind.ObjectMapper;

class RabbitCongestionMessagePublisherTest {

	private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
	private final ObjectMapper objectMapper = mock(ObjectMapper.class);

	@Test
	void publishesPersistentJsonAndWaitsForBrokerAck() {
		CongestionCalculatedMessage message = message();
		when(objectMapper.writeValueAsBytes(message)).thenReturn("{}".getBytes());
		doAnswer(invocation -> {
			CorrelationData correlationData = invocation.getArgument(3);
			correlationData.getFuture().complete(new CorrelationData.Confirm(true, null));
			return null;
		}).when(rabbitTemplate).send(
				eq(CongestionRabbitNames.EXCHANGE),
				eq(CongestionRabbitNames.ROUTING_KEY),
				any(Message.class),
				any(CorrelationData.class)
		);
		RabbitCongestionMessagePublisher publisher = new RabbitCongestionMessagePublisher(
				rabbitTemplate,
				objectMapper,
				Duration.ofSeconds(2)
		);

		publisher.publish(message);

		ArgumentCaptor<Message> sent = ArgumentCaptor.forClass(Message.class);
		verify(rabbitTemplate).send(
				eq(CongestionRabbitNames.EXCHANGE),
				eq(CongestionRabbitNames.ROUTING_KEY),
				sent.capture(),
				any(CorrelationData.class)
		);
		assertThat(sent.getValue().getMessageProperties().getMessageId()).isEqualTo(message.messageId());
		assertThat(sent.getValue().getMessageProperties().getContentType()).isEqualTo("application/json");
		assertThat(sent.getValue().getMessageProperties().getDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
	}

	@Test
	void failsWhenBrokerNacksMessage() {
		CongestionCalculatedMessage message = message();
		when(objectMapper.writeValueAsBytes(message)).thenReturn("{}".getBytes());
		doAnswer(invocation -> {
			CorrelationData correlationData = invocation.getArgument(3);
			correlationData.getFuture().complete(new CorrelationData.Confirm(false, "not-routed"));
			return null;
		}).when(rabbitTemplate).send(any(String.class), any(String.class), any(Message.class), any(CorrelationData.class));
		RabbitCongestionMessagePublisher publisher = new RabbitCongestionMessagePublisher(
				rabbitTemplate,
				objectMapper,
				Duration.ofSeconds(2)
		);

		assertThatThrownBy(() -> publisher.publish(message))
				.isInstanceOf(CongestionPublishException.class);
	}

	private CongestionCalculatedMessage message() {
		Instant now = Instant.parse("2026-08-13T03:00:00Z");
		return CongestionCalculatedMessage.formulaPending(
				UUID.fromString("8c530c6c-f819-4ad6-b687-760dc698c617"),
				now,
				new CongestionInputReferences(now, 1, now, 1, now, 1, "model-1", now)
		);
	}
}
