package com.drrk.main.consumer.inference;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record CarrierCountStreamResponse(
		@JsonProperty("n_carriers") int carrierCount,
		@JsonProperty("score") Double score,
		@JsonProperty("level") String level,
		Instant serverNow
) {
}
