package com.drrk.collector.client.airport;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "airport.api")
@Validated
public record AirportApiProperties(
		@Min(1) int numOfRows,
		@Valid Source arrivalStatus,
		@Valid Source passengerForecast,
		@Valid RailroadSource railroad
) {

	public record Source(@NotBlank String url, @NotBlank String key) {
	}

	public record RailroadSource(
			@NotBlank String url,
			@NotBlank String key,
			String trainClass,
			String stationCode
	) {
	}
}
