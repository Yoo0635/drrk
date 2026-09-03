package com.drrk.main.consumer.congestion;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

class RabbitCongestionRetryPublisherTest {

	private final RabbitTemplate rabbitTemplate = Mockito.mock(RabbitTemplate.class);
	private final Clock clock = Clock.fixed(Instant.parse("2026-09-03T12:00:00Z"), ZoneOffset.UTC);
	private final RabbitCongestionRetryPublisher publisher = new RabbitCongestionRetryPublisher(
			rabbitTemplate,
			clock,
			Duration.ofSeconds(5)
	);

	@Test
	void publishesPersistentCopyToRetryStageAfterBrokerConfirm() {
		completeConfirm(true, null);
		Message original = message("payload");

		publisher.publish(original, 1, new IllegalStateException("redis unavailable"));

		ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
		verify(rabbitTemplate).send(
				eq(CongestionRetryNames.EXCHANGE),
				eq(CongestionRetryNames.ONE_SECOND_ROUTING_KEY),
				messageCaptor.capture(),
				any(CorrelationData.class)
		);
		Message published = messageCaptor.getValue();
		assertThat(new String(published.getBody(), UTF_8)).isEqualTo("payload");
		assertThat(published.getMessageProperties().getDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
		assertThat((Object) published.getMessageProperties().getHeader(CongestionRetryPublisher.RETRY_COUNT_HEADER))
				.isEqualTo(1);
		assertThat((Object) published.getMessageProperties().getHeader(CongestionRetryPublisher.FIRST_FAILED_AT_HEADER))
				.isEqualTo("2026-09-03T12:00:00Z");
		assertThat((Object) published.getMessageProperties().getHeader(CongestionRetryPublisher.LAST_FAILURE_TYPE_HEADER))
				.isEqualTo(IllegalStateException.class.getName());
	}

	@Test
	void routesSecondAndThirdRetriesToTheirConfiguredStages() {
		completeConfirm(true, null);

		publisher.publish(message("second"), 2, new IllegalStateException("temporary"));
		publisher.publish(message("third"), 3, new IllegalStateException("temporary"));

		verify(rabbitTemplate).send(
				eq(CongestionRetryNames.EXCHANGE),
				eq(CongestionRetryNames.FIVE_SECONDS_ROUTING_KEY),
				any(Message.class),
				any(CorrelationData.class)
		);
		verify(rabbitTemplate).send(
				eq(CongestionRetryNames.EXCHANGE),
				eq(CongestionRetryNames.FIFTEEN_SECONDS_ROUTING_KEY),
				any(Message.class),
				any(CorrelationData.class)
		);
	}

	@Test
	void failsWhenBrokerNegativelyAcknowledgesRetryPublish() {
		completeConfirm(false, "queue unavailable");

		assertThatThrownBy(() -> publisher.publish(
				message("payload"),
				1,
				new IllegalStateException("temporary")
		)).isInstanceOf(AmqpException.class)
				.hasMessageContaining("queue unavailable");
	}

	private void completeConfirm(boolean acknowledged, String reason) {
		doAnswer(invocation -> {
			CorrelationData correlationData = invocation.getArgument(3);
			correlationData.getFuture().complete(new CorrelationData.Confirm(acknowledged, reason));
			return null;
		}).when(rabbitTemplate).send(any(String.class), any(String.class), any(Message.class), any(CorrelationData.class));
	}

	private Message message(String body) {
		MessageProperties properties = new MessageProperties();
		properties.setMessageId("8c530c6c-f819-4ad6-b687-760dc698c617");
		return new Message(body.getBytes(UTF_8), properties);
	}
}
