package com.drrk.collector.job;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.drrk.collector.congestion.ArrivalStatusItem;
import com.drrk.collector.congestion.ArrivalStatusSnapshot;
import com.drrk.collector.congestion.CongestionCalculator;
import com.drrk.collector.congestion.CongestionInputs;
import com.drrk.collector.congestion.LatestCongestionInputStore;
import com.drrk.collector.congestion.ModelMeasurementSnapshot;
import com.drrk.collector.congestion.PassengerForecastItem;
import com.drrk.collector.congestion.PassengerForecastSnapshot;
import com.drrk.collector.congestion.RailroadOperationItem;
import com.drrk.collector.congestion.RailroadOperationSnapshot;
import com.drrk.collector.publisher.congestion.CongestionMessagePublisher;
import com.drrk.messaging.congestion.CongestionCalculatedMessage;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class CongestionCalculationJobTest {

	private static final Instant NOW = Instant.parse("2026-08-13T03:00:00Z");

	private final LatestCongestionInputStore store = new LatestCongestionInputStore();
	private final CongestionCalculator calculator = Mockito.mock(CongestionCalculator.class);
	private final CongestionMessagePublisher publisher = Mockito.mock(CongestionMessagePublisher.class);
	private final CongestionCalculationJob job = new CongestionCalculationJob(
			store,
			calculator,
			publisher,
			Clock.fixed(NOW, ZoneOffset.UTC),
			Duration.ofMinutes(10),
			Duration.ofSeconds(10)
	);

	@Test
	void calculatesAndPublishesWhenEveryInputIsFresh() {
		storeFreshInputs();
		CongestionCalculatedMessage calculated = Mockito.mock(CongestionCalculatedMessage.class);
		when(calculator.calculate(Mockito.any())).thenReturn(calculated);

		job.calculateAndPublish();

		ArgumentCaptor<CongestionInputs> inputs = ArgumentCaptor.forClass(CongestionInputs.class);
		verify(calculator).calculate(inputs.capture());
		verify(publisher).publish(calculated);
	}

	@Test
	void skipsCalculationWhenAnyInputIsMissing() {
		job.calculateAndPublish();

		verify(calculator, never()).calculate(Mockito.any());
		verify(publisher, never()).publish(Mockito.any());
	}

	@Test
	void skipsCalculationWhenModelMeasurementIsStale() {
		storeAirportInputs();
		store.replaceModelIfNewer(new ModelMeasurementSnapshot("model-stale", NOW.minusSeconds(11), 1, 0.1));

		job.calculateAndPublish();

		verify(calculator, never()).calculate(Mockito.any());
		verify(publisher, never()).publish(Mockito.any());
	}

	@Test
	void logsCalculationFailureWithoutPublishing(CapturedOutput output) {
		storeFreshInputs();
		when(calculator.calculate(Mockito.any())).thenThrow(new IllegalStateException("formula failure"));

		job.calculateAndPublish();

		verify(publisher, never()).publish(Mockito.any());
		org.assertj.core.api.Assertions.assertThat(output).contains("[CALCULATION FAILED]");
	}

	@Test
	void logsPublishFailureWithoutEscapingSchedulerTick(CapturedOutput output) {
		storeFreshInputs();
		CongestionCalculatedMessage calculated = Mockito.mock(CongestionCalculatedMessage.class);
		when(calculator.calculate(Mockito.any())).thenReturn(calculated);
		Mockito.doThrow(new IllegalStateException("broker failure")).when(publisher).publish(calculated);

		job.calculateAndPublish();

		org.assertj.core.api.Assertions.assertThat(output).contains("[PUBLISH FAILED]");
	}

	private void storeFreshInputs() {
		storeAirportInputs();
		store.replaceModelIfNewer(new ModelMeasurementSnapshot("model-1", NOW.minusSeconds(10), 3, 0.42));
	}

	private void storeAirportInputs() {
		Instant collectedAt = NOW.minusSeconds(60);
		store.replaceArrivalStatus(new ArrivalStatusSnapshot(
				collectedAt,
				List.of(new ArrivalStatusItem("KE001", "1200"))
		));
		store.replacePassengerForecast(new PassengerForecastSnapshot(
				collectedAt,
				List.of(new PassengerForecastItem("1200", 300))
		));
		store.replaceRailroadOperation(new RailroadOperationSnapshot(
				collectedAt,
				List.of(new RailroadOperationItem("A001", "1155", "1200"))
		));
	}
}
