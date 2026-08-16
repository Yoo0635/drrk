package com.drrk.collector.congestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.drrk.messaging.congestion.CongestionCalculatedMessage;
import com.drrk.messaging.congestion.CongestionCalculationStatus;
import com.drrk.messaging.congestion.RailroadArrivalStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * v2 공식: 누적 창 (T_prev, T_next], 실측층 + 예보층(α·B·φ), C = min(1, L / L_cap).
 * 모든 시각은 KST(UTC+9) 2026-08-13 기준. NOW = 14:00 KST = 05:00Z.
 */
class PlatformCongestionCalculatorTest {

	private static final Instant NOW = Instant.parse("2026-08-13T05:00:00Z");
	private static final UUID MESSAGE_ID = UUID.fromString("35c9ef91-9f68-4fda-833f-90fa54c25816");

	@Test
	void coversWholeWindowWithMeasurementsWhenNextTrainArrivesWithinWalkTime() {
		// T_prev = 13:55, T_next = 14:05, w = 10분 → 센서 창 (13:45, 13:55], 예보층 불필요
		CongestionCalculatedMessage result = calculator().calculate(new CongestionInputs(
				new ArrivalStatusSnapshot(NOW.minusSeconds(60), List.of(
						new ArrivalStatusItem("B", "KE001", "202608131310", 120, 46)
				)),
				new PassengerForecastSnapshot(NOW.minusSeconds(60), List.of(
						new PassengerForecastItem("20260813", "14_15", 300)
				)),
				new RailroadOperationSnapshot(NOW.minusSeconds(60), List.of(
						new RailroadOperationItem("A0900", "110", "20260813135500", null, "20260813135500", "20260813135500", "일반"),
						new RailroadOperationItem("A0901", "110", "20260813140500", null, "20260813140500", null, "일반")
				)),
				List.of(
						new ModelMeasurementSnapshot("m1", Instant.parse("2026-08-13T04:46:00Z"), "desk01", 60, 5),
						new ModelMeasurementSnapshot("m2", Instant.parse("2026-08-13T04:49:00Z"), "desk01", 60, 7),
						new ModelMeasurementSnapshot("m3", Instant.parse("2026-08-13T04:53:00Z"), "desk01", 60, 11)
				)
		));

		assertEquals(CongestionCalculationStatus.CALCULATED, result.status());
		assertTrue(result.sensorDetected());
		assertEquals(23.0, result.currentLoad());
		assertEquals(0.0, result.forecastLoad(), 1.0e-9);
		assertEquals(48L, result.capacity());
		assertEquals(23.0 / 48.0, result.score(), 1.0e-9);
		assertEquals(result.score(), result.projectedScore(), 1.0e-9);
		assertEquals("MEDIUM", result.level());
		assertEquals(Instant.parse("2026-08-13T04:55:00Z"), result.lastTrainDepartureAt());
		assertEquals(List.of("A0900", "A0901"), result.railroadArrivals().stream()
				.map(arrival -> arrival.trainNo())
				.toList());
		assertEquals(RailroadArrivalStatus.DELAYED, result.railroadArrivals().get(0).status());
		assertEquals(RailroadArrivalStatus.SCHEDULED, result.railroadArrivals().get(1).status());
	}

