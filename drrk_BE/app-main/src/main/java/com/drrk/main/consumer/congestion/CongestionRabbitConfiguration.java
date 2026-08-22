package com.drrk.main.consumer.congestion;

import com.drrk.messaging.congestion.CongestionRabbitNames;
import java.time.Duration;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
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
	SimpleRabbitListenerContainerFactory congestionRabbitListenerContainerFactory(
			ConnectionFactory connectionFactory
	) {
		SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
		factory.setConnectionFactory(connectionFactory);
		factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
		factory.setConcurrentConsumers(1);
		factory.setMaxConcurrentConsumers(1);
		factory.setPrefetchCount(1);
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
			LatestAirportGuideStore handler
	) {
		return new CongestionResultListener(parser, handler);
	}
}
