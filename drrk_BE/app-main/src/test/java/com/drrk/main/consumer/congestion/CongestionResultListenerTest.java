package com.drrk.main.consumer.congestion;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.drrk.messaging.congestion.CongestionCalculatedMessage;
import com.rabbitmq.client.Channel;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import tools.jackson.databind.ObjectMapper;

class CongestionResultListenerTest {

	private static final String MESSAGE_ID = "8c530c6c-f819-4ad6-b687-760dc698c617";
	private static final long DELIVERY_TAG = 42L;

	private final CongestionResultHandler handler = Mockito.mock(CongestionResultHandler.class);
	private final CongestionRetryPublisher retryPublisher = Mockito.mock(CongestionRetryPublisher.class);
	private final CongestionDeliveryPublisher deliveryPublisher = Mockito.mock(CongestionDeliveryPublisher.class);
	private final CongestionConsumerScaler consumerScaler = Mockito.mock(CongestionConsumerScaler.class);
	private final Channel channel = Mockito.mock(Channel.class);
	private SimpleMeterRegistry meterRegistry;
	private CongestionResultListener listener;
	private final Clock clock = Clock.fixed(Instant.parse("2026-08-13T03:00:00Z"), ZoneOffset.UTC);

	@BeforeEach
	void setUp() {
		meterRegistry = new SimpleMeterRegistry();
		Mockito.when(handler.handle(any(), any(), any(), anyInt()))
				.thenAnswer(invocation -> {
					CongestionCalculatedMessage message = invocation.getArgument(0);
					CongestionDeliveryStatus deliveryStatus = invocation.getArgument(2);
					int retryCount = invocation.getArgument(3);
					if (!CongestionCalculatedMessage.hasScore(message.status())) {
						return Optional.empty();
					}
					return Optional.of(new CongestionSnapshot(
							message,
							deliveryStatus,
							retryCount,
							Instant.parse("2026-08-13T03:00:01Z")
					));
				});
		listener = new CongestionResultListener(
				new CongestionCalculatedMessageParser(new ObjectMapper()),
				handler,
				retryPublisher,
				deliveryPublisher,
				new CongestionReliabilityMetrics(meterRegistry),
				consumerScaler,
				clock
		);
	}

	@Test
	void handlesAndAcknowledgesValidMessage() throws Exception {
		listener.consume(message(validJson(), MESSAGE_ID), channel);

		verify(handler).handle(any(), any(), any(), anyInt());
		verify(channel).basicAck(DELIVERY_TAG, false);
		verify(channel, never()).basicReject(DELIVERY_TAG, false);
		verify(retryPublisher, never()).publish(any(), anyInt(), any());
		verify(consumerScaler, never()).retryDeliveryObserved();
	}

	@Test
	void publishesLiveCalculatedResultAfterSuccessfulHandling() throws Exception {
		listener.consume(message(calculatedJson(), MESSAGE_ID), channel);

		verify(deliveryPublisher).publish(
				any(CongestionCalculatedMessage.class),
				eq(CongestionDeliveryStatus.LIVE),
				eq(0)
		);
		verify(channel).basicAck(DELIVERY_TAG, false);
	}

	@Test
	void publishesRecoveredCalculatedResultWithOriginalTimestampAfterRetrySucceeds() throws Exception {
		listener.consume(message(calculatedJson(), MESSAGE_ID, 2), channel);

		var messageCaptor = org.mockito.ArgumentCaptor.forClass(CongestionCalculatedMessage.class);
		verify(deliveryPublisher).publish(
				messageCaptor.capture(),
				eq(CongestionDeliveryStatus.RECOVERED_LATE),
				eq(2)
		);
		org.assertj.core.api.Assertions.assertThat(messageCaptor.getValue().calculatedAt())
				.isEqualTo(Instant.parse("2026-08-13T03:00:00Z"));
		verify(channel).basicAck(DELIVERY_TAG, false);
		org.assertj.core.api.Assertions.assertThat(counter(
				"drrk.congestion.retry.recovered", "retry_count", "2"
		)).isEqualTo(1.0);
		verify(consumerScaler).retryDeliveryObserved();
	}

