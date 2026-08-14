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
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 승강장 혼잡도 공식 v2.
 *
 * <p>C(t) = min(1, L(t) / L_cap). L(t)는 누적 창 (T_prev, T_next] — 직전 열차 출발부터
 * 다음 열차 도착까지 — 동안 승강장에 누적될 철도행 수하물량이다. 승강장 도착 시각 τ에 대해
 * 센서 통과 시각은 τ − w 이므로:</p>
 *
 * <ul>
 *   <li>실측층: 센서 통과 시각 s ∈ (T_prev − w, min(now, T_next − w)] 의 계측 합</li>
 *   <li>예보층: s ∈ (now, T_next − w] 구간에 대해 α(h)·Σ_i B_i·φ_i 로 채움.
 *       B_i = k_i·r_K·c_K + f_i·r_F·c_F, φ_i는 편 i 도착 후 [exitDelayMin, exitDelayMax]
 *       구간의 균등분포, α(h) = t1eg1(h) / Σ_i (k_i + f_i)·Φ_i(h)</li>
 * </ul>
 *
 * <p>열차 스케줄이 없어 T_prev 또는 T_next를 정의할 수 없으면 NO_SERVICE(혼잡도 미산출),
 * 예보층이 필요한데 사용 가능한 항공편 데이터가 없으면 NO_FLIGHT_DATA(실측층만 산출)이다.</p>
 */
public class PlatformCongestionCalculator implements CongestionCalculator {

	private static final Logger log = LoggerFactory.getLogger(PlatformCongestionCalculator.class);

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
		List<RailroadOperationItem> railroadItems = inputs.railroadOperation().items();
		Optional<Instant> nextArrival = nextArrivalAfterNow(railroadItems, now);
		if (nextArrival.isEmpty()) {
			// 다음 열차가 없다 = 막차 이후~첫차 이전이거나 스케줄 자체가 없다 → 혼잡도 미산출
			log.info("[CONGESTION NO_SERVICE] reason=NO_UPCOMING_TRAIN railroadItemCount={}", railroadItems.size());
			return CongestionCalculatedMessage.noService(messageIdSupplier.get(), now, inputReferences(inputs));
		}

		Instant windowEnd = nextArrival.orElseThrow();
		WindowStart resolvedStart = resolveWindowStart(railroadItems, now, windowEnd);
		Instant windowStart = resolvedStart.value();
		Duration walk = Duration.ofMinutes(properties.getWalkMinutes());
		Instant sensorWindowStart = windowStart.minus(walk);
		Instant sensorWindowEnd = windowEnd.minus(walk);

		double measuredLoad = measuredLoad(inputs.modelMeasurements(), sensorWindowStart, minOf(now, sensorWindowEnd));

		double forecastLoad = 0d;
		boolean forecastRequired = sensorWindowEnd.isAfter(now);
		boolean flightDataMissing = false;
		if (forecastRequired) {
			List<FlightForecast> flights = usableFlights(inputs.arrivalStatus().items());
			if (flights.isEmpty()) {
				flightDataMissing = true;
			} else {
				forecastLoad = forecastLoad(flights, inputs.passengerForecast().items(), now, sensorWindowEnd);
			}
		}

