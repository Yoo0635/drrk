package com.drrk.collector.congestion;

import com.drrk.messaging.congestion.AirportRoute;
import com.drrk.messaging.congestion.CongestionCalculatedMessage;
import com.drrk.messaging.congestion.CongestionInputReferences;
import com.drrk.messaging.congestion.MovingWalkwayStatus;
import com.drrk.messaging.congestion.RailroadArrivalResult;
import com.drrk.messaging.congestion.RailroadArrivalStatus;
import com.drrk.messaging.congestion.RouteCongestionResult;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public class MovingWalkwayCongestionCalculator implements CongestionCalculator {

	static final double CAPACITY = 4.2;
	private static final double FORECAST_WINDOW_SECONDS = 3_600d;
	private static final ZoneId AIRPORT_ZONE = ZoneId.of("Asia/Seoul");
	private static final DateTimeFormatter MINUTE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
	private static final DateTimeFormatter SECOND_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
	private static final DateTimeFormatter DISPLAY_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

	private final Clock clock;
	private final Supplier<UUID> messageIdSupplier;
	private final double carriersPerPassenger;
	private final List<MovingWalkwayRouteDefinition> routes;

	public MovingWalkwayCongestionCalculator(
			Clock clock,
			Supplier<UUID> messageIdSupplier,
			double carriersPerPassenger,
			MovingWalkwayRouteDefinition routeB,
			MovingWalkwayRouteDefinition routeC
	) {
		this.clock = Objects.requireNonNull(clock, "clock");
		this.messageIdSupplier = Objects.requireNonNull(messageIdSupplier, "messageIdSupplier");
		if (!Double.isFinite(carriersPerPassenger) || carriersPerPassenger < 0) {
			throw new IllegalArgumentException("carriersPerPassenger must be finite and non-negative");
		}
		this.carriersPerPassenger = carriersPerPassenger;
		this.routes = List.of(
				requireRoute(routeB, AirportRoute.B),
				requireRoute(routeC, AirportRoute.C)
		);
	}

	@Override
	public CongestionCalculatedMessage calculate(CongestionInputs inputs) {
		Objects.requireNonNull(inputs, "inputs");
		Instant calculatedAt = clock.instant();
		List<RouteCongestionResult> routeResults = routes.stream()
				.map(route -> calculateRoute(route, inputs, calculatedAt))
				.toList();
		RouteCongestionResult recommended = routeResults.stream()
				.min(Comparator.comparingLong(RouteCongestionResult::totalTravelTimeSeconds)
						.thenComparing(RouteCongestionResult::route))
				.orElseThrow();

		return CongestionCalculatedMessage.calculated(
				messageIdSupplier.get(),
				calculatedAt,
				"moving-walkway-v1",
				routeResults,
				recommended.route(),
				mapRailroadArrivals(inputs.railroadOperation().items(), calculatedAt),
				inputReferences(inputs)
		);
	}

	private RouteCongestionResult calculateRoute(
			MovingWalkwayRouteDefinition route,
			CongestionInputs inputs,
			Instant calculatedAt
	) {
		long delta = route.walkwayArrivalOffsetSeconds();
		Instant walkwayArrival = calculatedAt.plusSeconds(delta);
		double measuredCarriers = inputs.modelMeasurement().carrierCount();
		double stay = Math.max(0d, 1d - (double) delta / route.retentionLengthSeconds()) * measuredCarriers;
		double incoming = route.split()
				* estimatedFlightCarriers(inputs.arrivalStatus().items(), walkwayArrival);
		double inflowPerSecond = forecastInflowPerSecond(inputs.passengerForecast().items(), walkwayArrival);
		double residual = route.split() * inflowPerSecond * delta;
		double load = stay + incoming + residual;
		double volumeCapacityRatio = load / CAPACITY;
		MovingWalkwayStatus status = MovingWalkwayStatus.fromVolumeCapacityRatio(volumeCapacityRatio);
		long passageTime = status == MovingWalkwayStatus.CONGESTED
				? route.congestedPassageTimeSeconds()
				: route.availablePassageTimeSeconds();
		long totalTravelTime = Math.addExact(
				Math.addExact(delta, passageTime),
				route.remainingTravelTimeSeconds()
		);

		return new RouteCongestionResult(
				route.route(),
				walkwayArrival,
				stay,
				incoming,
				residual,
				load,
				volumeCapacityRatio,
				status,
				passageTime,
				totalTravelTime
		);
	}

	private double estimatedFlightCarriers(List<ArrivalStatusItem> items, Instant walkwayArrival) {
		HashSet<String> flightIds = new HashSet<>();
		long passengers = items.stream()
				.filter(item -> appliesAt(item.effectiveArrivalTime(), walkwayArrival))
				.filter(item -> flightIds.add(item.flightId()))
				.mapToLong(item -> Math.addExact(item.koreanPassengerCount(), item.foreignPassengerCount()))
				.sum();
		return passengers * carriersPerPassenger;
	}

	private boolean appliesAt(String airportDateTime, Instant target) {
		Instant arrival = parseAirportTime(airportDateTime);
		if (arrival == null) {
			return false;
		}
		ZonedDateTime localArrival = arrival.atZone(AIRPORT_ZONE);
		ZonedDateTime localTarget = target.atZone(AIRPORT_ZONE);
		return localArrival.toLocalDate().equals(localTarget.toLocalDate())
				&& localArrival.getHour() == localTarget.getHour();
	}

	private double forecastInflowPerSecond(List<PassengerForecastItem> items, Instant walkwayArrival) {
		long passengers = items.stream()
				.filter(item -> appliesAt(item, walkwayArrival))
				.mapToLong(PassengerForecastItem::expectedPassengerCount)
				.sum();
		return passengers * carriersPerPassenger / FORECAST_WINDOW_SECONDS;
	}

	private boolean appliesAt(PassengerForecastItem item, Instant target) {
		ZonedDateTime localTarget = target.atZone(AIRPORT_ZONE);
		String expectedDate = localTarget.toLocalDate().format(DateTimeFormatter.BASIC_ISO_DATE);
		if (item.date() != null && !digits(item.date()).equals(expectedDate)) {
			return false;
		}
		if (item.timeSlot() == null || item.timeSlot().isBlank()) {
			return true;
		}
		if (item.timeSlot().contains("_")) {
			String[] range = item.timeSlot().split("_", -1);
			String startHour = range.length == 2 ? digits(range[0]) : "";
			return startHour.length() == 2 && localTarget.getHour() == Integer.parseInt(startHour);
		}
		String timeDigits = digits(item.timeSlot());
		if (timeDigits.length() >= 4) {
			int hour = Integer.parseInt(timeDigits.substring(0, 2));
			int minute = Integer.parseInt(timeDigits.substring(2, 4));
			return localTarget.getHour() == hour && localTarget.getMinute() >= minute;
		}
		if (timeDigits.length() == 2) {
			return localTarget.getHour() == Integer.parseInt(timeDigits);
		}
		return false;
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

	private static MovingWalkwayRouteDefinition requireRoute(
			MovingWalkwayRouteDefinition route,
			AirportRoute expected
	) {
		Objects.requireNonNull(route, "route");
		if (route.route() != expected) {
			throw new IllegalArgumentException("Expected route " + expected);
		}
		return route;
	}
}