	@Test
	void fillsUnobservedTailOfWindowWithFlightForecastLayer() {
		// T_prev = 13:55, T_next = 14:20, w = 10분 → 실측 (13:45, 14:00], 예보 (14:00, 14:10]
		// FRA 편(내 120/외 46, 13:10 도착): B = 120·0.09·0.75 + 46·0.22·0.95 = 17.714
		// 출구 분포 13:55~14:40 균등 → S 질량 10/45, 14시대 질량 40/45
		// α(14_15) = 300 / (166 · 40/45), forecast = α · 17.714 · (10/45)
		CongestionCalculatedMessage result = calculator().calculate(new CongestionInputs(
				new ArrivalStatusSnapshot(NOW.minusSeconds(60), List.of(
						new ArrivalStatusItem("B", "FR123", "202608131310", 120, 46)
				)),
				new PassengerForecastSnapshot(NOW.minusSeconds(60), List.of(
						new PassengerForecastItem("20260813", "14_15", 300)
				)),
				new RailroadOperationSnapshot(NOW.minusSeconds(60), List.of(
						new RailroadOperationItem("A0900", "110", "20260813135500", null, "20260813135500", "20260813135500", "일반"),
						new RailroadOperationItem("A0901", "110", "20260813142000", null, "20260813142000", null, "일반")
				)),
				List.of(
						new ModelMeasurementSnapshot("m1", Instant.parse("2026-08-13T04:46:00Z"), "desk01", 60, 5),
						new ModelMeasurementSnapshot("m2", Instant.parse("2026-08-13T04:49:00Z"), "desk01", 60, 7),
						new ModelMeasurementSnapshot("m3", Instant.parse("2026-08-13T04:53:00Z"), "desk01", 60, 11)
				)
		));

		double expectedBaggage = 120 * 0.09 * 0.75 + 46 * 0.22 * 0.95;
		double alpha = 300.0 / (166.0 * (40.0 / 45.0));
		double expectedForecast = alpha * expectedBaggage * (10.0 / 45.0);

		assertEquals(CongestionCalculationStatus.CALCULATED, result.status());
		assertEquals(23.0, result.currentLoad());
		assertEquals(expectedForecast, result.forecastLoad(), 1.0e-9);
		assertEquals(Math.min(1.0, (23.0 + expectedForecast) / 48.0), result.score(), 1.0e-9);
		assertEquals(result.score(), result.projectedScore(), 1.0e-9);
	}

	@Test
	void fallsBackToDefaultHeadwayWhenApiOmitsDepartureTimes() {
		// 운행정보 API가 출발시각을 비워 보내도 혼잡도를 버리지 않는다:
		// T_prev를 T_next − 기본 배차 간격(15분)으로 근사 → 13:50, 센서 창 (13:40, 13:55]
		CongestionCalculatedMessage result = calculator().calculate(new CongestionInputs(
				new ArrivalStatusSnapshot(NOW.minusSeconds(60), List.of()),
				new PassengerForecastSnapshot(NOW.minusSeconds(60), List.of()),
				new RailroadOperationSnapshot(NOW.minusSeconds(60), List.of(
						new RailroadOperationItem("A0901", "110", "20260813140500", null, null, null, "일반")
				)),
				List.of(new ModelMeasurementSnapshot("m1", Instant.parse("2026-08-13T04:49:00Z"), "desk01", 60, 7))
		));

		assertEquals(CongestionCalculationStatus.CALCULATED, result.status());
		assertEquals("platform-congestion-v2", result.calculationVersion());
		assertEquals(7.0, result.currentLoad());
		assertEquals(Instant.parse("2026-08-13T04:50:00Z"), result.lastTrainDepartureAt());
		assertEquals(7.0 / 48.0, result.score(), 1.0e-9);
	}

	@Test
	void fallsBackToPastArrivalWhenOnlyArrivalTimesExist() {
		// 출발시각은 없지만 직전 열차 도착시각이 있으면 그 시점을 승강장이 비워진 시점으로 본다
		CongestionCalculatedMessage result = calculator().calculate(new CongestionInputs(
				new ArrivalStatusSnapshot(NOW.minusSeconds(60), List.of()),
				new PassengerForecastSnapshot(NOW.minusSeconds(60), List.of()),
				new RailroadOperationSnapshot(NOW.minusSeconds(60), List.of(
						new RailroadOperationItem("A0900", "110", "20260813135500", null, null, null, "일반"),
						new RailroadOperationItem("A0901", "110", "20260813140500", null, null, null, "일반")
				)),
				List.of(new ModelMeasurementSnapshot("m1", Instant.parse("2026-08-13T04:49:00Z"), "desk01", 60, 7))
		));

		assertEquals(CongestionCalculationStatus.CALCULATED, result.status());
		assertEquals(Instant.parse("2026-08-13T04:55:00Z"), result.lastTrainDepartureAt());
	}

