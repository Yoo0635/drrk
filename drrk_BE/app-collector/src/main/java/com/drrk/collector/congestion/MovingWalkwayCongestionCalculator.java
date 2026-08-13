package com.drrk.collector.congestion;

import com.drrk.messaging.congestion.AirportRoute;
import com.drrk.messaging.congestion.CongestionCalculatedMessage;
import com.drrk.messaging.congestion.CongestionInputReferences;
import com.drrk.messaging.congestion.MovingWalkwayStatus;
import com.drrk.messaging.congestion.RailroadArrivalResult;
import com.drrk.messaging.congestion.RailroadArrivalStatus;
import com.drrk.messaging.congestion.RouteCongestionResult;
import com.drrk.messaging.congestion.RouteStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public class MovingWalkwayCongestionCalculator implements CongestionCalculator {

	static final double CAPACITY = 4.2;
	private static final long ROUTE_A_TOTAL_TRAVEL_TIME_SECONDS = 509;
	private static final long ROUTE_B_CLEAR_PASSAGE_TIME_SECONDS = 46;
	private static final long ROUTE_B_CONGESTED_PASSAGE_TIME_SECONDS = 110;
	private static final long ROUTE_B_FIXED_TRAVEL_TIME_SECONDS = 370;
	private static final long ROUTE_C_TOTAL_TRAVEL_TIME_SECONDS = 434;
	private static final ZoneId AIRPORT_ZONE = ZoneId.of("Asia/Seoul");
	private static final DateTimeFormatter MINUTE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
	private static final DateTimeFormatter SECOND_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
	private static final DateTimeFormatter DISPLAY_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

	private final Clock clock;
	private final Supplier<UUID> messageIdSupplier;

	public MovingWalkwayCongestionCalculator(Clock clock, Supplier<UUID> messageIdSupplier) {
		this.clock = Objects.requireNonNull(clock, "clock");
		this.messageIdSupplier = Objects.requireNonNull(messageIdSupplier, "messageIdSupplier");
	}

	@Override
	public CongestionCalculatedMessage calculate(CongestionInputs inputs) {
		Objects.requireNonNull(inputs, "inputs");
		Instant calculatedAt = clock.instant();
		boolean sensorDetected = inputs.modelMeasurement().carrierCount() > 0;
		List<RouteCongestionResult> routeResults = List.of(
				fixedRoute(AirportRoute.A, calculatedAt, ROUTE_A_TOTAL_TRAVEL_TIME_SECONDS),
				routeB(calculatedAt, inputs.modelMeasurement().carrierCount(), sensorDetected),
				fixedRoute(AirportRoute.C, calculatedAt, ROUTE_C_TOTAL_TRAVEL_TIME_SECONDS)
		);
		RouteCongestionResult recommended = routeResults.stream()
				.min(Comparator.comparingLong(RouteCongestionResult::totalTravelTimeSeconds)
						.thenComparing(RouteCongestionResult::route))
				.orElseThrow();

		return CongestionCalculatedMessage.calculated(
				messageIdSupplier.get(),
				calculatedAt,
				"moving-walkway-v2",
				sensorDetected,
				routeResults,
				recommended.route(),
				mapRailroadArrivals(inputs.railroadOperation().items(), calculatedAt),
				inputReferences(inputs)
		);
	}

	private RouteCongestionResult fixedRoute(AirportRoute route, Instant calculatedAt, long totalTravelTimeSeconds) {
		return routeResult(
				route,
				calculatedAt,
				0,
				RouteStatus.CLEAR,
				0,
				totalTravelTimeSeconds
		);
	}

	private RouteCongestionResult routeB(Instant calculatedAt, long carrierCount, boolean sensorDetected) {
		long passageTimeSeconds = sensorDetected
				? ROUTE_B_CONGESTED_PASSAGE_TIME_SECONDS
				: ROUTE_B_CLEAR_PASSAGE_TIME_SECONDS;
		return routeResult(
				AirportRoute.B,
				calculatedAt,
				carrierCount,
				sensorDetected ? RouteStatus.CONGESTED : RouteStatus.CLEAR,
				passageTimeSeconds,
				ROUTE_B_FIXED_TRAVEL_TIME_SECONDS + passageTimeSeconds
		);
	}

	private RouteCongestionResult routeResult(
			AirportRoute route,
			Instant calculatedAt,
			double stay,
			RouteStatus status,
			long passageTimeSeconds,
			long totalTravelTimeSeconds
	) {
		double volumeCapacityRatio = stay / CAPACITY;
		return new RouteCongestionResult(
				route,
				calculatedAt,
				stay,
				0,
				0,
				stay,
				volumeCapacityRatio,
				MovingWalkwayStatus.fromVolumeCapacityRatio(volumeCapacityRatio),
				status,
				passageTimeSeconds,
				totalTravelTimeSeconds
		);
	}

	private List<RailroadArrivalResult> mapRailroadArrivals(
			List<RailroadOperationItem> items,
			Instant calculatedAt
	) {
		return items.stream()
				.sorted(Comparator.comparing(
						item -> parseAirportTime(item.scheduledArrivalTime()),
						Comparator.nullsLast(Comparator.naturalOrder())
				))
				.map(item -> toRailroadArrival(item, calculatedAt))
				.filter(Objects::nonNull)
				.toList();
	}

	private RailroadArrivalResult toRailroadArrival(RailroadOperationItem item, Instant calculatedAt) {
		Instant scheduled = parseAirportTime(item.scheduledArrivalTime());
		if (scheduled == null || item.trainNumber() == null || item.trainNumber().isBlank()
				|| item.trainType() == null || item.trainType().isBlank()) {
			return null;
		}
		Instant actual = parseAirportTime(item.actualArrivalTime());
		if (item.actualArrivalTime() != null && actual == null) {
			return null;
		}
		RailroadArrivalStatus status;
		if (actual != null) {
			status = RailroadArrivalStatus.ARRIVED;
		} else if (calculatedAt.isAfter(scheduled)) {
			status = RailroadArrivalStatus.DELAYED;
		} else {
			status = RailroadArrivalStatus.SCHEDULED;
		}
		return new RailroadArrivalResult(
				item.trainNumber(),
				item.trainType(),
				formatAirportTime(scheduled),
				actual == null ? null : formatAirportTime(actual),
				status
		);
	}

	private Instant parseAirportTime(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		String normalized = digits(value);
		try {
			DateTimeFormatter formatter = normalized.length() == 14 ? SECOND_FORMAT : MINUTE_FORMAT;
			if (normalized.length() != 12 && normalized.length() != 14) {
				return null;
			}
			return LocalDateTime.parse(normalized, formatter).atZone(AIRPORT_ZONE).toInstant();
		} catch (DateTimeParseException exception) {
			return null;
		}
	}

	private String formatAirportTime(Instant instant) {
		return instant.atZone(AIRPORT_ZONE).format(DISPLAY_TIME_FORMAT);
	}

	private CongestionInputReferences inputReferences(CongestionInputs inputs) {
		return new CongestionInputReferences(
				inputs.arrivalStatus().collectedAt(),
				inputs.arrivalStatus().items().size(),
				inputs.passengerForecast().collectedAt(),
				inputs.passengerForecast().items().size(),
				inputs.railroadOperation().collectedAt(),
				inputs.railroadOperation().items().size(),
				inputs.modelMeasurement().messageId(),
				inputs.modelMeasurement().measuredAt()
		);
	}

	private String digits(String value) {
		return value.replaceAll("[^0-9]", "");
	}
}
