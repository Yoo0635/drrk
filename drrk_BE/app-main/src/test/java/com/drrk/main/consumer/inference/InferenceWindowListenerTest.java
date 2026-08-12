package com.drrk.main.consumer.inference;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rabbitmq.client.Channel;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class InferenceWindowListenerTest {

	private static final String MESSAGE_ID = "8c530c6c-f819-4ad6-b687-760dc698c617";
	private static final long DELIVERY_TAG = 42L;
	private static final String VALID_PAYLOAD = """
			{
			  "message_id": "8c530c6c-f819-4ad6-b687-760dc698c617",
			  "space_id": "desk01",
			  "ts": 1755000000.0,
			  "window_sec": 10,
			  "events": [
			    {"t": 1754999993.2, "dur": 3.4, "count": 2, "conf": 0.81, "snr": 24.6},
			    {"t": 1754999997.8, "dur": 2.9, "count": 1, "conf": 0.93, "snr": 21.2}
			  ],
			  "n_events": 2,
			  "n_carriers": 3,
			  "intensity": 0.42,
			  "count_est": null
			}
			""";

	@Mock
	private InferenceWindowIngestionService ingestionService;

	@Mock
	private Channel channel;

	private InferenceWindowListener listener;

	@BeforeEach
	void setUp() {
		InferenceWindowMessageParser parser = new InferenceWindowMessageParser(new JsonMapper());
		listener = new InferenceWindowListener(parser, ingestionService);
	}

	@Test
	void storesAndAcknowledgesAValidMessage(CapturedOutput output) throws Exception {
		when(ingestionService.ingest(any(InferenceWindowMessage.class), eq(VALID_PAYLOAD)))
				.thenReturn(InferenceIngestionResult.STORED);

		listener.consume(message(VALID_PAYLOAD, MESSAGE_ID), channel);

		verify(channel).basicAck(DELIVERY_TAG, false);
		verify(channel, never()).basicReject(DELIVERY_TAG, false);
		assertThat(output).contains("[CONSUME SUCCESS] messageId=" + MESSAGE_ID);
	}

	@Test
	void acknowledgesAnAlreadyStoredMessage() throws Exception {
		when(ingestionService.ingest(any(InferenceWindowMessage.class), eq(VALID_PAYLOAD)))
				.thenReturn(InferenceIngestionResult.DUPLICATE);

		listener.consume(message(VALID_PAYLOAD, MESSAGE_ID), channel);

		verify(channel).basicAck(DELIVERY_TAG, false);
		verify(channel, never()).basicReject(DELIVERY_TAG, false);
	}

	@Test
	void rejectsAContractViolationWithoutCallingTheStore(CapturedOutput output) throws Exception {
		String renamedField = VALID_PAYLOAD.replace("\"ts\"", "\"timestamp\"");

		listener.consume(message(renamedField, MESSAGE_ID), channel);

		verify(ingestionService, never()).ingest(any(), any());
		verify(channel).basicReject(DELIVERY_TAG, false);
		verify(channel, never()).basicAck(DELIVERY_TAG, false);
		assertThat(output).contains("[CONSUME REJECTED] messageId=" + MESSAGE_ID);
		assertThat(output).contains(
				"[CONSUME DLQ] messageId=" + MESSAGE_ID + " reason=PERMANENT_CONTRACT_ERROR"
		);
	}

	@Test
	void rejectsWhenTheAmqpAndJsonMessageIdsDiffer() throws Exception {
		listener.consume(
				message(VALID_PAYLOAD, "468c59d4-3b22-44e1-91ed-67b6290fa4a9"),
				channel
		);

		verify(ingestionService, never()).ingest(any(), any());
		verify(channel).basicReject(DELIVERY_TAG, false);
	}

	@Test
	void retriesAStorageFailureAtMostThreeTimesThenAcknowledges() throws Exception {
		when(ingestionService.ingest(any(InferenceWindowMessage.class), eq(VALID_PAYLOAD)))
				.thenThrow(new IllegalStateException("database unavailable"))
				.thenThrow(new IllegalStateException("database unavailable"))
				.thenReturn(InferenceIngestionResult.STORED);

		listener.consume(message(VALID_PAYLOAD, MESSAGE_ID), channel);

		verify(ingestionService, times(3))
				.ingest(any(InferenceWindowMessage.class), eq(VALID_PAYLOAD));
		verify(channel).basicAck(DELIVERY_TAG, false);
		verify(channel, never()).basicReject(DELIVERY_TAG, false);
	}

	@Test
	void rejectsAfterTheThirdStorageFailure(CapturedOutput output) throws Exception {
		when(ingestionService.ingest(any(InferenceWindowMessage.class), eq(VALID_PAYLOAD)))
				.thenThrow(new IllegalStateException("database unavailable"));

		listener.consume(message(VALID_PAYLOAD, MESSAGE_ID), channel);

		verify(ingestionService, times(3))
				.ingest(any(InferenceWindowMessage.class), eq(VALID_PAYLOAD));
		verify(channel).basicReject(DELIVERY_TAG, false);
		verify(channel, never()).basicAck(DELIVERY_TAG, false);
		assertThat(output)
				.contains("[CONSUME FAILED] messageId=" + MESSAGE_ID + " spaceId=desk01 ts=1.755E9 attempts=3")
				.contains(
						"[CONSUME DLQ] messageId=" + MESSAGE_ID
								+ " spaceId=desk01 ts=1.755E9 reason=PROCESSING_ATTEMPTS_EXHAUSTED"
				);
	}

	@Test
	void propagatesAcknowledgementIoFailureSoTheBrokerCanRedeliver() throws Exception {
		when(ingestionService.ingest(any(InferenceWindowMessage.class), eq(VALID_PAYLOAD)))
				.thenReturn(InferenceIngestionResult.STORED);
		doThrow(new IOException("channel closed"))
				.when(channel).basicAck(DELIVERY_TAG, false);

		assertThatThrownBy(
				() -> listener.consume(message(VALID_PAYLOAD, MESSAGE_ID), channel)
		).isInstanceOf(IOException.class);
	}

	private static Message message(String payload, String amqpMessageId) {
		MessageProperties properties = new MessageProperties();
		properties.setMessageId(amqpMessageId);
		properties.setDeliveryTag(DELIVERY_TAG);
		return new Message(payload.getBytes(UTF_8), properties);
	}
}
