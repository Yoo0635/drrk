package com.drrk.collector.consumer.inference;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record InferenceWindowMessage(
		@JsonProperty("message_id") String messageId,
		@JsonProperty("space_id") String spaceId,
		double ts,
		@JsonProperty("window_sec") int windowSec,
		List<InferenceEvent> events,
		@JsonProperty("n_events") int eventCount,
		@JsonProperty("n_carriers") int carrierCount,
		double intensity,
		@JsonProperty("count_est") Object countEstimate
) {

	public InferenceWindowMessage {
		events = List.copyOf(events);
	}
}
