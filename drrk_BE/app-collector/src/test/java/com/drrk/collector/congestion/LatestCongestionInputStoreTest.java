package com.drrk.collector.congestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class LatestCongestionInputStoreTest {

	@Test
	void replacesOnlyTheUpdatedApiSnapshot() {
		LatestCongestionInputStore store = new LatestCongestionInputStore();
		ArrivalStatusSnapshot first = new ArrivalStatusSnapshot(
				Instant.parse("2026-08-13T00:00:00Z"),
				List.of(new ArrivalStatusItem("KE001", "1200"))
		);
		PassengerForecastSnapshot passenger = new PassengerForecastSnapshot(
				Instant.parse("2026-08-13T00:00:01Z"),
				List.of(new PassengerForecastItem("1200-1300", 100))
		);
		ArrivalStatusSnapshot second = new ArrivalStatusSnapshot(
				Instant.parse("2026-08-13T00:05:00Z"),
				List.of(new ArrivalStatusItem("KE002", "1205"))
		);

		store.replaceArrivalStatus(first);
		store.replacePassengerForecast(passenger);
		store.replaceArrivalStatus(second);

		CongestionInputState state = store.snapshot();
		assertEquals(second, state.arrivalStatus());
		assertEquals(passenger, state.passengerForecast());
	}

	@Test
	void ignoresModelMeasurementThatIsNotNewer() {
		LatestCongestionInputStore store = new LatestCongestionInputStore();
		ModelMeasurementSnapshot latest = new ModelMeasurementSnapshot(
				"latest",
				Instant.parse("2026-08-13T00:00:10Z"),
				3,
				0.7
		);
		ModelMeasurementSnapshot older = new ModelMeasurementSnapshot(
				"older",
				Instant.parse("2026-08-13T00:00:00Z"),
				2,
				0.5
		);

		store.replaceModelIfNewer(latest);
		boolean replaced = store.replaceModelIfNewer(older);

		assertFalse(replaced);
		assertEquals(List.of(latest), store.snapshot().modelMeasurements());
	}

	@Test
	void exposesInputsOnlyWhenEverySnapshotIsFresh() {
		Instant now = Instant.parse("2026-08-13T00:10:00Z");
		LatestCongestionInputStore store = populatedStore(now);

		assertTrue(store.snapshot().freshInputs(now, Duration.ofMinutes(10), Duration.ofSeconds(10)).isPresent());

		Instant afterModelExpires = now.plusSeconds(11);
		assertTrue(
				store.snapshot()
						.freshInputs(afterModelExpires, Duration.ofMinutes(10), Duration.ofSeconds(10))
						.isEmpty()
		);
	}

	@Test
	void ignoresMeasurementsFromAnUnconfiguredSensorSpace() {
		LatestCongestionInputStore store = new LatestCongestionInputStore("desk01");
		ModelMeasurementSnapshot accepted = new ModelMeasurementSnapshot(
				"accepted", Instant.parse("2026-08-13T00:00:00Z"), "desk01", 10, 2);
		ModelMeasurementSnapshot other = new ModelMeasurementSnapshot(
				"other", Instant.parse("2026-08-13T00:00:01Z"), "desk02", 10, 9);

		assertTrue(store.replaceModelIfNewer(accepted));
		assertFalse(store.replaceModelIfNewer(other));
		assertEquals(List.of(accepted), store.snapshot().modelMeasurements());
	}

	@Test
	void keepsChronologicalSensorHistoryForRecentMeasurements() {
		LatestCongestionInputStore store = new LatestCongestionInputStore("desk01");
		ModelMeasurementSnapshot first = new ModelMeasurementSnapshot(
				"m1", Instant.parse("2026-08-13T00:00:00Z"), "desk01", 60, 1);
		ModelMeasurementSnapshot second = new ModelMeasurementSnapshot(
				"m2", Instant.parse("2026-08-13T00:01:00Z"), "desk01", 60, 2);

		assertTrue(store.replaceModelIfNewer(first));
		assertTrue(store.replaceModelIfNewer(second));

		assertEquals(List.of(first, second), store.snapshot().modelMeasurements());
	}

	private static LatestCongestionInputStore populatedStore(Instant now) {
		LatestCongestionInputStore store = new LatestCongestionInputStore();
		store.replaceArrivalStatus(new ArrivalStatusSnapshot(now, List.of()));
		store.replacePassengerForecast(new PassengerForecastSnapshot(now, List.of()));
		store.replaceRailroadOperation(new RailroadOperationSnapshot(now, List.of()));
		store.replaceModelIfNewer(new ModelMeasurementSnapshot("model-id", now, "", 60, 1));
		return store;
	}
}
