package com.drrk.collector.controller;

import java.util.Map;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

	private final ConnectionFactory rabbitConnectionFactory;

	public HealthController(ConnectionFactory rabbitConnectionFactory) {
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
		try (var rabbitConnection = rabbitConnectionFactory.createConnection()) {
			return rabbitConnection.isOpen();
		} catch (Exception ex) {
			return false;
		}
	}
}
