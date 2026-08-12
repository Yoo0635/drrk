package com.drrk.main.consumer.inference;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "inference_message_receipt")
public class InferenceMessageReceipt {

	@Id
	@Column(name = "message_id", nullable = false, length = 36)
	private String messageId;

	@Column(name = "space_id", nullable = false)
	private String spaceId;

	@Column(name = "window_ended_at", nullable = false)
	private double windowEndedAt;

	@Column(name = "payload", nullable = false, columnDefinition = "text")
	private String payload;

	@Column(name = "received_at", nullable = false)
	private Instant receivedAt;

	protected InferenceMessageReceipt() {
	}
}
