package com.drrk.main.controller;

import com.drrk.main.consumer.inference.InferenceSseBroadcaster;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/inference/carriers")
public class InferenceStreamController {

	private final InferenceSseBroadcaster broadcaster;

	public InferenceStreamController(InferenceSseBroadcaster broadcaster) {
		this.broadcaster = broadcaster;
	}

	@GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public ResponseEntity<SseEmitter> streamCarrierCounts() {
		return ResponseEntity.ok()
				.contentType(MediaType.TEXT_EVENT_STREAM)
				.cacheControl(CacheControl.noCache())
				.header("X-Accel-Buffering", "no")
				.header(HttpHeaders.CONNECTION, "keep-alive")
				.body(broadcaster.subscribe());
	}
}
