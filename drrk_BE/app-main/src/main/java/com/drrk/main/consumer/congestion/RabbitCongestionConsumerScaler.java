package com.drrk.main.consumer.congestion;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.listener.MessageListenerContainer;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.beans.factory.DisposableBean;

final class RabbitCongestionConsumerScaler implements CongestionConsumerScaler, DisposableBean {

	private static final Logger log = LoggerFactory.getLogger(RabbitCongestionConsumerScaler.class);
	private static final int BASE_CONSUMERS = 1;
	private static final int RETRY_CONSUMERS = 2;

	private final RabbitListenerEndpointRegistry registry;
	private final Duration scaleDownDelay;
	private final ScheduledExecutorService scheduler;
	private final AtomicReference<ScheduledFuture<?>> scaleDownTask = new AtomicReference<>();
	private final AtomicInteger configuredConsumers = new AtomicInteger(BASE_CONSUMERS);

	RabbitCongestionConsumerScaler(RabbitListenerEndpointRegistry registry, Duration scaleDownDelay) {
		this.registry = registry;
		this.scaleDownDelay = scaleDownDelay;
		this.scheduler = Executors.newSingleThreadScheduledExecutor(new ScalerThreadFactory());
	}

	@Override
	public void retryDeliveryObserved() {
		SimpleMessageListenerContainer container = congestionContainer();
		if (container == null) {
			return;
		}
		if (configuredConsumers.getAndSet(RETRY_CONSUMERS) != RETRY_CONSUMERS) {
			container.setConcurrentConsumers(RETRY_CONSUMERS);
			log.info("[CONSUME SCALE UP] listenerId={} concurrentConsumers={}", LISTENER_ID, RETRY_CONSUMERS);
		}
		scheduleScaleDown();
	}

	@Override
	public void destroy() {
		ScheduledFuture<?> task = scaleDownTask.getAndSet(null);
		if (task != null) {
			task.cancel(false);
		}
		scheduler.shutdownNow();
	}

	private void scheduleScaleDown() {
		ScheduledFuture<?> previous = scaleDownTask.getAndSet(
				scheduler.schedule(this::scaleDown, scaleDownDelay.toMillis(), TimeUnit.MILLISECONDS)
		);
		if (previous != null) {
			previous.cancel(false);
		}
	}

	private void scaleDown() {
		SimpleMessageListenerContainer container = congestionContainer();
		if (container == null || configuredConsumers.getAndSet(BASE_CONSUMERS) == BASE_CONSUMERS) {
			return;
		}
		container.setConcurrentConsumers(BASE_CONSUMERS);
		log.info("[CONSUME SCALE DOWN] listenerId={} concurrentConsumers={}", LISTENER_ID, BASE_CONSUMERS);
	}

	private SimpleMessageListenerContainer congestionContainer() {
		MessageListenerContainer container = registry.getListenerContainer(LISTENER_ID);
		if (container instanceof SimpleMessageListenerContainer simpleContainer) {
			return simpleContainer;
		}
		log.warn("[CONSUME SCALE SKIP] listenerId={} reason=CONTAINER_NOT_READY", LISTENER_ID);
		return null;
	}

	private static final class ScalerThreadFactory implements ThreadFactory {

		@Override
		public Thread newThread(Runnable runnable) {
			Thread thread = new Thread(runnable, "congestion-consumer-scaler");
			thread.setDaemon(true);
			return thread;
		}
	}
}
