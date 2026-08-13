package com.drrk.messaging.congestion;

public enum MovingWalkwayStatus {
	AVAILABLE,
	NORMAL,
	CONGESTED;

	public static MovingWalkwayStatus fromVolumeCapacityRatio(double ratio) {
		if (!Double.isFinite(ratio) || ratio < 0) {
			throw new IllegalArgumentException("volumeCapacityRatio must be finite and non-negative");
		}
		if (ratio <= 0.7) {
			return AVAILABLE;
		}
		if (ratio <= 1.0) {
			return NORMAL;
		}
		return CONGESTED;
	}
}
