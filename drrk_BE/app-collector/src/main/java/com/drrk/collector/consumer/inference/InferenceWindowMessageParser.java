package com.drrk.collector.consumer.inference;

import com.drrk.collector.congestion.ModelMeasurementSnapshot;
import java.time.Instant;
import java.util.UUID;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

public class InferenceWindowMessageParser {

	private final ObjectMapper objectMapper;

	public InferenceWindowMessageParser(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public ModelMeasurementSnapshot parse(String payload) {
		InferenceWindowMessage message;
		try {
			message = objectMapper.readValue(payload, InferenceWindowMessage.class);
		} catch (JacksonException | IllegalArgumentException exception) {
			throw new InvalidInferenceMessageException("Invalid model JSON", exception);
		}
		if (message == null) {
			throw new InvalidInferenceMessageException("Model message must not be null");
		}
		validate(message);
		return new ModelMeasurementSnapshot(
				message.messageId(),
				toInstant(message.ts()),
				message.carrierCount(),
				message.intensity()
		);
	}

	private void validate(InferenceWindowMessage message) {
		UUID messageId;
		try {
			messageId = UUID.fromString(message.messageId());
		} catch (RuntimeException exception) {
			throw new InvalidInferenceMessageException("message_id must be UUID v4", exception);
		}
		if (messageId.version() != 4) {
			throw new InvalidInferenceMessageException("message_id must be UUID v4");
		}
		if (message.spaceId() == null || message.spaceId().isBlank()) {
			throw new InvalidInferenceMessageException("space_id must not be blank");
		}
		if (message.windowSec() != 10) {
			throw new InvalidInferenceMessageException("window_sec must be 10");
		}
		if (!Double.isFinite(message.ts()) || message.ts() <= 0) {
			throw new InvalidInferenceMessageException("ts must be a positive finite epoch second");
		}
		if (message.eventCount() != message.events().size()) {
			throw new InvalidInferenceMessageException("n_events must match events size");
		}
		message.events().forEach(this::validateEvent);
		int derivedCarrierCount = message.events().stream().mapToInt(InferenceEvent::count).sum();
		if (message.carrierCount() != derivedCarrierCount) {
			throw new InvalidInferenceMessageException("n_carriers must match event counts");
		}
		if (!Double.isFinite(message.intensity()) || message.intensity() < 0 || message.intensity() > 1) {
			throw new InvalidInferenceMessageException("intensity must be between 0 and 1");
		}
	}

	private void validateEvent(InferenceEvent event) {
		if (!Double.isFinite(event.t()) || event.t() <= 0) {
			throw new InvalidInferenceMessageException("event.t must be a positive finite epoch second");
		}
		if (!Double.isFinite(event.dur()) || event.dur() <= 0) {
			throw new InvalidInferenceMessageException("event.dur must be positive and finite");
		}
		if (event.count() < 0) {
			throw new InvalidInferenceMessageException("event.count must be non-negative");
		}
		if (!Double.isFinite(event.conf()) || event.conf() < 0 || event.conf() > 1) {
			throw new InvalidInferenceMessageException("event.conf must be between 0 and 1");
		}
		if (!Double.isFinite(event.snr())) {
			throw new InvalidInferenceMessageException("event.snr must be finite");
		}
	}

	private Instant toInstant(double epochSeconds) {
		long seconds = (long) epochSeconds;
		long nanos = Math.round((epochSeconds - seconds) * 1_000_000_000d);
		return Instant.ofEpochSecond(seconds, nanos);
	}
}
