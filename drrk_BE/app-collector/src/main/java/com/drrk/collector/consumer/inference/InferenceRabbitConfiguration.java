package com.drrk.collector.consumer.inference;

import com.drrk.collector.congestion.LatestCongestionInputStore;
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

	@Bean
	DirectExchange inferenceExchange() {
		return new DirectExchange(InferenceRabbitNames.EXCHANGE, true, false);
	}

	@Bean
	DirectExchange inferenceDeadLetterExchange() {
		return new DirectExchange(InferenceRabbitNames.DEAD_LETTER_EXCHANGE, true, false);
	}

	@Bean
	Queue inferenceQueue() {
		return QueueBuilder.durable(InferenceRabbitNames.QUEUE)
				.deadLetterExchange(InferenceRabbitNames.DEAD_LETTER_EXCHANGE)
				.deadLetterRoutingKey(InferenceRabbitNames.DEAD_LETTER_ROUTING_KEY)
				.build();
	}

	@Bean
	Queue inferenceDeadLetterQueue() {
		return QueueBuilder.durable(InferenceRabbitNames.DEAD_LETTER_QUEUE).build();
	}

	@Bean
	Binding inferenceBinding(Queue inferenceQueue, DirectExchange inferenceExchange) {
		return BindingBuilder.bind(inferenceQueue)
				.to(inferenceExchange)
				.with(InferenceRabbitNames.ROUTING_KEY);
	}

	@Bean
	Binding inferenceDeadLetterBinding(
			Queue inferenceDeadLetterQueue,
			DirectExchange inferenceDeadLetterExchange
	) {
		return BindingBuilder.bind(inferenceDeadLetterQueue)
				.to(inferenceDeadLetterExchange)
				.with(InferenceRabbitNames.DEAD_LETTER_ROUTING_KEY);
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

	@Bean
	InferenceWindowListener inferenceWindowListener(
			InferenceWindowMessageParser parser,
			LatestCongestionInputStore store
	) {
		return new InferenceWindowListener(parser, store);
	}
}
