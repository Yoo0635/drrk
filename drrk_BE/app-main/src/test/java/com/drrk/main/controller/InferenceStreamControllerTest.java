package com.drrk.main.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.drrk.main.consumer.inference.InferenceSseBroadcaster;
import com.drrk.main.consumer.inference.LatestInferenceSnapshot;
import com.drrk.main.consumer.inference.LatestInferenceSnapshotStore;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

class InferenceStreamControllerTest {

	private LatestInferenceSnapshotStore store;
	private InferenceStreamController controller;

	@BeforeEach
	void setUp() {
		store = new LatestInferenceSnapshotStore();
		InferenceSseBroadcaster broadcaster = new InferenceSseBroadcaster(
				store,
				new ObjectMapper(),
				Duration.ofMinutes(30)
		);
		controller = new InferenceStreamController(broadcaster);
	}

	@Test
	void opensCarrierCountEventStreamWithNoCacheAndDisabledNginxBuffering() {
		store.updateIfLatest(new LatestInferenceSnapshot(
				"8c530c6c-f819-4ad6-b687-760dc698c617",
				"desk01",
				Instant.parse("2026-08-13T05:00:00Z"),
				3
		));

		ResponseEntity<SseEmitter> response = controller.streamCarrierCounts();

		assertThat(response.getStatusCode().value()).isEqualTo(200);
		assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.TEXT_EVENT_STREAM);
		assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-cache");
		assertThat(response.getHeaders().getFirst("X-Accel-Buffering")).isEqualTo("no");
		assertThat(response.getHeaders().getFirst(HttpHeaders.CONNECTION)).isEqualTo("keep-alive");
		assertThat(response.getBody()).isNotNull();
	}
}
