package com.drrk.main.consumer.inference;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class InferenceWindowIngestionServiceTest {

	private static final Instant RECEIVED_AT = Instant.parse("2026-08-13T01:30:00Z");
	private static final Clock CLOCK = Clock.fixed(RECEIVED_AT, ZoneOffset.UTC);
	private static final String RAW_PAYLOAD = "{\"message_id\":\"8c530c6c-f819-4ad6-b687-760dc698c617\"}";
	private static final InferenceWindowMessage MESSAGE = new InferenceWindowMessage(
			"8c530c6c-f819-4ad6-b687-760dc698c617",
			"desk01",
			1755000000.0,
			10,
			List.of(
					new InferenceEvent(1754999993.2, 3.4, 2, 0.81, 24.6),
					new InferenceEvent(1754999997.8, 2.9, 1, 0.93, 21.2)
			),
			2,
			3,
			0.42,
			null
	);

	@Test
	void storesTheOriginalPayloadForTheFirstMessageId() {
		FakeInferenceMessageReceiptStore store = new FakeInferenceMessageReceiptStore(true);
		InferenceWindowIngestionService service = new InferenceWindowIngestionService(store, CLOCK);

		InferenceIngestionResult result = service.ingest(MESSAGE, RAW_PAYLOAD);

		assertThat(result).isEqualTo(InferenceIngestionResult.STORED);
		assertThat(store.savedMessage).isEqualTo(MESSAGE);
		assertThat(store.savedPayload).isEqualTo(RAW_PAYLOAD);
		assertThat(store.receivedAt).isEqualTo(RECEIVED_AT);
	}

	@Test
	void reportsDuplicateWhenMessageIdAlreadyExists() {
		FakeInferenceMessageReceiptStore store = new FakeInferenceMessageReceiptStore(false);
		InferenceWindowIngestionService service = new InferenceWindowIngestionService(store, CLOCK);

		InferenceIngestionResult result = service.ingest(MESSAGE, RAW_PAYLOAD);

		assertThat(result).isEqualTo(InferenceIngestionResult.DUPLICATE);
	}

	private static final class FakeInferenceMessageReceiptStore implements InferenceMessageReceiptStore {

		private final boolean inserted;
		private InferenceWindowMessage savedMessage;
		private String savedPayload;
		private Instant receivedAt;

		private FakeInferenceMessageReceiptStore(boolean inserted) {
			this.inserted = inserted;
		}

		@Override
		public boolean insertIfAbsent(InferenceWindowMessage message, String payload, Instant receivedAt) {
			this.savedMessage = message;
			this.savedPayload = payload;
			this.receivedAt = receivedAt;
			return inserted;
		}
	}
}
