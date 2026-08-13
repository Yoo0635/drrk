package com.drrk.messaging.congestion;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MovingWalkwayStatusTest {

	@ParameterizedTest
	@CsvSource({
			"0.0, AVAILABLE",
			"0.7, AVAILABLE",
			"0.7000000000000001, NORMAL",
			"1.0, NORMAL",
			"1.0000000000000002, CONGESTED"
	})
	void classifiesVolumeCapacityRatioAtEveryBoundary(double ratio, MovingWalkwayStatus expected) {
		assertEquals(expected, MovingWalkwayStatus.fromVolumeCapacityRatio(ratio));
	}
}
