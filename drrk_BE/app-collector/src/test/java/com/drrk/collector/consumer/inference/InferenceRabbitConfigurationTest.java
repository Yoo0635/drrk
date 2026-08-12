package com.drrk.collector.consumer.inference;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerEndpoint;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.test.util.ReflectionTestUtils;

class InferenceRabbitConfigurationTest {

	private final InferenceRabbitConfiguration configuration = new InferenceRabbitConfiguration();

	@Test
	void declaresDurableCollectorQueueWithDeadLetterRouting() {
		Queue queue = configuration.inferenceQueue();

		assertThat(queue.getName()).isEqualTo(InferenceRabbitNames.QUEUE);
		assertThat(queue.isDurable()).isTrue();
		assertThat(queue.getArguments())
				.containsEntry("x-dead-letter-exchange", InferenceRabbitNames.DEAD_LETTER_EXCHANGE)
				.containsEntry("x-dead-letter-routing-key", InferenceRabbitNames.DEAD_LETTER_ROUTING_KEY);
	}

	@Test
	void bindsCollectorAndDeadLetterQueuesToTheirExchanges() {
		DirectExchange exchange = configuration.inferenceExchange();
		DirectExchange deadLetterExchange = configuration.inferenceDeadLetterExchange();
		Queue queue = configuration.inferenceQueue();
		Queue deadLetterQueue = configuration.inferenceDeadLetterQueue();

		Binding mainBinding = configuration.inferenceBinding(queue, exchange);
		Binding deadLetterBinding = configuration.inferenceDeadLetterBinding(deadLetterQueue, deadLetterExchange);

		assertThat(exchange.getName()).isEqualTo(InferenceRabbitNames.EXCHANGE);
		assertThat(exchange.isDurable()).isTrue();
		assertThat(mainBinding.getDestination()).isEqualTo(InferenceRabbitNames.QUEUE);
		assertThat(mainBinding.getExchange()).isEqualTo(InferenceRabbitNames.EXCHANGE);
		assertThat(mainBinding.getRoutingKey()).isEqualTo(InferenceRabbitNames.ROUTING_KEY);
		assertThat(deadLetterQueue.getName()).isEqualTo(InferenceRabbitNames.DEAD_LETTER_QUEUE);
		assertThat(deadLetterQueue.isDurable()).isTrue();
		assertThat(deadLetterBinding.getExchange()).isEqualTo(InferenceRabbitNames.DEAD_LETTER_EXCHANGE);
		assertThat(deadLetterBinding.getRoutingKey()).isEqualTo(InferenceRabbitNames.DEAD_LETTER_ROUTING_KEY);
	}

	@Test
	void configuresSingleManualAckConsumerWithoutAutomaticRequeue() {
		CachingConnectionFactory connectionFactory = new CachingConnectionFactory("localhost");
		SimpleRabbitListenerContainerFactory factory = configuration.inferenceRabbitListenerContainerFactory(
				connectionFactory
		);
		SimpleRabbitListenerEndpoint endpoint = new SimpleRabbitListenerEndpoint();
		endpoint.setId("collector-inference-test");
		endpoint.setQueueNames(InferenceRabbitNames.QUEUE);
		endpoint.setMessageListener(message -> { });

		SimpleMessageListenerContainer container = factory.createListenerContainer(endpoint);

		assertThat(container.getAcknowledgeMode()).isEqualTo(AcknowledgeMode.MANUAL);
		assertThat(ReflectionTestUtils.getField(container, "concurrentConsumers")).isEqualTo(1);
		assertThat(ReflectionTestUtils.getField(container, "prefetchCount")).isEqualTo(1);
		assertThat(ReflectionTestUtils.getField(container, "defaultRequeueRejected")).isEqualTo(false);
		connectionFactory.destroy();
	}
}
