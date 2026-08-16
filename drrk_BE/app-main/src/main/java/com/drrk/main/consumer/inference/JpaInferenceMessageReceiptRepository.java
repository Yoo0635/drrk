package com.drrk.main.consumer.inference;

import java.time.Instant;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface JpaInferenceMessageReceiptRepository
		extends Repository<InferenceMessageReceipt, String>, InferenceMessageReceiptStore {

	@Override
	default boolean insertIfAbsent(InferenceWindowMessage message, String payload, Instant receivedAt) {
		return insertRowIfAbsent(
				message.messageId(),
				message.spaceId(),
				message.ts(),
				payload,
				receivedAt
		) == 1;
	}

	@Modifying
	@Query(value = """
			INSERT INTO inference_message_receipt (
				message_id,
				space_id,
				window_ended_at,
				payload,
				received_at
			) VALUES (
				:messageId,
				:spaceId,
				:windowEndedAt,
				:payload,
				:receivedAt
			)
			ON CONFLICT (message_id) DO NOTHING
			""", nativeQuery = true)
	int insertRowIfAbsent(
			@Param("messageId") String messageId,
			@Param("spaceId") String spaceId,
			@Param("windowEndedAt") double windowEndedAt,
			@Param("payload") String payload,
			@Param("receivedAt") Instant receivedAt
	);
}