	@Test
	void assumesPublishedHeadwayWhenScheduleApiIsUnavailable() {
		// 운행정보 API가 403/장애로 비어 있을 때: 공시 배차 간격 한 주기((now-15분, now])로 근사한다.
		// 센서 창 = (now-25분, now-10분] → 13:35~13:50 계측만 합산
		CongestionCalculatedMessage result = calculator().calculate(new CongestionInputs(
				new ArrivalStatusSnapshot(NOW.minusSeconds(60), List.of()),
				new PassengerForecastSnapshot(NOW.minusSeconds(60), List.of()),
				new RailroadOperationSnapshot(NOW.minusSeconds(60), List.of()),
				List.of(
						new ModelMeasurementSnapshot("m0", Instant.parse("2026-08-13T04:30:00Z"), "desk01", 60, 99),
						new ModelMeasurementSnapshot("m1", Instant.parse("2026-08-13T04:40:00Z"), "desk01", 60, 6),
						new ModelMeasurementSnapshot("m2", Instant.parse("2026-08-13T04:49:00Z"), "desk01", 60, 4),
						new ModelMeasurementSnapshot("m3", Instant.parse("2026-08-13T04:55:00Z"), "desk01", 60, 77)
				)
		));

		assertEquals(CongestionCalculationStatus.CALCULATED, result.status());
		assertEquals(10.0, result.currentLoad());
		assertEquals(Instant.parse("2026-08-13T04:45:00Z"), result.lastTrainDepartureAt());
		assertEquals(10.0 / 48.0, result.score(), 1.0e-9);
	}

	@Test
	void returnsNoServiceWhenScheduleExistsButNoTrainIsComing() {
		CongestionCalculationProperties strict = properties();
		strict.setAssumeHeadwayWhenScheduleUnavailable(false);
		PlatformCongestionCalculator strictCalculator = new PlatformCongestionCalculator(
				Clock.fixed(NOW, ZoneOffset.UTC), () -> MESSAGE_ID, strict);

		CongestionCalculatedMessage result = strictCalculator.calculate(new CongestionInputs(
				new ArrivalStatusSnapshot(NOW.minusSeconds(60), List.of()),
				new PassengerForecastSnapshot(NOW.minusSeconds(60), List.of()),
				new RailroadOperationSnapshot(NOW.minusSeconds(60), List.of()),
				List.of(new ModelMeasurementSnapshot("m1", Instant.parse("2026-08-13T04:49:00Z"), "desk01", 60, 7))
		));

		assertEquals(CongestionCalculationStatus.NO_SERVICE, result.status());
	}

	@Test
	void returnsNoServiceWhenNoUpcomingTrainExists() {
		// 막차 이후: 과거 열차만 존재 → T_next 미정의 → 혼잡도 미산출
		CongestionCalculatedMessage result = calculator().calculate(new CongestionInputs(
				new ArrivalStatusSnapshot(NOW.minusSeconds(60), List.of()),
				new PassengerForecastSnapshot(NOW.minusSeconds(60), List.of()),
				new RailroadOperationSnapshot(NOW.minusSeconds(60), List.of(
						new RailroadOperationItem("A0900", "110", "20260813135500", null, "20260813135500", "20260813135500", "일반")
				)),
				List.of(new ModelMeasurementSnapshot("m1", Instant.parse("2026-08-13T04:49:00Z"), "desk01", 60, 7))
		));

		assertEquals(CongestionCalculationStatus.NO_SERVICE, result.status());
	}

