package com.drrk.main.consumer.inference;

import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InferenceWindowIngestionService {

	private final InferenceMessageReceiptStore receiptStore;
	private final Clock clock;

	public InferenceWindowIngestionService(InferenceMessageReceiptStore receiptStore, Clock clock) {
		this.receiptStore = receiptStore;
		this.clock = clock;
	}

	@Transactional
	public InferenceIngestionResult ingest(InferenceWindowMessage message, String payload) {
		boolean inserted = receiptStore.insertIfAbsent(message, payload, clock.instant());
		return inserted ? InferenceIngestionResult.STORED : InferenceIngestionResult.DUPLICATE;
	}
}
