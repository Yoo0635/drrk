package com.drrk.main.consumer.congestion;

import com.drrk.messaging.congestion.CongestionCalculatedMessage;
import com.drrk.messaging.congestion.CongestionCalculationStatus;
import java.time.Instant;
import java.util.UUID;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

public class CongestionCalculatedMessageParser {

	private static final double EPSILON = 1.0e-9;

	private final ObjectMapper objectMapper;

	public CongestionCalculatedMessageParser(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public CongestionCalculatedMessage parse(String payload) {
		CongestionCalculatedMessage message;
		try {
			message = objectMapper.readValue(payload, CongestionCalculatedMessage.class);
		} catch (JacksonException | IllegalArgumentException | NullPointerException exception) {
			throw new InvalidCongestionMessageException("Invalid congestion JSON", exception);
		}
		if (message == null) {
			throw new InvalidCongestionMessageException("Congestion message must not be null");
		}
		validate(message);
		return message;
	}

	private void validate(CongestionCalculatedMessage message) {
		validateUuidV4(message.messageId(), "messageId");
		if (!"4.0".equals(message.schemaVersion())) {
			throw new InvalidCongestionMessageException("Unsupported schemaVersion");
		}
		if (message.status() == CongestionCalculationStatus.FORMULA_PENDING) {
			if (!"formula-pending-v1".equals(message.calculationVersion())
					|| message.score() != null
					|| message.level() != null
					|| message.currentLoad() != null
					|| message.capacity() != null
					|| message.forecastLoad() != null
					|| message.projectedScore() != null
					|| message.lastTrainDepartureAt() != null) {
				throw new InvalidCongestionMessageException("Invalid FORMULA_PENDING payload");
			}
		}
		if (message.status() == CongestionCalculationStatus.CALCULATED) {
			validateCalculated(message);
		}
		validateUuidV4(message.inputs().modelMessageId(), "inputs.modelMessageId");
	}

	private void validateCalculated(CongestionCalculatedMessage message) {
		if (message.calculationVersion() == null || message.calculationVersion().isBlank()) {
			throw new InvalidCongestionMessageException("Missing calculationVersion");
		}
		if (message.score() == null || message.currentLoad() == null || message.capacity() == null
				|| message.forecastLoad() == null || message.projectedScore() == null
				|| message.lastTrainDepartureAt() == null || !Double.isFinite(message.score())
				|| !Double.isFinite(message.currentLoad()) || !Double.isFinite(message.forecastLoad())
				|| !Double.isFinite(message.projectedScore()) || message.capacity() <= 0) {
			throw new InvalidCongestionMessageException("Invalid CALCULATED summary");
		}
		if (message.lastTrainDepartureAt().isAfter(message.calculatedAt())) {
			throw new InvalidCongestionMessageException("lastTrainDepartureAt must not be after calculatedAt");
		}
		if (!nearlyEqual(message.score(), clamp(message.currentLoad() / message.capacity()))) {
			throw new InvalidCongestionMessageException("Invalid CALCULATED summary");
		}
		if (!nearlyEqual(message.projectedScore(),
				clamp((message.currentLoad() + message.forecastLoad()) / message.capacity()))) {
			throw new InvalidCongestionMessageException("Invalid projectedScore");
		}
		String expectedLevel = levelFor(message.score());
		if (!expectedLevel.equals(message.level())) {
			throw new InvalidCongestionMessageException("level does not match score");
		}
	}

	private boolean nearlyEqual(double left, double right) {
		double scale = Math.max(1d, Math.max(Math.abs(left), Math.abs(right)));
		return Math.abs(left - right) <= EPSILON * scale;
	}

	private void validateUuidV4(String value, String field) {
		try {
			if (UUID.fromString(value).version() != 4) {
				throw new InvalidCongestionMessageException(field + " must be UUID v4");
			}
		} catch (IllegalArgumentException | NullPointerException exception) {
			throw new InvalidCongestionMessageException(field + " must be UUID v4", exception);
		}
	}

	private double clamp(double value) {
		return Math.min(1d, value);
	}

	private String levelFor(double score) {
		if (score >= 1d) {
			return "FULL";
		}
		if (score >= 0.7d) {
			return "HIGH";
		}
		if (score >= 0.4d) {
			return "MEDIUM";
		}
		return "LOW";
	}
}
