package com.drrk.main.consumer.inference;

import java.io.IOException;
import java.time.Duration;
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
	private final ObjectMapper objectMapper;
	private final Duration emitterTimeout;
	private final Set<SseEmitter> emitters = ConcurrentHashMap.newKeySet();

	@Autowired
	public InferenceSseBroadcaster(
			LatestInferenceSnapshotStore store,
			ObjectMapper objectMapper,
			@Value("${inference.stream.emitter-timeout:PT30M}") Duration emitterTimeout
	) {
		this.store = store;
		this.objectMapper = objectMapper;
		this.emitterTimeout = emitterTimeout;
	}

	InferenceSseBroadcaster(LatestInferenceSnapshotStore store, Duration emitterTimeout) {
		this(store, new ObjectMapper(), emitterTimeout);
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
		List<LatestInferenceSnapshot> snapshots = store.findAll();
		for (SseEmitter emitter : List.copyOf(emitters)) {
			if (snapshots.isEmpty()) {
				sendHeartbeat(emitter);
				continue;
			}
			sendSnapshots(emitter, snapshots);
		}
	}

	private void sendCurrentState(SseEmitter emitter) {
		List<LatestInferenceSnapshot> snapshots = store.findAll();
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

	private String toJson(LatestInferenceSnapshot snapshot) {
		try {
			return objectMapper.writeValueAsString(
					new CarrierCountStreamResponse(snapshot.spaceId(), snapshot.carrierCount())
			);
		} catch (JacksonException exception) {
			throw new IllegalStateException("failed to serialize carrier count SSE payload", exception);
		}
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
