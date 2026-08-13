package com.drrk.collector.congestion;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class CongestionCalculationPropertiesTest {

	@Test
	void defaultsModelMeasurementFreshnessWindowToFiveSeconds() {
		CongestionCalculationProperties properties = new CongestionCalculationProperties();

		assertEquals(Duration.ofSeconds(5), properties.getModelMaxAge());
	}
}
