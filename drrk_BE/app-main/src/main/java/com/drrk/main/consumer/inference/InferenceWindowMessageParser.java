package com.drrk.main.consumer.inference;

import java.util.Set;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public class InferenceWindowMessageParser {

	private static final Set<String> MESSAGE_FIELDS = Set.of(
			"message_id",
			"space_id",
			"ts",
			"window_sec",
			"events",
			"n_events",
			"n_carriers",
			"intensity",
			"count_est"
	);
	private static final Set<String> EVENT_FIELDS = Set.of("t", "dur", "count", "conf", "snr");

	private final ObjectMapper objectMapper;

	public InferenceWindowMessageParser(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public InferenceWindowMessage parse(String payload) {
		return parse(payload, null);
	}

	public InferenceWindowMessage parse(String payload, String fallbackMessageId) {
		try {
			JsonNode root = objectMapper.readTree(payload);
			validateRoot(root);
			InferenceWindowMessage message = objectMapper.treeToValue(root, InferenceWindowMessage.class);
			message = withResolvedMessageId(message, fallbackMessageId);
			validateMessage(message);
			return message;
		} catch (InvalidInferenceMessageException exception) {
			throw exception;
		} catch (JacksonException | IllegalArgumentException exception) {
			throw new InvalidInferenceMessageException("invalid inference JSON", exception);
		}
	}

	private static InferenceWindowMessage withResolvedMessageId(
			InferenceWindowMessage message,
			String fallbackMessageId
	) {
		String messageId = resolveMessageId(message.messageId(), fallbackMessageId);
		return new InferenceWindowMessage(
				messageId,
				message.spaceId(),
				message.ts(),
				message.windowSec(),
				message.events(),
				message.nEvents(),
				message.nCarriers(),
				message.intensity(),
				message.countEst()
		);
	}

	private static String resolveMessageId(String jsonMessageId, String fallbackMessageId) {
		boolean hasJsonMessageId = jsonMessageId != null && !jsonMessageId.isBlank();
		boolean hasFallbackMessageId = fallbackMessageId != null && !fallbackMessageId.isBlank();
		if (hasJsonMessageId && hasFallbackMessageId && !jsonMessageId.equals(fallbackMessageId)) {
			throw invalid("AMQP messageId must match JSON message_id");
		}
		if (hasJsonMessageId) {
			return jsonMessageId;
		}
		if (hasFallbackMessageId) {
			return fallbackMessageId;
		}
		throw invalid("message_id must not be blank");
	}

	private static void validateRoot(JsonNode root) {
		if (root == null || !root.isObject()) {
			throw invalid("root must be an object");
		}
		validateMessageFields(root);
		if (root.has("message_id")) {
			requireText(root, "message_id");
		}
		requireText(root, "space_id");
		requireNumber(root, "ts");
		requireInteger(root, "window_sec");
		requireInteger(root, "n_events");
		requireInteger(root, "n_carriers");
		requireNumber(root, "intensity");
		if (!root.get("count_est").isNull()) {
			throw invalid("count_est must be null");
		}
		JsonNode events = root.get("events");
		if (events == null || !events.isArray()) {
			throw invalid("events must be an array");
		}
		for (int index = 0; index < events.size(); index++) {
			JsonNode event = events.get(index);
			if (!event.isObject()) {
				throw invalid("events[" + index + "] must be an object");
			}
			validateFields(event, EVENT_FIELDS, "event fields");
			requireNumber(event, "t");
			requireNumber(event, "dur");
			requireInteger(event, "count");
			requireNumber(event, "conf");
			requireNumber(event, "snr");
		}
	}

	private static void validateMessage(InferenceWindowMessage message) {
		if (message.messageId().isBlank()) {
			throw invalid("message_id must not be blank");
		}
		if (message.spaceId().isBlank()) {
			throw invalid("space_id must not be blank");
		}
		if (message.ts() <= 0) {
			throw invalid("ts must be positive");
		}
		if (message.windowSec() != 10) {
			throw invalid("window_sec must be 10");
		}
		if (message.nEvents() != message.events().size()) {
			throw invalid("n_events must equal events length");
		}
		int carriers = message.events().stream().mapToInt(InferenceEvent::count).sum();
		if (message.nCarriers() != carriers) {
			throw invalid("n_carriers must equal the sum of event count values");
		}
		if (!inRange(message.intensity(), 0.0, 1.0)) {
			throw invalid("intensity must be between 0.0 and 1.0");
		}
		for (InferenceEvent event : message.events()) {
			if (event.t() <= 0) {
				throw invalid("event t must be positive");
			}
			if (!inRange(event.dur(), 0.5, 6.0)) {
				throw invalid("event dur must be between 0.5 and 6.0");
			}
			if (event.count() < 0 || event.count() > 4) {
				throw invalid("event count must be between 0 and 4");
			}
			if (!inRange(event.conf(), 0.0, 1.0)) {
				throw invalid("event conf must be between 0.0 and 1.0");
			}
		}
	}

	private static void validateFields(JsonNode node, Set<String> expected, String label) {
		Set<String> actual = Set.copyOf(node.propertyNames());
		if (!actual.equals(expected)) {
			throw invalid(label + " must be exactly " + expected);
		}
	}

	private static void validateMessageFields(JsonNode root) {
		Set<String> actual = Set.copyOf(root.propertyNames());
		if (actual.equals(MESSAGE_FIELDS)) {
			return;
		}
		Set<String> withoutMessageId = new java.util.HashSet<>(MESSAGE_FIELDS);
		withoutMessageId.remove("message_id");
		if (!actual.equals(withoutMessageId)) {
			throw invalid("message fields must be exactly " + MESSAGE_FIELDS + " or " + withoutMessageId);
		}
	}

	private static void requireText(JsonNode node, String field) {
		if (!node.get(field).isString()) {
			throw invalid(field + " must be a string");
		}
	}

	private static void requireNumber(JsonNode node, String field) {
		if (!node.get(field).isNumber()) {
			throw invalid(field + " must be a number");
		}
	}

	private static void requireInteger(JsonNode node, String field) {
		if (!node.get(field).isIntegralNumber()) {
			throw invalid(field + " must be an integer");
		}
	}

	private static boolean inRange(double value, double min, double max) {
		return Double.isFinite(value) && value >= min && value <= max;
	}

	private static InvalidInferenceMessageException invalid(String message) {
		return new InvalidInferenceMessageException(message);
	}
}
