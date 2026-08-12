package com.drrk.collector.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.drrk.collector.client.airport.ArrivalStatusClient;
import com.drrk.collector.client.airport.PassengerForecastClient;
import com.drrk.collector.client.airport.RailroadOperationClient;
import com.drrk.collector.congestion.LatestCongestionInputStore;
import com.drrk.collector.congestion.PassengerForecastItem;
import com.drrk.collector.congestion.PassengerForecastSnapshot;
import com.drrk.collector.congestion.RailroadOperationItem;
import com.drrk.collector.congestion.RailroadOperationSnapshot;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class AirportDataCollectionJobTest {

	@Test
	void continuesCollectingOtherSourcesWhenOneSourceFails() {
		ArrivalStatusClient arrivalClient = mock(ArrivalStatusClient.class);
		PassengerForecastClient passengerClient = mock(PassengerForecastClient.class);
		RailroadOperationClient railroadClient = mock(RailroadOperationClient.class);
		LatestCongestionInputStore store = new LatestCongestionInputStore();
		PassengerForecastSnapshot passenger = new PassengerForecastSnapshot(
				Instant.parse("2026-08-13T00:00:00Z"),
				List.of(new PassengerForecastItem("00_01", 722))
		);
		RailroadOperationSnapshot railroad = new RailroadOperationSnapshot(
				Instant.parse("2026-08-13T00:00:01Z"),
				List.of(new RailroadOperationItem("A2002", "20260813050800", ""))
		);
		when(arrivalClient.fetch()).thenThrow(new IllegalStateException("upstream timeout"));
		when(passengerClient.fetch()).thenReturn(passenger);
		when(railroadClient.fetch()).thenReturn(railroad);
		AirportDataCollectionJob job = new AirportDataCollectionJob(
				arrivalClient,
				passengerClient,
				railroadClient,
				store
		);

		job.collect();

		assertNull(store.snapshot().arrivalStatus());
		assertEquals(passenger, store.snapshot().passengerForecast());
		assertEquals(railroad, store.snapshot().railroadOperation());
	}
}
