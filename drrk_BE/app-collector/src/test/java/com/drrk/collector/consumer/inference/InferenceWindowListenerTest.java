package com.drrk.collector.consumer.inference;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.drrk.collector.congestion.LatestCongestionInputStore;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import tools.jackson.databind.ObjectMapper;

class InferenceWindowListenerTest {

	private static final String MESSAGE_ID = "8c530c6c-f819-4ad6-b687-760dc698c617";

	@Test
	void storesLatestModelMeasurementBeforeManualAck() throws IOException {
		LatestCongestionInputStore store = new LatestCongestionInputStore();
		InferenceWindowListener listener = listener(store);
		Channel channel = mock(Channel.class);

		listener.consume(amqpMessage(MESSAGE_ID, validJson(), 7L), channel);

		assertEquals(MESSAGE_ID, store.snapshot().modelMeasurement().messageId());
		verify(channel).basicAck(7L, false);
		verify(channel, never()).basicReject(7L, false);
	}

	@Test
	void rejectsWhenAmqpAndJsonMessageIdsDiffer() throws IOException {
		LatestCongestionInputStore store = new LatestCongestionInputStore();
		InferenceWindowListener listener = listener(store);
		Channel channel = mock(Channel.class);

		listener.consume(amqpMessage("different-id", validJson(), 8L), channel);

		verify(channel).basicReject(8L, false);
		verify(channel, never()).basicAck(8L, false);
	}

	private InferenceWindowListener listener(LatestCongestionInputStore store) {
		return new InferenceWindowListener(new InferenceWindowMessageParser(new ObjectMapper()), store);
	}

	private Message amqpMessage(String amqpMessageId, String payload, long deliveryTag) {
		MessageProperties properties = new MessageProperties();
		properties.setMessageId(amqpMessageId);
		properties.setDeliveryTag(deliveryTag);
		return new Message(payload.getBytes(UTF_8), properties);
	}

	private String validJson() {
		return """
				{
				  "message_id": "8c530c6c-f819-4ad6-b687-760dc698c617",
				  "space_id": "desk01",
				  "ts": 1755000000.0,
				  "window_sec": 10,
				  "events": [{"t":1754999993.2,"dur":3.4,"count":3,"conf":0.81,"snr":24.6}],
				  "n_events": 1,
				  "n_carriers": 3,
				  "intensity": 0.42,
				  "count_est": null
				}
				""";
	}
}
