package com.drrk.main.consumer.congestion;

final class CongestionRetryNames {

	static final String EXCHANGE = "drrk.congestion.retry.exchange";
	static final String ONE_SECOND_QUEUE = "drrk.main.congestion.snapshot.v3.retry.1s";
	static final String FIVE_SECONDS_QUEUE = "drrk.main.congestion.snapshot.v3.retry.5s";
	static final String FIFTEEN_SECONDS_QUEUE = "drrk.main.congestion.snapshot.v3.retry.15s";
	static final String ONE_SECOND_ROUTING_KEY = "congestion.snapshot.retry.1s.v3";
	static final String FIVE_SECONDS_ROUTING_KEY = "congestion.snapshot.retry.5s.v3";
	static final String FIFTEEN_SECONDS_ROUTING_KEY = "congestion.snapshot.retry.15s.v3";

	private CongestionRetryNames() {
	}
}
