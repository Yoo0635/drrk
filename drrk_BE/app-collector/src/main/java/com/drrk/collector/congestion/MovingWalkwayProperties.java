package com.drrk.collector.congestion;

import com.drrk.messaging.congestion.AirportRoute;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "congestion.moving-walkway")
public class MovingWalkwayProperties {

	private boolean enabled;
	private String sensorSpaceId;
	private Double carriersPerPassenger;
	private final Route routeB = new Route();
	private final Route routeC = new Route();

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public Double getCarriersPerPassenger() {
		return carriersPerPassenger;
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

	public void setCarriersPerPassenger(Double carriersPerPassenger) {
		this.carriersPerPassenger = carriersPerPassenger;
	}

	public Route getRouteB() {
		return routeB;
	}

	public Route getRouteC() {
		return routeC;
	}

	double requiredCarriersPerPassenger() {
		if (carriersPerPassenger == null) {
			throw new IllegalStateException("congestion.moving-walkway.carriers-per-passenger is required");
		}
		return carriersPerPassenger;
	}

	MovingWalkwayRouteDefinition routeDefinition(AirportRoute airportRoute) {
		Route route = airportRoute == AirportRoute.B ? routeB : routeC;
		return route.toDefinition(airportRoute);
	}

	public static class Route {

		private Double split;
		private Long retentionLengthSeconds;
		private Long walkwayArrivalOffsetSeconds;
		private Long availablePassageTimeSeconds;
		private Long congestedPassageTimeSeconds;
		private Long remainingTravelTimeSeconds;

		public Double getSplit() {
			return split;
		}

		public void setSplit(Double split) {
			this.split = split;
		}

		public Long getRetentionLengthSeconds() {
			return retentionLengthSeconds;
		}

		public void setRetentionLengthSeconds(Long retentionLengthSeconds) {
			this.retentionLengthSeconds = retentionLengthSeconds;
		}

		public Long getWalkwayArrivalOffsetSeconds() {
			return walkwayArrivalOffsetSeconds;
		}

		public void setWalkwayArrivalOffsetSeconds(Long walkwayArrivalOffsetSeconds) {
			this.walkwayArrivalOffsetSeconds = walkwayArrivalOffsetSeconds;
		}

		public Long getAvailablePassageTimeSeconds() {
			return availablePassageTimeSeconds;
		}

		public void setAvailablePassageTimeSeconds(Long availablePassageTimeSeconds) {
			this.availablePassageTimeSeconds = availablePassageTimeSeconds;
		}

		public Long getCongestedPassageTimeSeconds() {
			return congestedPassageTimeSeconds;
		}

		public void setCongestedPassageTimeSeconds(Long congestedPassageTimeSeconds) {
			this.congestedPassageTimeSeconds = congestedPassageTimeSeconds;
		}

		public Long getRemainingTravelTimeSeconds() {
			return remainingTravelTimeSeconds;
		}

		public void setRemainingTravelTimeSeconds(Long remainingTravelTimeSeconds) {
			this.remainingTravelTimeSeconds = remainingTravelTimeSeconds;
		}

		private MovingWalkwayRouteDefinition toDefinition(AirportRoute route) {
			String prefix = "congestion.moving-walkway.route-" + route.name().toLowerCase();
			return new MovingWalkwayRouteDefinition(
					route,
					require(split, prefix + ".split"),
					require(retentionLengthSeconds, prefix + ".retention-length-seconds"),
					require(walkwayArrivalOffsetSeconds, prefix + ".walkway-arrival-offset-seconds"),
					require(availablePassageTimeSeconds, prefix + ".available-passage-time-seconds"),
					require(congestedPassageTimeSeconds, prefix + ".congested-passage-time-seconds"),
					require(remainingTravelTimeSeconds, prefix + ".remaining-travel-time-seconds")
			);
		}

		private static <T> T require(T value, String property) {
			if (value == null) {
				throw new IllegalStateException(property + " is required");
			}
			return value;
		}
	}
}