	@Test
	void returnsNoFlightDataWhenForecastNeededWithoutUsableFlights() {
		// 예보 구간 (14:00, 14:10] 필요하지만 항공편 없음 → 실측층만으로 산출
		CongestionCalculatedMessage result = calculator().calculate(new CongestionInputs(
				new ArrivalStatusSnapshot(NOW.minusSeconds(60), List.of()),
				new PassengerForecastSnapshot(NOW.minusSeconds(60), List.of()),
				new RailroadOperationSnapshot(NOW.minusSeconds(60), List.of(
						new RailroadOperationItem("A0900", "110", "20260813135500", null, "20260813135500", "20260813135500", "일반"),
						new RailroadOperationItem("A0901", "110", "20260813142000", null, "20260813142000", null, "일반")
				)),
				List.of(new ModelMeasurementSnapshot("m1", Instant.parse("2026-08-13T04:53:00Z"), "desk01", 60, 11))
		));

		assertEquals(CongestionCalculationStatus.NO_FLIGHT_DATA, result.status());
		assertEquals(11.0, result.currentLoad());
		assertEquals(0.0, result.forecastLoad(), 1.0e-9);
		assertEquals(11.0 / 48.0, result.score(), 1.0e-9);
		assertEquals("LOW", result.level());
	}

	@Test
	void matchesSpecSectionSixVerificationExample() {
		// 문서 6절: 배차 6분, w=8분, 창당 4대×6창=24대, L_cap=48 → C=0.50
		CongestionCalculationProperties properties = properties();
		properties.setWalkMinutes(8);
		PlatformCongestionCalculator calculator = new PlatformCongestionCalculator(
				Clock.fixed(Instant.parse("2026-08-13T01:03:00Z"), ZoneOffset.UTC), // 10:03 KST
				() -> MESSAGE_ID,
				properties
		);

		// T_prev = 10:00, T_next = 10:06 → 센서 창 (09:52, 09:58], 실측 확정 범위 내
		List<ModelMeasurementSnapshot> measurements = List.of(
				new ModelMeasurementSnapshot("m1", Instant.parse("2026-08-13T00:53:00Z"), "desk01", 60, 4),
				new ModelMeasurementSnapshot("m2", Instant.parse("2026-08-13T00:54:00Z"), "desk01", 60, 4),
				new ModelMeasurementSnapshot("m3", Instant.parse("2026-08-13T00:55:00Z"), "desk01", 60, 4),
				new ModelMeasurementSnapshot("m4", Instant.parse("2026-08-13T00:56:00Z"), "desk01", 60, 4),
				new ModelMeasurementSnapshot("m5", Instant.parse("2026-08-13T00:57:00Z"), "desk01", 60, 4),
				new ModelMeasurementSnapshot("m6", Instant.parse("2026-08-13T00:58:00Z"), "desk01", 60, 4)
		);

		CongestionCalculatedMessage result = calculator.calculate(new CongestionInputs(
				new ArrivalStatusSnapshot(Instant.parse("2026-08-13T01:02:00Z"), List.of(
						new ArrivalStatusItem("B", "KE001", "202608130910", 100, 50)
				)),
				new PassengerForecastSnapshot(Instant.parse("2026-08-13T01:02:00Z"), List.of()),
				new RailroadOperationSnapshot(Instant.parse("2026-08-13T01:02:00Z"), List.of(
						new RailroadOperationItem("A0900", "110", "20260813100000", null, "20260813100000", "20260813100000", "일반"),
						new RailroadOperationItem("A0901", "110", "20260813100600", null, "20260813100600", null, "일반")
				)),
				measurements
		));

		assertEquals(CongestionCalculationStatus.CALCULATED, result.status());
		assertEquals(24.0, result.currentLoad());
		assertEquals(0.0, result.forecastLoad(), 1.0e-9);
		assertEquals(0.5, result.score(), 1.0e-9);
		assertEquals("MEDIUM", result.level());
	}

	private PlatformCongestionCalculator calculator() {
		return new PlatformCongestionCalculator(
				Clock.fixed(NOW, ZoneOffset.UTC),
				() -> MESSAGE_ID,
				properties()
		);
	}

	private CongestionCalculationProperties properties() {
		CongestionCalculationProperties properties = new CongestionCalculationProperties();
		properties.setTrainCapacity(48);
		properties.setWalkMinutes(10);
		properties.setExitDelayMinMinutes(45);
		properties.setExitDelayMaxMinutes(90);
		properties.setRK(0.09);
		properties.setRF(0.22);
		properties.setCK(0.75);
		properties.setCF(0.95);
		return properties;
	}
}
