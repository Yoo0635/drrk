package com.drrk.main.consumer.congestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.drrk.messaging.congestion.CongestionCalculatedMessage;
import com.drrk.messaging.congestion.CongestionInputReferences;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CongestionHistoryIngestionServiceTest {

	private final CongestionHistoryStore store = mock(CongestionHistoryStore.class);
	private final Clock clock = Clock.fixed(Instant.parse("2026-09-04T00:00:00Z"), ZoneOffset.UTC);
	private final CongestionHistoryIngestionService service =
			new CongestionHistoryIngestionService(store, clock, Duration.ofDays(30), 1000);

	@Test
	void insertsTheFirstPayloadWithinRetention() {
		CongestionCalculatedMessage message = calculatedAt(Instant.parse("2026-09-03T23:59:55Z"));
		when(store.findPayload(message.messageId())).thenReturn(Optional.empty());
		when(store.insertIfAbsent(message, "payload", clock.instant())).thenReturn(true);

		CongestionHistoryIngestionResult result = service.ingest(message, "payload");

		assertThat(result).isEqualTo(CongestionHistoryIngestionResult.STORED);
	}

	@Test
	void rejectsSameMessageIdWithDifferentPayload() {
		CongestionCalculatedMessage message = calculatedAt(Instant.parse("2026-09-03T23:59:55Z"));
		when(store.findPayload(message.messageId())).thenReturn(Optional.of("old-payload"));

		assertThatThrownBy(() -> service.ingest(message, "new-payload"))
				.isInstanceOf(InvalidCongestionMessageException.class);
		verify(store, never()).insertIfAbsent(message, "new-payload", clock.instant());
	}

	@Test
	void skipsMessagesOutsideThirtyDayRetention() {
		CongestionCalculatedMessage message = calculatedAt(Instant.parse("2026-08-05T00:00:00Z"));

		CongestionHistoryIngestionResult result = service.ingest(message, "payload");

		assertThat(result).isEqualTo(CongestionHistoryIngestionResult.TOO_OLD);
		verify(store, never()).findPayload(message.messageId());
	}

	private CongestionCalculatedMessage calculatedAt(Instant calculatedAt) {
		return CongestionCalculatedMessage.calculated(
				UUID.randomUUID(),
				calculatedAt,
				"platform-congestion-v2",
				false,
				12.0,
				48L,
				6.0,
				calculatedAt.minusSeconds(300),
				List.of(),
				inputs()
		);
	}

	private CongestionInputReferences inputs() {
		return new CongestionInputReferences(
				Instant.EPOCH,
				0,
				Instant.EPOCH,
				0,
				Instant.EPOCH,
				0,
				"8c530c6c-f819-4ad6-b687-760dc698c617",
				Instant.EPOCH
		);
	}
}
