package com.drrk.main.consumer.inference;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class InferenceSseBroadcasterTest {

	private final LatestInferenceSnapshotStore store = new LatestInferenceSnapshotStore();
	private final InferenceSseBroadcaster broadcaster = new InferenceSseBroadcaster(store, Duration.ofMinutes(30));

	@Test
	void sendsLatestSnapshotsImmediatelyWhenClientSubscribes() {
		store.updateIfLatest(snapshot("8c530c6c-f819-4ad6-b687-760dc698c617", "desk01", "2026-08-13T05:00:00Z", 3));
		store.updateIfLatest(snapshot("9d82ae8a-0a67-4540-b519-528386835f80", "desk02", "2026-08-13T05:00:10Z", 1));
		CapturingEmitter emitter = new CapturingEmitter();

		broadcaster.subscribe(emitter);

		assertThat(emitter.events()).containsExactly(
				"event:carrier-count\nid:8c530c6c-f819-4ad6-b687-760dc698c617\ndata:{\"space_id\":\"desk01\",\"n_carriers\":3}\n\n",
				"event:carrier-count\nid:9d82ae8a-0a67-4540-b519-528386835f80\ndata:{\"space_id\":\"desk02\",\"n_carriers\":1}\n\n"
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
		store.updateIfLatest(snapshot("8c530c6c-f819-4ad6-b687-760dc698c617", "desk01", "2026-08-13T05:00:00Z", 3));

		broadcaster.broadcastLatestSnapshots();

		assertThat(first.events()).containsExactly(
				"event:carrier-count\nid:8c530c6c-f819-4ad6-b687-760dc698c617\ndata:{\"space_id\":\"desk01\",\"n_carriers\":3}\n\n"
		);
		assertThat(second.events()).containsExactly(
				"event:carrier-count\nid:8c530c6c-f819-4ad6-b687-760dc698c617\ndata:{\"space_id\":\"desk01\",\"n_carriers\":3}\n\n"
		);
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
	void removesOnlyEmitterThatFailsDuringBroadcast() {
		FailingEmitter failing = new FailingEmitter();
		CapturingEmitter healthy = new CapturingEmitter();
		broadcaster.subscribe(failing);
		broadcaster.subscribe(healthy);
		healthy.clear();
		store.updateIfLatest(snapshot("8c530c6c-f819-4ad6-b687-760dc698c617", "desk01", "2026-08-13T05:00:00Z", 3));

		broadcaster.broadcastLatestSnapshots();
		healthy.clear();
		broadcaster.broadcastLatestSnapshots();

		assertThat(healthy.events()).containsExactly(
				"event:carrier-count\nid:8c530c6c-f819-4ad6-b687-760dc698c617\ndata:{\"space_id\":\"desk01\",\"n_carriers\":3}\n\n"
		);
		assertThat(failing.completed()).isTrue();
	}

	private static LatestInferenceSnapshot snapshot(String messageId, String spaceId, String endedAt, int carriers) {
		return new LatestInferenceSnapshot(messageId, spaceId, Instant.parse(endedAt), carriers);
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