		List<RailroadArrivalResult> railroadArrivals = mapRailroadArrivals(railroadItems, now);
		CongestionInputReferences references = inputReferences(inputs);
		log.info("[CONGESTION WINDOW] windowStart={} source={} windowEnd={} measuredLoad={} forecastLoad={} "
						+ "forecastRequired={} flightCount={}",
				windowStart, resolvedStart.source(), windowEnd, measuredLoad, forecastLoad,
				forecastRequired, inputs.arrivalStatus().items().size());
		if (flightDataMissing) {
			return CongestionCalculatedMessage.noFlightData(
					messageIdSupplier.get(),
					now,
					CongestionCalculatedMessage.CALCULATION_VERSION_V2,
					measuredLoad > 0,
					measuredLoad,
					properties.getTrainCapacity(),
					forecastLoad,
					windowStart,
					railroadArrivals,
					references
			);
		}
		return CongestionCalculatedMessage.calculated(
				messageIdSupplier.get(),
				now,
				CongestionCalculatedMessage.CALCULATION_VERSION_V2,
				measuredLoad > 0,
				measuredLoad,
				properties.getTrainCapacity(),
				forecastLoad,
				windowStart,
				railroadArrivals,
				references
		);
	}

	/**
	 * 실측층: 센서 통과 시각 s ∈ (windowStart, windowEnd] 의 캐리어 계측 합.
	 */
	private double measuredLoad(
			List<ModelMeasurementSnapshot> measurements,
			Instant windowStart,
			Instant windowEnd
	) {
		if (!windowEnd.isAfter(windowStart)) {
			return 0d;
		}
		return measurements.stream()
				.filter(snapshot -> snapshot.measuredAt().isAfter(windowStart))
				.filter(snapshot -> !snapshot.measuredAt().isAfter(windowEnd))
				.mapToLong(ModelMeasurementSnapshot::carrierCount)
				.sum();
	}

	/**
	 * 예보층: 센서 통과 시각 s ∈ (forecastStart, forecastEnd] 구간을 시간대(h) 단위로
	 * 나눠 α(h)·Σ_i B_i·mass_i(h ∩ S) 를 합산한다.
	 */
	private double forecastLoad(
			List<FlightForecast> flights,
			List<PassengerForecastItem> forecastItems,
			Instant forecastStart,
			Instant forecastEnd
	) {
		if (!forecastEnd.isAfter(forecastStart)) {
			return 0d;
		}
		double total = 0d;
		Instant hourCursor = forecastStart.atZone(AIRPORT_ZONE).truncatedTo(ChronoUnit.HOURS).toInstant();
		while (hourCursor.isBefore(forecastEnd)) {
			Instant hourEnd = hourCursor.plus(Duration.ofHours(1));
			Instant overlapStart = maxOf(hourCursor, forecastStart);
			Instant overlapEnd = minOf(hourEnd, forecastEnd);
			if (overlapEnd.isAfter(overlapStart)) {
				double alpha = alphaFor(hourCursor, hourEnd, flights, forecastItems);
				double hourLoad = flights.stream()
						.mapToDouble(flight -> flight.expectedBaggage()
								* flight.exitMassBetween(overlapStart, overlapEnd))
						.sum();
				total += alpha * hourLoad;
			}
			hourCursor = hourEnd;
		}
		return total;
	}

	/**
	 * α(h) = t1eg1(h) / Σ_i (k_i + f_i)·Φ_i(h). 분모·분자 모두 "시간대 h에 출구를
	 * 통과하는 인원" 기준으로 시간축을 맞춘다. 자료가 없으면 보정 없이 1을 쓴다.
	 */
	private double alphaFor(
			Instant hourStart,
			Instant hourEnd,
			List<FlightForecast> flights,
			List<PassengerForecastItem> forecastItems
	) {
		String slot = slotOf(hourStart);
		int forecastTotal = forecastItems.stream()
				.filter(item -> slot.equals(item.timeSlot()))
				.mapToInt(PassengerForecastItem::expectedPassengerCount)
				.findFirst()
				.orElse(0);
		double expectedExitTotal = flights.stream()
				.mapToDouble(flight -> flight.totalPassengers() * flight.exitMassBetween(hourStart, hourEnd))
				.sum();
		if (forecastTotal <= 0 || expectedExitTotal <= 0d) {
			return 1d;
		}
		return forecastTotal / expectedExitTotal;
	}

	private List<FlightForecast> usableFlights(List<ArrivalStatusItem> arrivals) {
		Duration exitDelayMin = Duration.ofMinutes(properties.getExitDelayMinMinutes());
		Duration exitDelayMax = Duration.ofMinutes(properties.getExitDelayMaxMinutes());
		return arrivals.stream()
				.map(arrival -> parseAirportTime(arrival.effectiveArrivalTime())
						.map(arrivalTime -> new FlightForecast(
								arrival.koreanPassengerCount(),
								arrival.foreignPassengerCount(),
								arrival.koreanPassengerCount() * properties.getRK() * properties.getCK()
										+ arrival.foreignPassengerCount() * properties.getRF() * properties.getCF(),
								arrivalTime.plus(exitDelayMin),
								arrivalTime.plus(exitDelayMax)
						))
						.orElse(null))
				.filter(Objects::nonNull)
				.toList();
	}

	/**
	 * T_prev(승강장이 마지막으로 비워진 시점) 결정. 운행정보 API가 출발시각을 비워 보내는
	 * 경우가 있어 3단 폴백을 둔다 — 이 값이 없다고 혼잡도를 통째로 버리지 않기 위함이다.
	 */
	private WindowStart resolveWindowStart(List<RailroadOperationItem> items, Instant now, Instant windowEnd) {
		Optional<Instant> departure = items.stream()
				.map(this::effectiveDepartureTime)
				.flatMap(Optional::stream)
				.filter(value -> !value.isAfter(now))
				.max(Comparator.naturalOrder());
		if (departure.isPresent()) {
			return new WindowStart(departure.orElseThrow(), "DEPARTURE");
		}

		Optional<Instant> pastArrival = items.stream()
				.map(item -> parseAirportTime(item.scheduledArrivalTime()))
				.flatMap(Optional::stream)
				.filter(value -> !value.isAfter(now))
				.max(Comparator.naturalOrder());
		if (pastArrival.isPresent()) {
			return new WindowStart(pastArrival.orElseThrow(), "PAST_ARRIVAL");
		}

		Instant headwayStart = windowEnd.minus(Duration.ofMinutes(properties.getDefaultHeadwayMinutes()));
		return new WindowStart(minOf(headwayStart, now), "HEADWAY_FALLBACK");
	}

	private Optional<Instant> nextArrivalAfterNow(List<RailroadOperationItem> items, Instant now) {
		return items.stream()
				.map(item -> parseAirportTime(item.scheduledArrivalTime()))
				.flatMap(Optional::stream)
				.filter(arrival -> arrival.isAfter(now))
				.min(Comparator.naturalOrder());
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
		ZonedDateTime zoned = instant.atZone(AIRPORT_ZONE);
		return String.format("%02d_%02d", zoned.getHour(), (zoned.getHour() + 1) % 24);
	}

	private static Instant minOf(Instant left, Instant right) {
		return left.isBefore(right) ? left : right;
	}

	private static Instant maxOf(Instant left, Instant right) {
		return left.isAfter(right) ? left : right;
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

	/**
	 * 누적 창 시작점과 그 근거 (진단 로그용).
	 */
	private record WindowStart(Instant value, String source) {
	}

	/**
	 * 편 i의 예보: 총 승객 수, 기대 수하물량 B_i, 출구 통과(=센서 통과) 균등분포 구간.
	 */
	private record FlightForecast(
			int koreanPassengers,
			int foreignPassengers,
			double expectedBaggage,
			Instant exitStart,
			Instant exitEnd
	) {

		int totalPassengers() {
			return koreanPassengers + foreignPassengers;
		}

		/**
		 * φ_i 균등분포에서 [from, to) 구간이 차지하는 질량 (0~1).
		 */
		double exitMassBetween(Instant from, Instant to) {
			Instant overlapStart = exitStart.isAfter(from) ? exitStart : from;
			Instant overlapEnd = exitEnd.isBefore(to) ? exitEnd : to;
			if (!overlapEnd.isAfter(overlapStart)) {
				return 0d;
			}
			long totalSeconds = Duration.between(exitStart, exitEnd).getSeconds();
			if (totalSeconds <= 0) {
				return 0d;
			}
			return (double) Duration.between(overlapStart, overlapEnd).getSeconds() / totalSeconds;
		}
	}
}
