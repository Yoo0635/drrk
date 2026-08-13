package com.drrk.main.consumer.inference;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class LatestInferenceSnapshotStoreTest {

	private final LatestInferenceSnapshotStore store = new LatestInferenceSnapshotStore();

	@Test
	void storesFirstSnapshotForASpace() {
		LatestInferenceSnapshot snapshot = new LatestInferenceSnapshot(
				"8c530c6c-f819-4ad6-b687-760dc698c617",
				"desk01",
				Instant.parse("2026-08-13T05:00:00Z"),
				3
		);

		store.updateIfLatest(snapshot);

		assertThat(store.findAll()).containsExactly(snapshot);
	}

	@Test
	void replacesOnlyWithNewerSnapshotForTheSameSpace() {
		LatestInferenceSnapshot first = new LatestInferenceSnapshot(
				"8c530c6c-f819-4ad6-b687-760dc698c617",
				"desk01",
				Instant.parse("2026-08-13T05:00:00Z"),
				3
		);
		LatestInferenceSnapshot newer = new LatestInferenceSnapshot(
				"9d82ae8a-0a67-4540-b519-528386835f80",
				"desk01",
				Instant.parse("2026-08-13T05:00:10Z"),
				5
		);
		LatestInferenceSnapshot older = new LatestInferenceSnapshot(
				"61a44ce7-a70d-4872-82bc-9ea5e15334df",
				"desk01",
				Instant.parse("2026-08-13T04:59:50Z"),
				1
		);
		LatestInferenceSnapshot sameTime = new LatestInferenceSnapshot(
				"6eab24a5-9f96-4bb6-99c3-db5963fb6f63",
				"desk01",
				Instant.parse("2026-08-13T05:00:10Z"),
				7
		);

		store.updateIfLatest(first);
		store.updateIfLatest(newer);
		store.updateIfLatest(older);
		store.updateIfLatest(sameTime);

		assertThat(store.findAll()).containsExactly(newer);
	}

	@Test
	void keepsLatestSnapshotsSeparatedBySpace() {
		LatestInferenceSnapshot desk01 = new LatestInferenceSnapshot(
				"8c530c6c-f819-4ad6-b687-760dc698c617",
				"desk01",
				Instant.parse("2026-08-13T05:00:00Z"),
				3
		);
		LatestInferenceSnapshot desk02 = new LatestInferenceSnapshot(
				"9d82ae8a-0a67-4540-b519-528386835f80",
				"desk02",
				Instant.parse("2026-08-13T04:59:50Z"),
				1
		);

		store.updateIfLatest(desk02);
		store.updateIfLatest(desk01);

		assertThat(store.findAll()).containsExactly(desk01, desk02);
	}
}
