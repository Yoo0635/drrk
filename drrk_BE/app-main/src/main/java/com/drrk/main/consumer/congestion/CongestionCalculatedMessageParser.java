package com.drrk.main.consumer.congestion;

import com.drrk.messaging.congestion.AirportRoute;
import com.drrk.messaging.congestion.CongestionCalculatedMessage;
import com.drrk.messaging.congestion.CongestionCalculationStatus;
import com.drrk.messaging.congestion.MovingWalkwayStatus;
import com.drrk.messaging.congestion.RouteCongestionResult;
import com.drrk.messaging.congestion.RouteStatus;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

public class CongestionCalculatedMessageParser {

	private static final double CAPACITY = 4.2;
	private static final double EPSILON = 1.0e-9;

	private final ObjectMapper objectMapper;

	public CongestionCalculatedMessageParser(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public CongestionCalculatedMessage parse(String payload) {
		CongestionCalculatedMessage message;
		try {
			message = objectMapper.readValue(payload, CongestionCalculatedMessage.class);
		} catch (JacksonException | IllegalArgumentException | NullPointerException exception) {
			throw new InvalidCongestionMessageException("Invalid congestion JSON", exception);
		}
		if (message == null) {
			throw new InvalidCongestionMessageException("Congestion message must not be null");
		}
		validate(message);
		return message;
	}

	private void validate(CongestionCalculatedMessage message) {
		validateUuidV4(message.messageId(), "messageId");
		if (!"3.0".equals(message.schemaVersion())) {
			throw new InvalidCongestionMessageException("Unsupported schemaVersion");
		}
		if (message.status() == CongestionCalculationStatus.FORMULA_PENDING) {
			if (!"formula-pending-v0".equals(message.calculationVersion())
					|| message.score() != null
					|| message.level() != null
					|| !message.routeResults().isEmpty()
					|| message.recommendedRoute() != null
					|| !message.railroadArrivals().isEmpty()) {
				throw new InvalidCongestionMessageException("Invalid FORMULA_PENDING payload");
			}
		}
		if (message.status() == CongestionCalculationStatus.CALCULATED) {
			validateCalculated(message);
		}
		validateUuidV4(message.inputs().modelMessageId(), "inputs.modelMessageId");
	}

	private void validateCalculated(CongestionCalculatedMessage message) {
		Set<AirportRoute> routes = new HashSet<>(message.routeResults().stream()
				.map(RouteCongestionResult::route)
				.toList());
		if (message.calculationVersion() == null || message.calculationVersion().isBlank()
				|| message.routeResults().size() != 3
				|| routes.size() != 3
				|| !routes.containsAll(List.of(AirportRoute.A, AirportRoute.B, AirportRoute.C))) {
			throw new InvalidCongestionMessageException("Invalid CALCULATED routes");
		}
		RouteCongestionResult recommended = message.routeResults().stream()
				.filter(result -> result.route() == message.recommendedRoute())
				.findFirst()
				.orElseThrow(() -> new InvalidCongestionMessageException("Recommended route is missing"));
		if (message.score() == null || !Double.isFinite(message.score())
				|| Double.compare(message.score(), recommended.volumeCapacityRatio()) != 0
				|| !recommended.congestionStatus().name().equals(message.level())) {
			throw new InvalidCongestionMessageException("Invalid CALCULATED summary");
		}
		for (RouteCongestionResult result : message.routeResults()) {
			double expectedLoad = result.stay() + result.incoming() + result.residual();
			if (!nearlyEqual(result.load(), expectedLoad)
					|| !nearlyEqual(result.volumeCapacityRatio(), result.load() / CAPACITY)) {
				throw new InvalidCongestionMessageException("Route congestion formula is inconsistent");
			}
			MovingWalkwayStatus expected = MovingWalkwayStatus.fromVolumeCapacityRatio(
					result.volumeCapacityRatio()
			);
			if (result.congestionStatus() != expected) {
				throw new InvalidCongestionMessageException("Route congestion status does not match v/c");
			}
			RouteStatus expectedRouteStatus = message.sensorDetected() && result.route() == AirportRoute.B
					? RouteStatus.CONGESTED
					: RouteStatus.CLEAR;
			if (result.status() != expectedRouteStatus) {
				throw new InvalidCongestionMessageException("Route sensor status does not match sensorDetected");
			}
		}
		RouteCongestionResult fastest = message.routeResults().stream()
				.min(Comparator.comparingLong(RouteCongestionResult::totalTravelTimeSeconds)
						.thenComparing(RouteCongestionResult::route))
				.orElseThrow();
		if (fastest.route() != message.recommendedRoute()) {
			throw new InvalidCongestionMessageException("Recommended route is not the shortest route");
		}
	}

	private boolean nearlyEqual(double left, double right) {
		double scale = Math.max(1d, Math.max(Math.abs(left), Math.abs(right)));
		return Math.abs(left - right) <= EPSILON * scale;
	}

	private void validateUuidV4(String value, String field) {
		try {
			if (UUID.fromString(value).version() != 4) {
				throw new InvalidCongestionMessageException(field + " must be UUID v4");
			}
		} catch (IllegalArgumentException | NullPointerException exception) {
			throw new InvalidCongestionMessageException(field + " must be UUID v4", exception);
		}
	}
}
