package com.drrk.main.consumer.congestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.drrk.messaging.congestion.CongestionCalculatedMessage;
import com.drrk.messaging.congestion.CongestionInputReferences;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class PersistentCongestionResultHandlerTest {

	private final CongestionHistoryIngestionService historyIngestionService =
			mock(CongestionHistoryIngestionService.class);
	private final LatestAirportGuideStore latestStore = mock(LatestAirportGuideStore.class);
	private final PersistentCongestionResultHandler handler =
			new PersistentCongestionResultHandler(historyIngestionService, latestStore);

	@Test
	void updatesCacheOnlyAfterHistoryIsIngested() {
		CongestionCalculatedMessage message = calculatedAt(Instant.parse("2026-08-13T05:01:00Z"));
		CongestionSnapshot snapshot = new CongestionSnapshot(
				message,
				CongestionDeliveryStatus.LIVE,
				0,
				Instant.parse("2026-08-13T05:01:01Z")
		);
		when(historyIngestionService.ingest(message, "payload"))
				.thenReturn(CongestionHistoryIngestionResult.STORED);
		when(latestStore.handle(message, CongestionDeliveryStatus.LIVE, 0))
				.thenReturn(Optional.of(snapshot));

		Optional<CongestionSnapshot> result =
				handler.handle(message, "payload", CongestionDeliveryStatus.LIVE, 0);

		assertThat(result).contains(snapshot);
		InOrder order = inOrder(historyIngestionService, latestStore);
		order.verify(historyIngestionService).ingest(message, "payload");
		order.verify(latestStore).handle(message, CongestionDeliveryStatus.LIVE, 0);
	}

	@Test
	void skipsCacheWhenHistoryIsOutsideRetention() {
		CongestionCalculatedMessage message = calculatedAt(Instant.parse("2026-08-13T05:01:00Z"));
		when(historyIngestionService.ingest(message, "payload"))
				.thenReturn(CongestionHistoryIngestionResult.TOO_OLD);

		Optional<CongestionSnapshot> result =
				handler.handle(message, "payload", CongestionDeliveryStatus.LIVE, 0);

		assertThat(result).isEmpty();
		verifyNoInteractions(latestStore);
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
