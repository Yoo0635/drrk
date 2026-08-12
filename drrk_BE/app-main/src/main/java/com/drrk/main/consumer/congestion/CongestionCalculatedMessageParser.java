package com.drrk.main.consumer.congestion;

import com.drrk.messaging.congestion.CongestionCalculatedMessage;
import com.drrk.messaging.congestion.CongestionCalculationStatus;
import java.util.UUID;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

public class CongestionCalculatedMessageParser {

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
		validate(message);
		return message;
	}

	private void validate(CongestionCalculatedMessage message) {
		validateUuidV4(message.messageId(), "messageId");
		if (!"1.0".equals(message.schemaVersion())) {
			throw new InvalidCongestionMessageException("Unsupported schemaVersion");
		}
		if (message.status() == CongestionCalculationStatus.FORMULA_PENDING) {
			if (!"formula-pending-v0".equals(message.calculationVersion())
					|| message.score() != null
					|| message.level() != null) {
				throw new InvalidCongestionMessageException("Invalid FORMULA_PENDING payload");
			}
		}
		if (message.status() == CongestionCalculationStatus.CALCULATED
				&& (message.score() == null || !Double.isFinite(message.score())
				|| message.level() == null || message.level().isBlank())) {
			throw new InvalidCongestionMessageException("Invalid CALCULATED payload");
		}
		validateUuidV4(message.inputs().modelMessageId(), "inputs.modelMessageId");
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
}
