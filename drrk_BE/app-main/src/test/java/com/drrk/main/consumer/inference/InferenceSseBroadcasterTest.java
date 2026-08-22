package com.drrk.main.consumer.inference;

import static org.assertj.core.api.Assertions.assertThat;

import com.drrk.main.consumer.congestion.LatestAirportGuideStore;
import com.drrk.messaging.congestion.CongestionCalculatedMessage;
import com.drrk.messaging.congestion.CongestionInputReferences;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class InferenceSseBroadcasterTest {

	private final LatestInferenceSnapshotStore store = new LatestInferenceSnapshotStore();
	private final LatestAirportGuideStore airportGuideStore = new LatestAirportGuideStore();
	private final Clock clock = Clock.fixed(Instant.parse("2026-08-13T05:00:14Z"), ZoneOffset.UTC);
	private final InferenceSseBroadcaster broadcaster = new InferenceSseBroadcaster(
			store,
			airportGuideStore,
			clock,
			Duration.ofSeconds(5),
			Duration.ofSeconds(25),
			Duration.ofMinutes(30)
	);

	@Test
	void sendsLatestSnapshotsImmediatelyWhenClientSubscribes() {
		store.updateIfLatest(snapshot("8c530c6c-f819-4ad6-b687-760dc698c617", "desk01", "2026-08-13T05:00:10Z", 3));
		store.updateIfLatest(snapshot("9d82ae8a-0a67-4540-b519-528386835f80", "desk02", "2026-08-13T05:00:12Z", 1));
		airportGuideStore.handle(calculatedCongestion("2026-08-13T05:00:13Z"));
		CapturingEmitter emitter = new CapturingEmitter();

		broadcaster.subscribe(emitter);

		assertThat(emitter.events()).containsExactly(
				"event:carrier-count\nid:8c530c6c-f819-4ad6-b687-760dc698c617\ndata:{\"n_carriers\":3,\"score\":0.375,\"level\":\"LOW\"}\n\n",
				"event:carrier-count\nid:9d82ae8a-0a67-4540-b519-528386835f80\ndata:{\"n_carriers\":1,\"score\":0.375,\"level\":\"LOW\"}\n\n"
		);
	}

	@Test
	void broadcastsAllLatestSnapshotsToAllSubscribersOnTick() {
		CapturingEmitter first = new CapturingEmitter();
		CapturingEmitter second = new CapturingEmitter();
		broadcaster.subscribe(first);
		broadcaster.subscribe(second);
		first.clear();
		second.clear();
		store.updateIfLatest(snapshot("8c530c6c-f819-4ad6-b687-760dc698c617", "desk01", "2026-08-13T05:00:10Z", 3));
		airportGuideStore.handle(calculatedCongestion("2026-08-13T05:00:13Z"));

		broadcaster.broadcastLatestSnapshots();

		assertThat(first.events()).containsExactly(
				"event:carrier-count\nid:8c530c6c-f819-4ad6-b687-760dc698c617\ndata:{\"n_carriers\":3,\"score\":0.375,\"level\":\"LOW\"}\n\n"
		);
		assertThat(second.events()).containsExactly(
				"event:carrier-count\nid:8c530c6c-f819-4ad6-b687-760dc698c617\ndata:{\"n_carriers\":3,\"score\":0.375,\"level\":\"LOW\"}\n\n"
		);
	}

	@Test
	void sendsNullScoreFieldsWhenNoCongestionResultExistsYet() {
		store.updateIfLatest(snapshot("8c530c6c-f819-4ad6-b687-760dc698c617", "desk01", "2026-08-13T05:00:10Z", 3));
		CapturingEmitter emitter = new CapturingEmitter();

		broadcaster.subscribe(emitter);

		assertThat(emitter.events()).containsExactly(
				"event:carrier-count\nid:8c530c6c-f819-4ad6-b687-760dc698c617\ndata:{\"n_carriers\":3,\"score\":null,\"level\":null}\n\n"
		);
	}

	@Test
	void doesNotSendStaleSnapshotsWhenClientSubscribes() {
		store.updateIfLatest(snapshot("8c530c6c-f819-4ad6-b687-760dc698c617", "desk01", "2026-08-13T05:00:00Z", 3));
		airportGuideStore.handle(calculatedCongestion("2026-08-13T05:00:10Z"));
		CapturingEmitter emitter = new CapturingEmitter();

		broadcaster.subscribe(emitter);

		assertThat(emitter.events()).isEmpty();
	}

	@Test
	void sendsHeartbeatWhenThereIsNoSnapshot() {
		CapturingEmitter emitter = new CapturingEmitter();
		broadcaster.subscribe(emitter);
		emitter.clear();

		broadcaster.broadcastLatestSnapshots();

		assertThat(emitter.events()).containsExactly(":heartbeat\n\n");
	}

	@Test
	void sendsHeartbeatWhenOnlyStaleSnapshotsRemain() {
		CapturingEmitter emitter = new CapturingEmitter();
		store.updateIfLatest(snapshot("8c530c6c-f819-4ad6-b687-760dc698c617", "desk01", "2026-08-13T05:00:00Z", 3));
		airportGuideStore.handle(calculatedCongestion("2026-08-13T05:00:10Z"));
		broadcaster.subscribe(emitter);
		emitter.clear();

		broadcaster.broadcastLatestSnapshots();

		assertThat(emitter.events()).containsExactly(":heartbeat\n\n");
	}

	@Test
	void removesOnlyEmitterThatFailsDuringBroadcast() {
		FailingEmitter failing = new FailingEmitter();
		CapturingEmitter healthy = new CapturingEmitter();
		broadcaster.subscribe(failing);
		broadcaster.subscribe(healthy);
		healthy.clear();
		store.updateIfLatest(snapshot("8c530c6c-f819-4ad6-b687-760dc698c617", "desk01", "2026-08-13T05:00:10Z", 3));
		airportGuideStore.handle(calculatedCongestion("2026-08-13T05:00:13Z"));

		broadcaster.broadcastLatestSnapshots();
		healthy.clear();
		broadcaster.broadcastLatestSnapshots();

		assertThat(healthy.events()).containsExactly(
				"event:carrier-count\nid:8c530c6c-f819-4ad6-b687-760dc698c617\ndata:{\"n_carriers\":3,\"score\":0.375,\"level\":\"LOW\"}\n\n"
		);
		assertThat(failing.completed()).isTrue();
	}

	@Test
	void drainsActiveEmittersWithReconnectHint() {
		CapturingEmitter first = new CapturingEmitter();
		CapturingEmitter second = new CapturingEmitter();
		broadcaster.subscribe(first);
		broadcaster.subscribe(second);
		first.clear();
		second.clear();

		int drained = broadcaster.drainActiveEmitters();

		assertThat(drained).isEqualTo(2);
		assertThat(broadcaster.activeEmitterCount()).isZero();
		assertThat(first.events()).containsExactly("retry:1000\n:drain\n\n");
		assertThat(second.events()).containsExactly("retry:1000\n:drain\n\n");
	}

	@Test
	void reportsActiveEmitterCount() {
		assertThat(broadcaster.activeEmitterCount()).isZero();

		broadcaster.subscribe(new CapturingEmitter());

		assertThat(broadcaster.activeEmitterCount()).isOne();
	}

	@Test
	void keepsScoreWhileCongestionResultIsWithinCongestionMaxAge() {
		// carrier snapshot은 5초 TTL, 혼잡도는 25초 TTL — 10초 주기 발행에도 score 유지
		store.updateIfLatest(snapshot("8c530c6c-f819-4ad6-b687-760dc698c617", "desk01", "2026-08-13T05:00:10Z", 3));
		airportGuideStore.handle(calculatedCongestion("2026-08-13T04:59:52Z"));
		CapturingEmitter emitter = new CapturingEmitter();

		broadcaster.subscribe(emitter);

		assertThat(emitter.events()).containsExactly(
				"event:carrier-count\nid:8c530c6c-f819-4ad6-b687-760dc698c617\ndata:{\"n_carriers\":3,\"score\":0.375,\"level\":\"LOW\"}\n\n"
		);
	}

	@Test
	void dropsScoreWhenCongestionResultIsOlderThanCongestionMaxAge() {
		store.updateIfLatest(snapshot("8c530c6c-f819-4ad6-b687-760dc698c617", "desk01", "2026-08-13T05:00:10Z", 3));
		airportGuideStore.handle(calculatedCongestion("2026-08-13T04:59:40Z"));
		CapturingEmitter emitter = new CapturingEmitter();

		broadcaster.subscribe(emitter);

		assertThat(emitter.events()).containsExactly(
				"event:carrier-count\nid:8c530c6c-f819-4ad6-b687-760dc698c617\ndata:{\"n_carriers\":3,\"score\":null,\"level\":null}\n\n"
		);
	}

	private static LatestInferenceSnapshot snapshot(String messageId, String spaceId, String endedAt, int carriers) {
		return new LatestInferenceSnapshot(messageId, spaceId, Instant.parse(endedAt), carriers);
	}

	private static CongestionCalculatedMessage calculatedCongestion(String calculatedAt) {
		Instant instant = Instant.parse(calculatedAt);
		return CongestionCalculatedMessage.calculated(
				UUID.randomUUID(),
				instant,
				"platform-congestion-v2",
				false,
				12.0,
				48L,
				6.0,
				instant.minusSeconds(300),
				List.of(),
				new CongestionInputReferences(
						Instant.EPOCH,
						0,
						Instant.EPOCH,
						0,
						Instant.EPOCH,
						0,
						"8c530c6c-f819-4ad6-b687-760dc698c617",
						Instant.EPOCH
				)
		);
	}

	private static class CapturingEmitter extends SseEmitter {

		private final List<String> events = new ArrayList<>();

		private CapturingEmitter() {
			super(0L);
		}

		@Override
		public void send(SseEventBuilder builder) throws IOException {
			StringBuilder encoded = new StringBuilder();
			builder.build().forEach(data -> encoded.append(data.getData()));
			events.add(encoded.toString());
		}

		private List<String> events() {
			return events;
		}

		private void clear() {
			events.clear();
		}
	}

	private static final class FailingEmitter extends CapturingEmitter {

		private boolean completed;

		@Override
		public void send(SseEventBuilder builder) throws IOException {
			throw new IOException("client disconnected");
		}

		@Override
		public void complete() {
			completed = true;
		}

		private boolean completed() {
			return completed;
		}
	}
}
