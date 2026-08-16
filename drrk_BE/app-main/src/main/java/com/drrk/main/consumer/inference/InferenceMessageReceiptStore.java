package com.drrk.main.consumer.inference;

import java.time.Instant;

public interface InferenceMessageReceiptStore {

	boolean insertIfAbsent(InferenceWindowMessage message, String payload, Instant receivedAt);
}