	@Test
	void rejectsContractErrorWithoutCallingHandler() throws Exception {
		listener.consume(message(validJson(), "468c59d4-3b22-44e1-91ed-67b6290fa4a9"), channel);

		verify(handler, never()).handle(any(), any(), any(), anyInt());
		verify(channel).basicReject(DELIVERY_TAG, false);
		verify(channel, never()).basicAck(DELIVERY_TAG, false);
	}

	@Test
	void rejectsJsonNullAsContractError() throws Exception {
		listener.consume(message("null", MESSAGE_ID), channel);

		verify(handler, never()).handle(any(), any(), any(), anyInt());
		verify(channel).basicReject(DELIVERY_TAG, false);
		verify(channel, never()).basicAck(DELIVERY_TAG, false);
	}

	@Test
	void publishesFirstRedisFailureToOneSecondRetryQueueThenAcknowledges() throws Exception {
		RedisConnectionFailureException failure = new RedisConnectionFailureException("temporary");
		doThrow(failure).when(handler).handle(any(), any(), any(), anyInt());

		listener.consume(message(validJson(), MESSAGE_ID), channel);

		verify(handler).handle(any(), any(), any(), anyInt());
		verify(retryPublisher).publish(any(Message.class), eq(1), eq(failure));
		verify(channel).basicAck(DELIVERY_TAG, false);
		verify(channel, never()).basicReject(DELIVERY_TAG, false);
		org.assertj.core.api.Assertions.assertThat(counter(
				"drrk.congestion.retry.published", "retry_count", "1"
		)).isEqualTo(1.0);
	}

	@Test
	void advancesRedisFailureToNextRetryStage() throws Exception {
		RedisConnectionFailureException failure = new RedisConnectionFailureException("temporary");
		doThrow(failure).when(handler).handle(any(), any(), any(), anyInt());

		listener.consume(message(validJson(), MESSAGE_ID, 1), channel);

		verify(retryPublisher).publish(any(Message.class), eq(2), eq(failure));
		verify(channel).basicAck(DELIVERY_TAG, false);
	}

	@Test
	void publishesRedisTimeoutToRetryQueueThenAcknowledges() throws Exception {
		QueryTimeoutException failure = new QueryTimeoutException("command timeout");
		doThrow(failure).when(handler).handle(any(), any(), any(), anyInt());

		listener.consume(message(validJson(), MESSAGE_ID), channel);

		verify(retryPublisher).publish(any(Message.class), eq(1), eq(failure));
		verify(channel).basicAck(DELIVERY_TAG, false);
		verify(channel, never()).basicReject(DELIVERY_TAG, false);
	}

	@Test
	void rejectsRedisFailureAfterThirdRetry() throws Exception {
		doThrow(new RedisConnectionFailureException("temporary")).when(handler).handle(any(), any(), any(), anyInt());

		listener.consume(message(validJson(), MESSAGE_ID, 3), channel);

		verify(handler).handle(any(), any(), any(), anyInt());
		verify(retryPublisher, never()).publish(any(), anyInt(), any());
		verify(channel).basicReject(DELIVERY_TAG, false);
		verify(channel, never()).basicAck(DELIVERY_TAG, false);
		org.assertj.core.api.Assertions.assertThat(counter(
				"drrk.congestion.dead.lettered", "reason", "retries_exhausted"
		)).isEqualTo(1.0);
	}

	@Test
	void rejectsPermanentHandlerFailureWithoutRetry() throws Exception {
		doThrow(new IllegalStateException("permanent")).when(handler).handle(any(), any(), any(), anyInt());

		listener.consume(message(validJson(), MESSAGE_ID), channel);

		verify(retryPublisher, never()).publish(any(), anyInt(), any());
		verify(channel).basicReject(DELIVERY_TAG, false);
	}

