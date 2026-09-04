package com.drrk.main.consumer.inference;

import com.drrk.main.consumer.congestion.LatestAirportGuideStore;
import com.drrk.main.consumer.congestion.CongestionDeliveryPublisher;
import com.drrk.main.consumer.congestion.CongestionDeliveryStatus;
import com.drrk.main.consumer.congestion.CongestionSnapshot;
import com.drrk.messaging.congestion.CongestionCalculatedMessage;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
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
public class InferenceSseBroadcaster implements CongestionDeliveryPublisher {

	private static final Logger log = LoggerFactory.getLogger(InferenceSseBroadcaster.class);
	private static final String EVENT_NAME = "carrier-count";
	private static final String CONGESTION_EVENT_NAME = "congestion-delivery";
	private static final String CONGESTION_HISTORY_EVENT_NAME = "congestion-history";
	private static final Duration DRAIN_RETRY = Duration.ofSeconds(1);
	private static final int INITIAL_BUFFER_LIMIT = 120;

	private final LatestInferenceSnapshotStore store;
	private final LatestAirportGuideStore airportGuideStore;
	private final ObjectMapper objectMapper;
	private final Clock clock;
	private final Duration snapshotMaxAge;
	private final Duration congestionMaxAge;
	private final Duration emitterTimeout;
	private final Set<ClientEmitter> emitters = ConcurrentHashMap.newKeySet();

	@Autowired
	public InferenceSseBroadcaster(
			LatestInferenceSnapshotStore store,
			LatestAirportGuideStore airportGuideStore,
			ObjectMapper objectMapper,
			Clock clock,
			@Value("${inference.stream.snapshot-max-age:PT5S}") Duration snapshotMaxAge,
			@Value("${inference.stream.congestion-max-age:PT25S}") Duration congestionMaxAge,
			@Value("${inference.stream.emitter-timeout:PT30M}") Duration emitterTimeout
	) {
		this.store = store;
		this.airportGuideStore = airportGuideStore;
		this.objectMapper = objectMapper;
		this.clock = clock;
		this.snapshotMaxAge = snapshotMaxAge;
		this.congestionMaxAge = congestionMaxAge;
		this.emitterTimeout = emitterTimeout;
	}

	InferenceSseBroadcaster(
			LatestInferenceSnapshotStore store,
			LatestAirportGuideStore airportGuideStore,
			Clock clock,
			Duration snapshotMaxAge,
			Duration congestionMaxAge,
			Duration emitterTimeout
	) {
		this(store, airportGuideStore, new ObjectMapper(), clock, snapshotMaxAge, congestionMaxAge, emitterTimeout);
	}

	public SseEmitter subscribe() {
		return subscribe(new SseEmitter(emitterTimeout.toMillis()));
	}

	public int activeEmitterCount() {
		return emitters.size();
	}

	public int drainActiveEmitters() {
		List<ClientEmitter> currentEmitters = List.copyOf(emitters);
		for (ClientEmitter client : currentEmitters) {
			sendDrainAndComplete(client);
		}
		return currentEmitters.size();
	}

	SseEmitter subscribe(SseEmitter emitter) {
		ClientEmitter client = new ClientEmitter(emitter);
		emitters.add(client);
		emitter.onCompletion(() -> emitters.remove(client));
		emitter.onTimeout(() -> removeAndComplete(client));
		emitter.onError(error -> removeAndComplete(client));
		sendCurrentState(client);
		if (!client.finishInitialization()) {
			removeAndComplete(client);
		}
		return emitter;
	}

	@Scheduled(fixedRateString = "${inference.stream.fixed-rate:PT5S}")
	public void broadcastLatestSnapshots() {
		List<LatestInferenceSnapshot> snapshots = currentSnapshots();
		for (ClientEmitter client : List.copyOf(emitters)) {
			if (snapshots.isEmpty()) {
				sendHeartbeat(client);
				continue;
			}
			sendSnapshots(client, snapshots);
		}
	}

	@Override
	public void publish(
			CongestionCalculatedMessage message,
			CongestionDeliveryStatus deliveryStatus,
			int retryCount
	) {
		Instant now = now();
		String payload = toJson(new CongestionDeliveryStreamResponse(
				message.messageId(),
				message.calculatedAt(),
				message.score(),
				message.level(),
				deliveryStatus,
				retryCount,
				now
		));
		for (ClientEmitter client : List.copyOf(emitters)) {
			if (!client.sendOrBuffer(new PendingEvent(CONGESTION_EVENT_NAME, message.messageId(), payload))) {
				removeAndComplete(client);
			}
		}
	}

