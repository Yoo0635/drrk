package com.drrk.main.consumer.inference;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record InferenceWindowMessage(
		@JsonProperty("message_id") String messageId,
		@JsonProperty("space_id") String spaceId,
		double ts,
		@JsonProperty("window_sec") int windowSec,
		List<InferenceEvent> events,
		@JsonProperty("n_events") int nEvents,
		@JsonProperty("n_carriers") int nCarriers,
		double intensity,
		@JsonProperty("count_est") Object countEst
) {
	public InferenceWindowMessage {
		events = List.copyOf(events);
	}
}
