package com.drrk.messaging.congestion;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record CongestionCalculatedMessage(
		String messageId,
		String schemaVersion,
		Instant calculatedAt,
		String calculationVersion,
		CongestionCalculationStatus status,
		Double score,
		String level,
		CongestionInputReferences inputs
) {

	public CongestionCalculatedMessage {
		Objects.requireNonNull(messageId, "messageId");
		Objects.requireNonNull(schemaVersion, "schemaVersion");
		Objects.requireNonNull(calculatedAt, "calculatedAt");
		Objects.requireNonNull(calculationVersion, "calculationVersion");
		Objects.requireNonNull(status, "status");
		Objects.requireNonNull(inputs, "inputs");
	}

	public static CongestionCalculatedMessage formulaPending(
			UUID messageId,
			Instant calculatedAt,
			CongestionInputReferences inputs
	) {
		return new CongestionCalculatedMessage(
				messageId.toString(),
				"1.0",
				calculatedAt,
				"formula-pending-v0",
				CongestionCalculationStatus.FORMULA_PENDING,
				null,
				null,
				inputs
		);
	}
}
