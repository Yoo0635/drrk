package com.drrk.main.consumer.congestion;

import com.drrk.messaging.congestion.CongestionCalculatedMessage;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;

class JdbcCongestionHistoryStore implements CongestionHistoryStore {

	private final JdbcTemplate jdbcTemplate;

	JdbcCongestionHistoryStore(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public Optional<String> findPayload(String messageId) {
		return jdbcTemplate.query(
				"SELECT payload FROM congestion_history WHERE message_id = ?",
				(rs, rowNum) -> rs.getString("payload"),
				messageId
		).stream().findFirst();
	}

	@Override
	public boolean insertIfAbsent(CongestionCalculatedMessage message, String payload, Instant receivedAt) {
		return jdbcTemplate.update("""
				INSERT INTO congestion_history (
					message_id,
					calculated_at,
					received_at,
					status,
					calculation_version,
					score,
					current_load,
					forecast_load,
					level,
					capacity,
					payload
				) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				ON CONFLICT (message_id) DO NOTHING
				""",
				message.messageId(),
				message.calculatedAt(),
				receivedAt,
				message.status().name(),
				message.calculationVersion(),
				message.score(),
				message.currentLoad(),
				message.forecastLoad(),
				message.level(),
				message.capacity(),
				payload
		) == 1;
	}

	@Override
	public int deleteExpired(Instant cutoff, int limit) {
		return jdbcTemplate.update("""
				WITH expired AS (
					SELECT message_id
					FROM congestion_history
					WHERE calculated_at <= ?
					ORDER BY calculated_at
					LIMIT ?
				)
				DELETE FROM congestion_history history
				USING expired
				WHERE history.message_id = expired.message_id
				""", cutoff, limit);
	}
}
