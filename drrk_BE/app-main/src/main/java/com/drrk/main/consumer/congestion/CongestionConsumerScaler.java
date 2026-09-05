package com.drrk.main.consumer.congestion;

interface CongestionConsumerScaler {

	String LISTENER_ID = "congestionResultListener";

	void retryDeliveryObserved();
}