	private void sendCurrentState(ClientEmitter client) {
		sendCongestionHistory(client);
		List<LatestInferenceSnapshot> snapshots = currentSnapshots();
		if (snapshots.isEmpty()) {
			return;
		}
		sendSnapshots(client, snapshots);
	}

	private void sendSnapshots(ClientEmitter client, List<LatestInferenceSnapshot> snapshots) {
		for (LatestInferenceSnapshot snapshot : snapshots) {
			if (!client.sendOrBuffer(new PendingEvent(EVENT_NAME, snapshot.messageId(), toJson(snapshot)))) {
				removeAndComplete(client);
				return;
			}
		}
	}

	private void sendCongestionHistory(ClientEmitter client) {
		Instant now = now();
		List<CongestionHistorySampleResponse> samples = airportGuideStore.recentSnapshots(now).stream()
				.map(this::toHistorySample)
				.toList();
		String payload = toJson(new CongestionHistoryStreamResponse(now, now.minus(Duration.ofMinutes(10)), samples));
		if (!client.sendNow(new PendingEvent(CONGESTION_HISTORY_EVENT_NAME, "congestion-history-" + now.toEpochMilli(), payload))) {
			removeAndComplete(client);
		}
	}

	private CongestionHistorySampleResponse toHistorySample(CongestionSnapshot snapshot) {
		CongestionCalculatedMessage message = snapshot.message();
		return new CongestionHistorySampleResponse(
				message.messageId(),
				message.calculatedAt(),
				message.score(),
				message.level(),
				snapshot.deliveryStatus(),
				snapshot.retryCount()
		);
	}

	private void sendHeartbeat(ClientEmitter client) {
		try {
			client.emitter().send(SseEmitter.event().comment("heartbeat"));
		} catch (IOException | IllegalStateException exception) {
			log.debug("[INFERENCE SSE HEARTBEAT FAILED] reason={}", exception.getMessage());
			removeAndComplete(client);
		}
	}

	private void sendDrainAndComplete(ClientEmitter client) {
		try {
			client.emitter().send(SseEmitter.event()
					.reconnectTime(DRAIN_RETRY.toMillis())
					.comment("drain"));
		} catch (IOException | IllegalStateException exception) {
			log.debug("[INFERENCE SSE DRAIN FAILED] reason={}", exception.getMessage());
		} finally {
			removeAndComplete(client);
		}
	}

	private List<LatestInferenceSnapshot> currentSnapshots() {
		return store.findAllFresh(now(), snapshotMaxAge);
	}

	private String toJson(LatestInferenceSnapshot snapshot) {
		var latestGuide = airportGuideStore.latestFresh(now(), congestionMaxAge).orElse(null);
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

	private String toJson(CongestionDeliveryStreamResponse response) {
		try {
			return objectMapper.writeValueAsString(response);
		} catch (JacksonException exception) {
			throw new IllegalStateException("failed to serialize congestion delivery SSE payload", exception);
		}
	}

	private String toJson(CongestionHistoryStreamResponse response) {
		try {
			return objectMapper.writeValueAsString(response);
		} catch (JacksonException exception) {
			throw new IllegalStateException("failed to serialize congestion history SSE payload", exception);
		}
	}

	private Instant now() {
		return clock.instant();
	}

	private void removeAndComplete(ClientEmitter client) {
		emitters.remove(client);
		try {
			client.emitter().complete();
		} catch (IllegalStateException ignored) {
			log.debug("[INFERENCE SSE ALREADY COMPLETED]");
		}
	}

	private record PendingEvent(String name, String id, String payload) {
	}

	private static final class ClientEmitter {

		private final SseEmitter emitter;
		private final List<PendingEvent> buffer = new ArrayList<>();
		private boolean initializing = true;

		private ClientEmitter(SseEmitter emitter) {
			this.emitter = emitter;
		}

		private SseEmitter emitter() {
			return emitter;
		}

		private synchronized boolean sendOrBuffer(PendingEvent event) {
			if (initializing) {
				if (buffer.size() >= INITIAL_BUFFER_LIMIT) {
					return false;
				}
				buffer.add(event);
				return true;
			}
			return sendNow(event);
		}

		private synchronized boolean sendNow(PendingEvent event) {
			try {
				emitter.send(SseEmitter.event()
						.name(event.name())
						.id(event.id())
						.data(event.payload()));
				return true;
			} catch (IOException | IllegalStateException exception) {
				log.debug("[SSE DISCONNECTED] event={} reason={}", event.name(), exception.getMessage());
				return false;
			}
		}

		private synchronized boolean finishInitialization() {
			initializing = false;
			for (PendingEvent event : List.copyOf(buffer)) {
				if (!sendNow(event)) {
					buffer.clear();
					return false;
				}
			}
			buffer.clear();
			return true;
		}
	}
}
