package com.drrk.main.consumer.congestion;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import tools.jackson.databind.ObjectMapper;

class CongestionResultListenerTest {

	private static final String MESSAGE_ID = "8c530c6c-f819-4ad6-b687-760dc698c617";
	private static final long DELIVERY_TAG = 42L;

	private final CongestionResultHandler handler = Mockito.mock(CongestionResultHandler.class);
	private final Channel channel = Mockito.mock(Channel.class);
	private CongestionResultListener listener;

	@BeforeEach
	void setUp() {
		listener = new CongestionResultListener(new CongestionCalculatedMessageParser(new ObjectMapper()), handler);
	}

	@Test
	void handlesAndAcknowledgesValidMessage() throws Exception {
		listener.consume(message(validJson(), MESSAGE_ID), channel);

		verify(handler).handle(any());
		verify(channel).basicAck(DELIVERY_TAG, false);
		verify(channel, never()).basicReject(DELIVERY_TAG, false);
	}

	@Test
	void rejectsContractErrorWithoutCallingHandler() throws Exception {
		listener.consume(message(validJson(), "468c59d4-3b22-44e1-91ed-67b6290fa4a9"), channel);

		verify(handler, never()).handle(any());
		verify(channel).basicReject(DELIVERY_TAG, false);
		verify(channel, never()).basicAck(DELIVERY_TAG, false);
	}

	@Test
	void rejectsJsonNullAsContractError() throws Exception {
		listener.consume(message("null", MESSAGE_ID), channel);

		verify(handler, never()).handle(any());
		verify(channel).basicReject(DELIVERY_TAG, false);
		verify(channel, never()).basicAck(DELIVERY_TAG, false);
	}

	@Test
	void retriesTransientHandlerFailureUpToThreeTimesThenAcknowledges() throws Exception {
		doThrow(new IllegalStateException("temporary"))
				.doThrow(new IllegalStateException("temporary"))
				.doNothing()
				.when(handler).handle(any());

		listener.consume(message(validJson(), MESSAGE_ID), channel);

		verify(handler, times(3)).handle(any());
		verify(channel).basicAck(DELIVERY_TAG, false);
		verify(channel, never()).basicReject(DELIVERY_TAG, false);
	}

	@Test
	void rejectsAfterThirdHandlerFailure() throws Exception {
		doThrow(new IllegalStateException("temporary")).when(handler).handle(any());

		listener.consume(message(validJson(), MESSAGE_ID), channel);

		verify(handler, times(3)).handle(any());
		verify(channel).basicReject(DELIVERY_TAG, false);
		verify(channel, never()).basicAck(DELIVERY_TAG, false);
	}

	private Message message(String payload, String messageId) {
		MessageProperties properties = new MessageProperties();
		properties.setMessageId(messageId);
		properties.setDeliveryTag(DELIVERY_TAG);
		return new Message(payload.getBytes(UTF_8), properties);
	}

	private String validJson() {
		return """
				{
				  "messageId": "8c530c6c-f819-4ad6-b687-760dc698c617",
				  "schemaVersion": "2.0",
				  "calculatedAt": "2026-08-13T03:00:00Z",
				  "calculationVersion": "formula-pending-v0",
				  "status": "FORMULA_PENDING",
				  "score": null,
				  "level": null,
				  "inputs": {
				    "arrivalStatusCollectedAt": "2026-08-13T02:59:00Z",
				    "arrivalStatusItemCount": 2,
				    "passengerForecastCollectedAt": "2026-08-13T02:59:00Z",
				    "passengerForecastItemCount": 1,
				    "railroadOperationCollectedAt": "2026-08-13T02:59:00Z",
				    "railroadOperationItemCount": 3,
				    "modelMessageId": "468c59d4-3b22-44e1-91ed-67b6290fa4a9",
				    "modelMeasuredAt": "2026-08-13T02:59:50Z"
				  }
				}
				""";
	}
}
