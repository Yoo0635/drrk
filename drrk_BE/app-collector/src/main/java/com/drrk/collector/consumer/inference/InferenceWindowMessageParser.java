package com.drrk.collector.consumer.inference;

import com.drrk.collector.congestion.ModelMeasurementSnapshot;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.List;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

public class InferenceWindowMessageParser {

	private final ObjectMapper objectMapper;

	public InferenceWindowMessageParser(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public ModelMeasurementSnapshot parse(String payload) {
		return parse(payload, null);
	}

	public ModelMeasurementSnapshot parse(String payload, String fallbackMessageId) {
		InferenceWindowMessage message;
		try {
			message = objectMapper.readValue(payload, InferenceWindowMessage.class);
		} catch (JacksonException | IllegalArgumentException | NullPointerException exception) {
			throw new InvalidInferenceMessageException("Invalid model JSON", exception);
		}
		if (message == null) {
			throw new InvalidInferenceMessageException("Model message must not be null");
		}
		String messageId = resolveMessageId(message.messageId(), fallbackMessageId);
		validate(message);
		return new ModelMeasurementSnapshot(
				messageId,
				toInstant(message.ts()),
				message.spaceId(),
				message.windowSec(),
				message.carrierCount()
		);
	}

	private void validate(InferenceWindowMessage message) {
		if (message.spaceId() == null || message.spaceId().isBlank()) {
			throw new InvalidInferenceMessageException("space_id must not be blank");
		}
		if (message.windowSec() == null || message.windowSec() != 10) {
			throw new InvalidInferenceMessageException("window_sec must be 10");
		}
		if (message.ts() == null || !Double.isFinite(message.ts())) {
			throw new InvalidInferenceMessageException("ts must be finite");
		}
		try {
			toInstant(message.ts());
		} catch (DateTimeException exception) {
			throw new InvalidInferenceMessageException("ts must be a valid timestamp", exception);
		}
		if (message.carrierCount() == null || message.carrierCount() < 0) {
			throw new InvalidInferenceMessageException("n_carriers must be non-negative");
		}
		List<InferenceEvent> events = message.events();
		if (events == null) {
			throw new InvalidInferenceMessageException("events must not be null");
		}
		events.forEach(event -> validateEvent(event, message.ts(), message.windowSec()));
		long derivedCarrierCount;
		try {
			derivedCarrierCount = events.stream()
					.mapToLong(InferenceEvent::count)
					.reduce(0L, Math::addExact);
		} catch (ArithmeticException exception) {
			throw new InvalidInferenceMessageException("event counts overflow", exception);
		}
		if (message.carrierCount() != derivedCarrierCount) {
			throw new InvalidInferenceMessageException("n_carriers must match event counts");
		}
	}

	private String resolveMessageId(String jsonMessageId, String fallbackMessageId) {
		if (jsonMessageId != null && !jsonMessageId.isBlank()) {
			return jsonMessageId;
		}
		if (fallbackMessageId == null || fallbackMessageId.isBlank()) {
			throw new InvalidInferenceMessageException("message_id must not be blank");
		}
		return fallbackMessageId;
	}

	private void validateEvent(InferenceEvent event, double ts, int windowSec) {
		if (event == null) {
			throw new InvalidInferenceMessageException("events must not contain null");
		}
		if (event.t() == null || !Double.isFinite(event.t()) || event.t() < ts - windowSec || event.t() > ts) {
			throw new InvalidInferenceMessageException("event.t must be finite and within the measurement window");
		}
		if (event.dur() == null || !Double.isFinite(event.dur()) || event.dur() <= 0) {
			throw new InvalidInferenceMessageException("event.dur must be positive and finite");
		}
		if (event.dur() > windowSec) {
			throw new InvalidInferenceMessageException("event.dur must not be longer than window_sec");
		}
		if (event.count() == null || event.count() < 0) {
			throw new InvalidInferenceMessageException("event.count must be non-negative");
		}
		if (event.conf() == null || !Double.isFinite(event.conf()) || event.conf() < 0 || event.conf() > 1) {
			throw new InvalidInferenceMessageException("event.conf must be between 0 and 1");
		}
	}

	private Instant toInstant(double epochSeconds) {
		long seconds = (long) epochSeconds;
		long nanos = Math.round((epochSeconds - seconds) * 1_000_000_000d);
		return Instant.ofEpochSecond(seconds, nanos);
	}
}
