package com.drrk.collector.job;

import com.drrk.collector.client.airport.ArrivalStatusClient;
import com.drrk.collector.client.airport.PassengerForecastClient;
import com.drrk.collector.client.airport.RailroadOperationClient;
import com.drrk.collector.congestion.ArrivalStatusSnapshot;
import com.drrk.collector.congestion.LatestCongestionInputStore;
import com.drrk.collector.congestion.PassengerForecastSnapshot;
import com.drrk.collector.congestion.RailroadOperationSnapshot;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

public class AirportDataCollectionJob {

	private static final Logger log = LoggerFactory.getLogger(AirportDataCollectionJob.class);

	private final ArrivalStatusClient arrivalStatusClient;
	private final PassengerForecastClient passengerForecastClient;
	private final RailroadOperationClient railroadOperationClient;
	private final LatestCongestionInputStore store;

	public AirportDataCollectionJob(
			ArrivalStatusClient arrivalStatusClient,
			PassengerForecastClient passengerForecastClient,
			RailroadOperationClient railroadOperationClient,
			LatestCongestionInputStore store
	) {
		this.arrivalStatusClient = arrivalStatusClient;
		this.passengerForecastClient = passengerForecastClient;
		this.railroadOperationClient = railroadOperationClient;
		this.store = store;
	}

	@Scheduled(
			fixedDelayString = "${airport.collection.fixed-delay:PT5M}",
			initialDelayString = "${airport.collection.initial-delay:PT1S}"
	)
	public void collect() {
		String cycleId = UUID.randomUUID().toString();
		collectArrivalStatus(cycleId);
		collectPassengerForecast(cycleId);
		collectRailroadOperation(cycleId);
	}

	private void collectArrivalStatus(String cycleId) {
		try {
			ArrivalStatusSnapshot snapshot = arrivalStatusClient.fetch();
			store.replaceArrivalStatus(snapshot);
			log.info(
					"[SNAPSHOT REPLACED] cycleId={} source=ARRIVAL_STATUS collectedAt={} itemCount={}",
					cycleId,
					snapshot.collectedAt(),
					snapshot.items().size()
			);
		} catch (RuntimeException exception) {
			log.warn("[FETCH FAILED] cycleId={} source=ARRIVAL_STATUS errorType={}",
					cycleId, exception.getClass().getSimpleName());
		}
	}

	private void collectPassengerForecast(String cycleId) {
		try {
			PassengerForecastSnapshot snapshot = passengerForecastClient.fetch();
			store.replacePassengerForecast(snapshot);
			log.info(
					"[SNAPSHOT REPLACED] cycleId={} source=PASSENGER_FORECAST collectedAt={} itemCount={}",
					cycleId,
					snapshot.collectedAt(),
					snapshot.items().size()
			);
		} catch (RuntimeException exception) {
			log.warn("[FETCH FAILED] cycleId={} source=PASSENGER_FORECAST errorType={}",
					cycleId, exception.getClass().getSimpleName());
		}
	}

	private void collectRailroadOperation(String cycleId) {
		try {
			RailroadOperationSnapshot snapshot = railroadOperationClient.fetch();
			store.replaceRailroadOperation(snapshot);
			log.info(
					"[SNAPSHOT REPLACED] cycleId={} source=RAILROAD_OPERATION collectedAt={} itemCount={}",
					cycleId,
					snapshot.collectedAt(),
					snapshot.items().size()
			);
		} catch (RuntimeException exception) {
			log.warn("[FETCH FAILED] cycleId={} source=RAILROAD_OPERATION errorType={}",
					cycleId, exception.getClass().getSimpleName());
		}
	}
}
