package com.drrk.main.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.drrk.main.consumer.congestion.LatestAirportGuideStore;
import com.drrk.main.consumer.inference.InferenceSseBroadcaster;
import com.drrk.main.consumer.inference.LatestInferenceSnapshotStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class InternalSseControllerTest {

	private InferenceSseBroadcaster broadcaster;
	private InternalSseController controller;

	@BeforeEach
	void setUp() {
		broadcaster = new InferenceSseBroadcaster(
				new LatestInferenceSnapshotStore(),
				new LatestAirportGuideStore(),
				new ObjectMapper(),
				Clock.fixed(Instant.parse("2026-08-13T05:00:04Z"), ZoneOffset.UTC),
				Duration.ofSeconds(5),
				Duration.ofSeconds(25),
				Duration.ofMinutes(30)
		);
		controller = new InternalSseController(broadcaster);
	}

	@Test
	void returnsActiveConnectionCountAsPlainText() {
		broadcaster.subscribe();

		assertThat(controller.activeConnections()).isEqualTo("1");
	}

	@Test
	void drainsAndReturnsCounts() {
		broadcaster.subscribe();
		broadcaster.subscribe();

		Map<String, Integer> response = controller.drain();

		assertThat(response).containsEntry("drained", 2);
		assertThat(response).containsEntry("active", 0);
	}
}
