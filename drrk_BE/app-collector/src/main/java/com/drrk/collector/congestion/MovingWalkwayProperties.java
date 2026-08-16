package com.drrk.collector.congestion;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "congestion.moving-walkway")
public class MovingWalkwayProperties {

	private boolean enabled;
	private String sensorSpaceId;

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public String getSensorSpaceId() {
		return sensorSpaceId;
	}

	public void setSensorSpaceId(String sensorSpaceId) {
		this.sensorSpaceId = sensorSpaceId;
	}

	String requiredSensorSpaceId() {
		if (sensorSpaceId == null || sensorSpaceId.isBlank()) {
			throw new IllegalStateException("congestion.moving-walkway.sensor-space-id is required");
		}
		return sensorSpaceId.trim();
	}
}