	@Test
	void requeuesOriginalDeliveryWhenRetryPublishFails() throws Exception {
		RedisConnectionFailureException redisFailure = new RedisConnectionFailureException("temporary");
		doThrow(redisFailure).when(handler).handle(any(), any(), any(), anyInt());
		doThrow(new AmqpException("publish failed"))
				.when(retryPublisher).publish(any(), eq(1), eq(redisFailure));

		listener.consume(message(validJson(), MESSAGE_ID), channel);

		verify(channel).basicNack(DELIVERY_TAG, false, true);
		verify(channel, never()).basicAck(DELIVERY_TAG, false);
		verify(channel, never()).basicReject(DELIVERY_TAG, false);
	}

	private Message message(String payload, String messageId) {
		return message(payload, messageId, 0);
	}

	private double counter(String name, String tagKey, String tagValue) {
		return meterRegistry.get(name).tag(tagKey, tagValue).counter().count();
	}

	private Message message(String payload, String messageId, int retryCount) {
		MessageProperties properties = new MessageProperties();
		properties.setMessageId(messageId);
		properties.setDeliveryTag(DELIVERY_TAG);
		if (retryCount > 0) {
			properties.setHeader(CongestionRetryPublisher.RETRY_COUNT_HEADER, retryCount);
		}
		return new Message(payload.getBytes(UTF_8), properties);
	}

	private String validJson() {
		return """
				{
				  "messageId": "8c530c6c-f819-4ad6-b687-760dc698c617",
				  "schemaVersion": "5.0",
				  "calculatedAt": "2026-08-13T03:00:00Z",
				  "calculationVersion": "formula-pending-v1",
				  "status": "FORMULA_PENDING",
				  "sensorDetected": false,
				  "score": null,
				  "level": null,
				  "currentLoad": null,
				  "capacity": null,
				  "forecastLoad": null,
				  "projectedScore": null,
				  "lastTrainDepartureAt": null,
				  "inputs": {
				    "arrivalStatusCollectedAt": "2026-08-13T02:59:00Z",
				    "arrivalStatusItemCount": 2,
				    "passengerForecastCollectedAt": "2026-08-13T02:59:00Z",
				    "passengerForecastItemCount": 1,
				    "railroadOperationCollectedAt": "2026-08-13T02:59:00Z",
				    "railroadOperationItemCount": 3,
				    "modelMessageId": "468c59d4-3b22-44e1-91ed-67b6290fa4a9",
				    "modelMeasuredAt": "2026-08-13T02:59:50Z"
				  },
				  "railroadArrivals": []
				}
				""";
	}

	private String calculatedJson() {
		return """
				{
				  "messageId": "8c530c6c-f819-4ad6-b687-760dc698c617",
				  "schemaVersion": "5.0",
				  "calculatedAt": "2026-08-13T03:00:00Z",
				  "calculationVersion": "platform-congestion-v2",
				  "status": "CALCULATED",
				  "sensorDetected": false,
				  "score": 0.375,
				  "level": "LOW",
				  "currentLoad": 12.0,
				  "capacity": 48,
				  "forecastLoad": 6.0,
				  "projectedScore": 0.375,
				  "lastTrainDepartureAt": "2026-08-13T02:55:00Z",
				  "inputs": {
				    "arrivalStatusCollectedAt": "2026-08-13T02:59:00Z",
				    "arrivalStatusItemCount": 2,
				    "passengerForecastCollectedAt": "2026-08-13T02:59:00Z",
				    "passengerForecastItemCount": 1,
				    "railroadOperationCollectedAt": "2026-08-13T02:59:00Z",
				    "railroadOperationItemCount": 3,
				    "modelMessageId": "468c59d4-3b22-44e1-91ed-67b6290fa4a9",
				    "modelMeasuredAt": "2026-08-13T02:59:50Z"
				  },
				  "railroadArrivals": []
				}
				""";
	}
}
