package com.drrk.main.consumer.inference;

import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class InferenceRabbitConfiguration {

	public static final String INFERENCE_EXCHANGE = "drrk.inference.exchange";
	public static final String INFERENCE_ROUTING_KEY = "inference.window.v1";
	public static final String INFERENCE_QUEUE = "drrk.main.inference.window.v1";
	public static final String INFERENCE_DEAD_LETTER_EXCHANGE = "drrk.inference.dlx";
	public static final String INFERENCE_DEAD_LETTER_ROUTING_KEY = "inference.window.dead.v1";
	public static final String INFERENCE_DEAD_LETTER_QUEUE = "drrk.main.inference.window.dlq";
	public static final int INFERENCE_DEAD_LETTER_MESSAGE_TTL_MILLIS = 30 * 60 * 1000;

	@Bean
	DirectExchange inferenceExchange() {
		return new DirectExchange(INFERENCE_EXCHANGE, true, false);
	}

	@Bean
	DirectExchange inferenceDeadLetterExchange() {
		return new DirectExchange(INFERENCE_DEAD_LETTER_EXCHANGE, true, false);
	}

	@Bean
	Queue inferenceQueue() {
		return QueueBuilder.durable(INFERENCE_QUEUE)
				.deadLetterExchange(INFERENCE_DEAD_LETTER_EXCHANGE)
				.deadLetterRoutingKey(INFERENCE_DEAD_LETTER_ROUTING_KEY)
				.build();
	}

	@Bean
	Queue inferenceDeadLetterQueue() {
		return QueueBuilder.durable(INFERENCE_DEAD_LETTER_QUEUE)
				.ttl(INFERENCE_DEAD_LETTER_MESSAGE_TTL_MILLIS)
				.build();
	}

	@Bean
	Binding inferenceBinding(Queue inferenceQueue, DirectExchange inferenceExchange) {
		return BindingBuilder.bind(inferenceQueue).to(inferenceExchange).with(INFERENCE_ROUTING_KEY);
	}

	@Bean
	Binding inferenceDeadLetterBinding(
			Queue inferenceDeadLetterQueue,
			DirectExchange inferenceDeadLetterExchange
	) {
		return BindingBuilder.bind(inferenceDeadLetterQueue)
				.to(inferenceDeadLetterExchange)
				.with(INFERENCE_DEAD_LETTER_ROUTING_KEY);
	}

	@Bean
	SimpleRabbitListenerContainerFactory inferenceRabbitListenerContainerFactory(
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
	InferenceWindowMessageParser inferenceWindowMessageParser(ObjectMapper objectMapper) {
		return new InferenceWindowMessageParser(objectMapper);
	}
}
