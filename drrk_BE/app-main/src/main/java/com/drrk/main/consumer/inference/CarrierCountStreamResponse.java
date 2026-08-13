package com.drrk.main.consumer.inference;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CarrierCountStreamResponse(
		@JsonProperty("space_id") String spaceId,
		@JsonProperty("n_carriers") int carrierCount
) {
}
