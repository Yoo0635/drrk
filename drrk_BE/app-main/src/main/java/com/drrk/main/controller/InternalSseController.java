package com.drrk.main.controller;

import com.drrk.main.consumer.inference.InferenceSseBroadcaster;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/sse")
public class InternalSseController {

	private final InferenceSseBroadcaster broadcaster;

	public InternalSseController(InferenceSseBroadcaster broadcaster) {
		this.broadcaster = broadcaster;
	}

	@GetMapping(path = "/connections", produces = MediaType.TEXT_PLAIN_VALUE)
	public String activeConnections() {
		return String.valueOf(broadcaster.activeEmitterCount());
	}

	@PostMapping("/drain")
	public Map<String, Integer> drain() {
		int drained = broadcaster.drainActiveEmitters();
		return Map.of(
				"drained", drained,
				"active", broadcaster.activeEmitterCount()
		);
	}
}
