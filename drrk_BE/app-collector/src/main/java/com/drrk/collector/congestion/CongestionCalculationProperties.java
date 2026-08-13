package com.drrk.collector.congestion;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "congestion.calculation")
public class CongestionCalculationProperties {

	private Duration apiMaxAge = Duration.ofMinutes(10);
	private Duration modelMaxAge = Duration.ofSeconds(5);
	private long trainCapacity = 48;
	private int walkMinutes = 10;
	private int exitDelayMinMinutes = 45;
	private int exitDelayMaxMinutes = 90;
	private double rK = 0.09;
	private double rF = 0.22;
	private double cK = 0.75;
	private double cF = 0.95;

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

	public long getTrainCapacity() {
		return trainCapacity;
	}

	public void setTrainCapacity(long trainCapacity) {
		this.trainCapacity = trainCapacity;
	}

	public int getWalkMinutes() {
		return walkMinutes;
	}

	public void setWalkMinutes(int walkMinutes) {
		this.walkMinutes = walkMinutes;
	}

	public int getExitDelayMinMinutes() {
		return exitDelayMinMinutes;
	}

	public void setExitDelayMinMinutes(int exitDelayMinMinutes) {
		this.exitDelayMinMinutes = exitDelayMinMinutes;
	}

	public int getExitDelayMaxMinutes() {
		return exitDelayMaxMinutes;
	}

	public void setExitDelayMaxMinutes(int exitDelayMaxMinutes) {
		this.exitDelayMaxMinutes = exitDelayMaxMinutes;
	}

	public double getRK() {
		return rK;
	}

	public void setRK(double rK) {
		this.rK = rK;
	}

	public double getRF() {
		return rF;
	}

	public void setRF(double rF) {
		this.rF = rF;
	}

	public double getCK() {
		return cK;
	}

	public void setCK(double cK) {
		this.cK = cK;
	}

	public double getCF() {
		return cF;
	}

	public void setCF(double cF) {
		this.cF = cF;
	}
}
