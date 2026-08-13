package com.drrk.collector.congestion;

import com.drrk.messaging.congestion.CongestionCalculatedMessage;
import com.drrk.messaging.congestion.CongestionInputReferences;
import com.drrk.messaging.congestion.RailroadArrivalResult;
import com.drrk.messaging.congestion.RailroadArrivalStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public class PlatformCongestionCalculator implements CongestionCalculator {

	private static final ZoneId AIRPORT_ZONE = ZoneId.of("Asia/Seoul");
	private static final DateTimeFormatter MINUTE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
	private static final DateTimeFormatter SECOND_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
	private static final DateTimeFormatter DISPLAY_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

	private final Clock clock;
	private final Supplier<UUID> messageIdSupplier;
	private final CongestionCalculationProperties properties;

	public PlatformCongestionCalculator(
			Clock clock,
			Supplier<UUID> messageIdSupplier,
			CongestionCalculationProperties properties
	) {
		this.clock = Objects.requireNonNull(clock, "clock");
		this.messageIdSupplier = Objects.requireNonNull(messageIdSupplier, "messageIdSupplier");
		this.properties = Objects.requireNonNull(properties, "properties");
	}

	@Override
	public CongestionCalculatedMessage calculate(CongestionInputs inputs) {
		Objects.requireNonNull(inputs, "inputs");
		Instant now = clock.instant();
		Optional<Instant> lastDeparture = lastDepartureBeforeNow(inputs.railroadOperation().items(), now);
		if (lastDeparture.isEmpty()) {
			return CongestionCalculatedMessage.formulaPending(messageIdSupplier.get(), now, inputReferences(inputs));
		}

		double currentLoad = currentMeasuredLoad(inputs.modelMeasurements(), lastDeparture.orElseThrow(), now);
		double forecastLoad = forecastLoad(
				inputs.arrivalStatus().items(),
				inputs.passengerForecast().items(),
				now,
				now.plus(Duration.ofMinutes(properties.getForecastLeadMinutes()))
		);
		return CongestionCalculatedMessage.calculated(
				messageIdSupplier.get(),
				now,
				"platform-congestion-v1",
				currentLoad > 0,
				currentLoad,
				properties.getTrainCapacity(),
				forecastLoad,
				lastDeparture.orElseThrow(),
				mapRailroadArrivals(inputs.railroadOperation().items(), now),
				inputReferences(inputs)
		);
	}

	private double currentMeasuredLoad(
			List<ModelMeasurementSnapshot> measurements,
			Instant lastDeparture,
			Instant now
	) {
		Duration walkDuration = Duration.ofMinutes(properties.getWalkMinutes());
		Instant sensorWindowStart = lastDeparture.minus(walkDuration);
		Instant sensorWindowEnd = now.minus(walkDuration);
		return measurements.stream()
				.filter(snapshot -> !snapshot.measuredAt().isBefore(sensorWindowStart))
				.filter(snapshot -> !snapshot.measuredAt().isAfter(sensorWindowEnd))
				.mapToLong(ModelMeasurementSnapshot::carrierCount)
				.sum();
	}

	private double forecastLoad(
			List<ArrivalStatusItem> arrivals,
			List<PassengerForecastItem> forecastItems,
			Instant forecastStart,
			Instant forecastEnd
	) {
		return arrivals.stream()
				.mapToDouble(arrival -> adjustedExpectedBaggage(arrival, arrivals, forecastItems)
						* overlapRatio(arrival, forecastStart, forecastEnd))
				.sum();
	}

	private double adjustedExpectedBaggage(
			ArrivalStatusItem arrival,
			List<ArrivalStatusItem> arrivals,
			List<PassengerForecastItem> forecastItems
	) {
		double base = arrival.koreanPassengerCount() * properties.getRK() * properties.getCK()
				+ arrival.foreignPassengerCount() * properties.getRF() * properties.getCF();
		Optional<Instant> arrivalTime = parseAirportTime(arrival.effectiveArrivalTime());
		if (arrivalTime.isEmpty()) {
			return 0d;
		}
		String slot = slotOf(arrivalTime.orElseThrow());
		int forecastTotal = forecastItems.stream()
				.filter(item -> slot.equals(item.timeSlot()))
				.mapToInt(PassengerForecastItem::expectedPassengerCount)
				.findFirst()
				.orElse(0);
		int slotPassengerTotal = arrivals.stream()
				.filter(item -> parseAirportTime(item.effectiveArrivalTime())
						.map(this::slotOf)
						.filter(slot::equals)
						.isPresent())
				.mapToInt(item -> item.koreanPassengerCount() + item.foreignPassengerCount())
				.sum();
		double alpha = forecastTotal > 0 && slotPassengerTotal > 0
				? (double) forecastTotal / slotPassengerTotal
				: 1d;
		return base * alpha;
	}

	private double overlapRatio(ArrivalStatusItem arrival, Instant forecastStart, Instant forecastEnd) {
		Optional<Instant> arrivalTime = parseAirportTime(arrival.effectiveArrivalTime());
		if (arrivalTime.isEmpty()) {
			return 0d;
		}
		Instant windowStart = arrivalTime.orElseThrow();
		Instant windowEnd = windowStart.plus(Duration.ofMinutes(properties.getForecastDistributionMinutes()));
		Instant overlapStart = windowStart.isAfter(forecastStart) ? windowStart : forecastStart;
		Instant overlapEnd = windowEnd.isBefore(forecastEnd) ? windowEnd : forecastEnd;
		if (!overlapEnd.isAfter(overlapStart)) {
			return 0d;
		}
		long totalSeconds = Duration.between(windowStart, windowEnd).getSeconds();
		long overlapSeconds = Duration.between(overlapStart, overlapEnd).getSeconds();
		return totalSeconds <= 0 ? 0d : (double) overlapSeconds / totalSeconds;
	}

	private Optional<Instant> lastDepartureBeforeNow(List<RailroadOperationItem> items, Instant now) {
		return items.stream()
				.map(this::effectiveDepartureTime)
				.flatMap(Optional::stream)
				.filter(departure -> !departure.isAfter(now))
				.max(Comparator.naturalOrder());
	}

	private Optional<Instant> effectiveDepartureTime(RailroadOperationItem item) {
		Optional<Instant> actual = parseAirportTime(item.actualDepartureTime());
		return actual.isPresent() ? actual : parseAirportTime(item.plannedDepartureTime());
	}

	private List<RailroadArrivalResult> mapRailroadArrivals(List<RailroadOperationItem> items, Instant calculatedAt) {
		return items.stream()
				.sorted(Comparator.comparing(
						item -> parseAirportTime(item.scheduledArrivalTime()).orElse(Instant.MAX)
				))
				.map(item -> toRailroadArrival(item, calculatedAt))
				.filter(Objects::nonNull)
				.toList();
	}

	private RailroadArrivalResult toRailroadArrival(RailroadOperationItem item, Instant calculatedAt) {
		Optional<Instant> scheduled = parseAirportTime(item.scheduledArrivalTime());
		if (scheduled.isEmpty() || item.trainNumber() == null || item.trainNumber().isBlank()
				|| item.trainType() == null || item.trainType().isBlank()) {
			return null;
		}
		Optional<Instant> actual = parseAirportTime(item.actualArrivalTime());
		RailroadArrivalStatus status;
		if (actual.isPresent()) {
			status = RailroadArrivalStatus.ARRIVED;
		} else if (calculatedAt.isAfter(scheduled.orElseThrow())) {
			status = RailroadArrivalStatus.DELAYED;
		} else {
			status = RailroadArrivalStatus.SCHEDULED;
		}
		return new RailroadArrivalResult(
				item.trainNumber(),
				item.trainType(),
				formatAirportTime(scheduled.orElseThrow()),
				actual.map(this::formatAirportTime).orElse(null),
				status
		);
	}

	private Optional<Instant> parseAirportTime(String value) {
		if (value == null || value.isBlank()) {
			return Optional.empty();
		}
		try {
			DateTimeFormatter formatter = value.length() == 14 ? SECOND_FORMAT : MINUTE_FORMAT;
			return Optional.of(LocalDateTime.parse(value, formatter).atZone(AIRPORT_ZONE).toInstant());
		} catch (DateTimeParseException exception) {
			return Optional.empty();
		}
	}

	private String formatAirportTime(Instant instant) {
		return instant.atZone(AIRPORT_ZONE).format(DISPLAY_TIME_FORMAT);
	}

	private String slotOf(Instant instant) {
		LocalDateTime localDateTime = LocalDateTime.ofInstant(instant, AIRPORT_ZONE);
		return String.format("%02d_%02d", localDateTime.getHour(), (localDateTime.getHour() + 1) % 24);
	}

	private CongestionInputReferences inputReferences(CongestionInputs inputs) {
		ModelMeasurementSnapshot latestMeasurement = inputs.modelMeasurements().get(inputs.modelMeasurements().size() - 1);
		return new CongestionInputReferences(
				inputs.arrivalStatus().collectedAt(),
				inputs.arrivalStatus().items().size(),
				inputs.passengerForecast().collectedAt(),
				inputs.passengerForecast().items().size(),
				inputs.railroadOperation().collectedAt(),
				inputs.railroadOperation().items().size(),
				latestMeasurement.messageId(),
				latestMeasurement.measuredAt()
		);
	}
}
