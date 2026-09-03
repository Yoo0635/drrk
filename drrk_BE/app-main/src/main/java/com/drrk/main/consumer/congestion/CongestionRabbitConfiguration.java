package com.drrk.main.consumer.congestion;

import com.drrk.messaging.congestion.CongestionRabbitNames;
import java.time.Clock;
import java.time.Duration;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
public class CongestionRabbitConfiguration {

	@Bean
	DirectExchange congestionExchange() {
		return new DirectExchange(CongestionRabbitNames.EXCHANGE, true, false);
	}

	@Bean
	DirectExchange congestionDeadLetterExchange() {
		return new DirectExchange(CongestionRabbitNames.DEAD_LETTER_EXCHANGE, true, false);
	}

	@Bean
	DirectExchange congestionRetryExchange() {
		return new DirectExchange(CongestionRetryNames.EXCHANGE, true, false);
	}

	@Bean
	Queue congestionQueue() {
		return QueueBuilder.durable(CongestionRabbitNames.MAIN_QUEUE)
				.deadLetterExchange(CongestionRabbitNames.DEAD_LETTER_EXCHANGE)
				.deadLetterRoutingKey(CongestionRabbitNames.DEAD_LETTER_ROUTING_KEY)
				.build();
	}

	@Bean
	Queue congestionDeadLetterQueue() {
		return QueueBuilder.durable(CongestionRabbitNames.DEAD_LETTER_QUEUE).build();
	}

	@Bean
	Queue congestionRetryOneSecondQueue() {
		return retryQueue(CongestionRetryNames.ONE_SECOND_QUEUE, Duration.ofSeconds(1));
	}

	@Bean
	Queue congestionRetryFiveSecondsQueue() {
		return retryQueue(CongestionRetryNames.FIVE_SECONDS_QUEUE, Duration.ofSeconds(5));
	}

	@Bean
	Queue congestionRetryFifteenSecondsQueue() {
		return retryQueue(CongestionRetryNames.FIFTEEN_SECONDS_QUEUE, Duration.ofSeconds(15));
	}

	@Bean
	Binding congestionBinding(Queue congestionQueue, DirectExchange congestionExchange) {
		return BindingBuilder.bind(congestionQueue)
				.to(congestionExchange)
				.with(CongestionRabbitNames.ROUTING_KEY);
	}

	@Bean
	Binding congestionDeadLetterBinding(
			Queue congestionDeadLetterQueue,
			DirectExchange congestionDeadLetterExchange
	) {
		return BindingBuilder.bind(congestionDeadLetterQueue)
				.to(congestionDeadLetterExchange)
				.with(CongestionRabbitNames.DEAD_LETTER_ROUTING_KEY);
	}

	@Bean
	Binding congestionRetryOneSecondBinding(
			Queue congestionRetryOneSecondQueue,
			DirectExchange congestionRetryExchange
	) {
		return BindingBuilder.bind(congestionRetryOneSecondQueue)
				.to(congestionRetryExchange)
				.with(CongestionRetryNames.ONE_SECOND_ROUTING_KEY);
	}

	@Bean
	Binding congestionRetryFiveSecondsBinding(
			Queue congestionRetryFiveSecondsQueue,
			DirectExchange congestionRetryExchange
	) {
		return BindingBuilder.bind(congestionRetryFiveSecondsQueue)
				.to(congestionRetryExchange)
				.with(CongestionRetryNames.FIVE_SECONDS_ROUTING_KEY);
	}

	@Bean
	Binding congestionRetryFifteenSecondsBinding(
			Queue congestionRetryFifteenSecondsQueue,
			DirectExchange congestionRetryExchange
	) {
		return BindingBuilder.bind(congestionRetryFifteenSecondsQueue)
				.to(congestionRetryExchange)
				.with(CongestionRetryNames.FIFTEEN_SECONDS_ROUTING_KEY);
	}

	@Bean
	SimpleRabbitListenerContainerFactory congestionRabbitListenerContainerFactory(
			ConnectionFactory connectionFactory
	) {
		SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
		factory.setConnectionFactory(connectionFactory);
		factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
		factory.setConcurrentConsumers(1);
		factory.setMaxConcurrentConsumers(1);
		factory.setPrefetchCount(5);
		factory.setDefaultRequeueRejected(false);
		return factory;
	}

	@Bean
	CongestionCalculatedMessageParser congestionCalculatedMessageParser(ObjectMapper objectMapper) {
		return new CongestionCalculatedMessageParser(objectMapper);
	}

	@Bean
	LatestAirportGuideStore latestAirportGuideStore(
			StringRedisTemplate redis,
			ObjectMapper objectMapper,
			@Value("${inference.stream.redis-retention:PT10M}") Duration redisRetention
	) {
		return new LatestAirportGuideStore(redis, objectMapper, redisRetention);
	}

	@Bean
	CongestionResultListener congestionResultListener(
			CongestionCalculatedMessageParser parser,
			LatestAirportGuideStore handler,
			CongestionRetryPublisher retryPublisher,
			CongestionDeliveryPublisher deliveryPublisher,
			CongestionReliabilityMetrics reliabilityMetrics
	) {
		return new CongestionResultListener(
				parser,
				handler,
				retryPublisher,
				deliveryPublisher,
				reliabilityMetrics
		);
	}

	@Bean
	CongestionRetryPublisher congestionRetryPublisher(
			RabbitTemplate rabbitTemplate,
			Clock clock,
			@Value("${congestion.retry.publisher-confirm-timeout:PT5S}") Duration confirmTimeout
	) {
		return new RabbitCongestionRetryPublisher(rabbitTemplate, clock, confirmTimeout);
	}

	private Queue retryQueue(String name, Duration ttl) {
		return QueueBuilder.durable(name)
				.ttl(Math.toIntExact(ttl.toMillis()))
				.deadLetterExchange(CongestionRabbitNames.EXCHANGE)
				.deadLetterRoutingKey(CongestionRabbitNames.ROUTING_KEY)
				.build();
	}
}
