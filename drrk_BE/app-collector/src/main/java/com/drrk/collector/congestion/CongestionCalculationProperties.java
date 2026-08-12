package com.drrk.collector.congestion;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "congestion.calculation")
public class CongestionCalculationProperties {

	private Duration apiMaxAge = Duration.ofMinutes(10);
	private Duration modelMaxAge = Duration.ofSeconds(20);

	public Duration getApiMaxAge() {
		return apiMaxAge;
	}

	public void setApiMaxAge(Duration apiMaxAge) {
		this.apiMaxAge = apiMaxAge;
	}

	public Duration getModelMaxAge() {
		return modelMaxAge;
	}

	public void setModelMaxAge(Duration modelMaxAge) {
		this.modelMaxAge = modelMaxAge;
	}
}
