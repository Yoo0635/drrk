package com.drrk.collector.consumer.inference;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record InferenceWindowMessage(
		@JsonProperty("message_id") String messageId,
		@JsonProperty("space_id") String spaceId,
		Double ts,
		@JsonProperty("window_sec") Integer windowSec,
		List<InferenceEvent> events,
		@JsonProperty("n_events") Integer eventCount,
		@JsonProperty("n_carriers") Long carrierCount,
		Double intensity,
		@JsonProperty("count_est") Object countEstimate
) {
}
