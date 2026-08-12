package com.drrk.collector.consumer.inference;

public final class InferenceRabbitNames {

	public static final String EXCHANGE = "drrk.inference.exchange";
	public static final String ROUTING_KEY = "inference.window.v1";
	public static final String QUEUE = "drrk.collector.inference.window.v1";
	public static final String DEAD_LETTER_EXCHANGE = "drrk.inference.dlx";
	public static final String DEAD_LETTER_ROUTING_KEY = "inference.window.collector.dead.v1";
	public static final String DEAD_LETTER_QUEUE = "drrk.collector.inference.window.dlq";

	private InferenceRabbitNames() {
	}
}
