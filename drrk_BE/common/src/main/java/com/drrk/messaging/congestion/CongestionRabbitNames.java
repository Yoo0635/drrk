package com.drrk.messaging.congestion;

public final class CongestionRabbitNames {

	public static final String EXCHANGE = "drrk.congestion.exchange";
	public static final String ROUTING_KEY = "congestion.snapshot.v2";
	public static final String MAIN_QUEUE = "drrk.main.congestion.snapshot.v2";
	public static final String DEAD_LETTER_EXCHANGE = "drrk.congestion.dlx";
	public static final String DEAD_LETTER_ROUTING_KEY = "congestion.snapshot.dead.v2";
	public static final String DEAD_LETTER_QUEUE = "drrk.main.congestion.snapshot.v2.dlq";

	private CongestionRabbitNames() {
	}
}
