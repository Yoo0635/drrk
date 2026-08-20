package com.drrk.main.consumer.inference;

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
	void declaresDurableInferenceQueueWithDeadLetterRouting() {
		Queue queue = configuration.inferenceQueue();

		assertThat(queue.getName()).isEqualTo("drrk.main.inference.window.v1");
		assertThat(queue.isDurable()).isTrue();
		assertThat(queue.getArguments())
				.containsEntry("x-dead-letter-exchange", "drrk.inference.dlx")
				.containsEntry("x-dead-letter-routing-key", "inference.window.dead.v1");
	}

	@Test
	void bindsMainAndDeadLetterQueuesToTheirExchanges() {
		DirectExchange exchange = configuration.inferenceExchange();
		DirectExchange deadLetterExchange = configuration.inferenceDeadLetterExchange();
		Queue queue = configuration.inferenceQueue();
		Queue deadLetterQueue = configuration.inferenceDeadLetterQueue();

		Binding mainBinding = configuration.inferenceBinding(queue, exchange);
		Binding deadLetterBinding = configuration.inferenceDeadLetterBinding(deadLetterQueue, deadLetterExchange);

		assertThat(exchange.getName()).isEqualTo("drrk.inference.exchange");
		assertThat(exchange.isDurable()).isTrue();
		assertThat(mainBinding.getDestination()).isEqualTo("drrk.main.inference.window.v1");
		assertThat(mainBinding.getExchange()).isEqualTo("drrk.inference.exchange");
		assertThat(mainBinding.getRoutingKey()).isEqualTo("inference.window.v1");
		assertThat(deadLetterQueue.getName()).isEqualTo("drrk.main.inference.window.dlq");
		assertThat(deadLetterQueue.isDurable()).isTrue();
		assertThat(deadLetterQueue.getArguments())
				.containsEntry("x-message-ttl", InferenceRabbitConfiguration.INFERENCE_DEAD_LETTER_MESSAGE_TTL_MILLIS);
		assertThat(deadLetterBinding.getExchange()).isEqualTo("drrk.inference.dlx");
		assertThat(deadLetterBinding.getRoutingKey()).isEqualTo("inference.window.dead.v1");
	}

	@Test
	void configuresSingleManualAckConsumerWithoutAutomaticRequeue() {
		CachingConnectionFactory connectionFactory = new CachingConnectionFactory("localhost");
		SimpleRabbitListenerContainerFactory factory = configuration.inferenceRabbitListenerContainerFactory(
				connectionFactory
		);
		SimpleRabbitListenerEndpoint endpoint = new SimpleRabbitListenerEndpoint();
		endpoint.setId("inference-test");
		endpoint.setQueueNames(InferenceRabbitConfiguration.INFERENCE_QUEUE);
		endpoint.setMessageListener(message -> { });

		SimpleMessageListenerContainer container = factory.createListenerContainer(endpoint);

		assertThat(container.getAcknowledgeMode()).isEqualTo(AcknowledgeMode.MANUAL);
		assertThat(ReflectionTestUtils.getField(container, "concurrentConsumers")).isEqualTo(1);
		assertThat(ReflectionTestUtils.getField(container, "prefetchCount")).isEqualTo(1);
		assertThat(ReflectionTestUtils.getField(container, "defaultRequeueRejected")).isEqualTo(false);
		connectionFactory.destroy();
	}
}
