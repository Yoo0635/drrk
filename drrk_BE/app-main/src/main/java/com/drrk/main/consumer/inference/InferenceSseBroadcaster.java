package com.drrk.main.consumer.inference;

import com.drrk.main.consumer.congestion.LatestAirportGuideStore;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class InferenceSseBroadcaster {

	private static final Logger log = LoggerFactory.getLogger(InferenceSseBroadcaster.class);
	private static final String EVENT_NAME = "carrier-count";

	private final LatestInferenceSnapshotStore store;
	private final LatestAirportGuideStore airportGuideStore;
	private final ObjectMapper objectMapper;
	private final Clock clock;
	private final Duration snapshotMaxAge;
	private final Duration emitterTimeout;
	private final Set<SseEmitter> emitters = ConcurrentHashMap.newKeySet();

	@Autowired
	public InferenceSseBroadcaster(
			LatestInferenceSnapshotStore store,
			LatestAirportGuideStore airportGuideStore,
			ObjectMapper objectMapper,
			Clock clock,
			@Value("${inference.stream.snapshot-max-age:PT5S}") Duration snapshotMaxAge,
			@Value("${inference.stream.emitter-timeout:PT30M}") Duration emitterTimeout
	) {
		this.store = store;
		this.airportGuideStore = airportGuideStore;
		this.objectMapper = objectMapper;
		this.clock = clock;
		this.snapshotMaxAge = snapshotMaxAge;
		this.emitterTimeout = emitterTimeout;
	}

	InferenceSseBroadcaster(
			LatestInferenceSnapshotStore store,
			LatestAirportGuideStore airportGuideStore,
			Clock clock,
			Duration snapshotMaxAge,
			Duration emitterTimeout
	) {
		this(store, airportGuideStore, new ObjectMapper(), clock, snapshotMaxAge, emitterTimeout);
	}

	public SseEmitter subscribe() {
		return subscribe(new SseEmitter(emitterTimeout.toMillis()));
	}

	SseEmitter subscribe(SseEmitter emitter) {
		emitters.add(emitter);
		emitter.onCompletion(() -> emitters.remove(emitter));
		emitter.onTimeout(() -> removeAndComplete(emitter));
		emitter.onError(error -> removeAndComplete(emitter));
		sendCurrentState(emitter);
		return emitter;
	}

	@Scheduled(fixedRateString = "${inference.stream.fixed-rate:PT5S}")
	public void broadcastLatestSnapshots() {
		List<LatestInferenceSnapshot> snapshots = currentSnapshots();
		for (SseEmitter emitter : List.copyOf(emitters)) {
			if (snapshots.isEmpty()) {
				sendHeartbeat(emitter);
				continue;
			}
			sendSnapshots(emitter, snapshots);
		}
	}

	private void sendCurrentState(SseEmitter emitter) {
		List<LatestInferenceSnapshot> snapshots = currentSnapshots();
		if (snapshots.isEmpty()) {
			return;
		}
		sendSnapshots(emitter, snapshots);
	}

	private void sendSnapshots(SseEmitter emitter, List<LatestInferenceSnapshot> snapshots) {
		for (LatestInferenceSnapshot snapshot : snapshots) {
			try {
				emitter.send(SseEmitter.event()
						.name(EVENT_NAME)
						.id(snapshot.messageId())
						.data(toJson(snapshot)));
			} catch (IOException | IllegalStateException exception) {
				log.debug("[INFERENCE SSE DISCONNECTED] reason={}", exception.getMessage());
				removeAndComplete(emitter);
				return;
			}
		}
	}

	private void sendHeartbeat(SseEmitter emitter) {
		try {
			emitter.send(SseEmitter.event().comment("heartbeat"));
		} catch (IOException | IllegalStateException exception) {
			log.debug("[INFERENCE SSE HEARTBEAT FAILED] reason={}", exception.getMessage());
			removeAndComplete(emitter);
		}
	}

	private List<LatestInferenceSnapshot> currentSnapshots() {
		return store.findAllFresh(now(), snapshotMaxAge);
	}

	private String toJson(LatestInferenceSnapshot snapshot) {
		var latestGuide = airportGuideStore.latestFresh(now(), snapshotMaxAge).orElse(null);
		try {
			return objectMapper.writeValueAsString(
					new CarrierCountStreamResponse(
							snapshot.carrierCount(),
							latestGuide == null ? null : latestGuide.score(),
							latestGuide == null ? null : latestGuide.level()
					)
			);
		} catch (JacksonException exception) {
			throw new IllegalStateException("failed to serialize carrier count SSE payload", exception);
		}
	}

	private Instant now() {
		return clock.instant();
	}

	private void removeAndComplete(SseEmitter emitter) {
		emitters.remove(emitter);
		try {
			emitter.complete();
		} catch (IllegalStateException ignored) {
			log.debug("[INFERENCE SSE ALREADY COMPLETED]");
		}
	}
}
