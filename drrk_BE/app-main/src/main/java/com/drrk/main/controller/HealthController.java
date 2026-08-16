package com.drrk.main.controller;

import java.util.Map;
import javax.sql.DataSource;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

	private final DataSource dataSource;
	private final RedisConnectionFactory redisConnectionFactory;
	private final ConnectionFactory rabbitConnectionFactory;

	public HealthController(
			DataSource dataSource,
			RedisConnectionFactory redisConnectionFactory,
			ConnectionFactory rabbitConnectionFactory
	) {
		this.dataSource = dataSource;
		this.redisConnectionFactory = redisConnectionFactory;
		this.rabbitConnectionFactory = rabbitConnectionFactory;
	}

	@GetMapping("/healthz")
	public ResponseEntity<Map<String, String>> healthz() {
		if (isReady()) {
			return ResponseEntity.ok(Map.of("status", "UP"));
		}
		return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
				.body(Map.of("status", "DOWN"));
	}

	private boolean isReady() {
		try (var jdbcConnection = dataSource.getConnection();
				var redisConnection = redisConnectionFactory.getConnection();
				var rabbitConnection = rabbitConnectionFactory.createConnection()) {
			return jdbcConnection.isValid(2)
					&& "PONG".equals(redisConnection.ping())
					&& rabbitConnection.isOpen();
		} catch (Exception ex) {
			return false;
		}
	}
}
