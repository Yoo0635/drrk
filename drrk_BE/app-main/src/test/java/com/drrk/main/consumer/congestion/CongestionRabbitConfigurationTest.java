package com.drrk.main.consumer.congestion;

import static org.assertj.core.api.Assertions.assertThat;

import com.drrk.messaging.congestion.CongestionRabbitNames;
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

class CongestionRabbitConfigurationTest {

	private final CongestionRabbitConfiguration configuration = new CongestionRabbitConfiguration();

	@Test
	void declaresDurableMainQueueWithDeadLetterRouting() {
		Queue queue = configuration.congestionQueue();

		assertThat(queue.getName()).isEqualTo(CongestionRabbitNames.MAIN_QUEUE);
		assertThat(queue.isDurable()).isTrue();
		assertThat(queue.getArguments())
				.containsEntry("x-dead-letter-exchange", CongestionRabbitNames.DEAD_LETTER_EXCHANGE)
				.containsEntry("x-dead-letter-routing-key", CongestionRabbitNames.DEAD_LETTER_ROUTING_KEY);
	}

	@Test
	void bindsMainAndDeadLetterQueues() {
		DirectExchange exchange = configuration.congestionExchange();
		DirectExchange deadLetterExchange = configuration.congestionDeadLetterExchange();
		Queue queue = configuration.congestionQueue();
		Queue deadLetterQueue = configuration.congestionDeadLetterQueue();

		Binding mainBinding = configuration.congestionBinding(queue, exchange);
		Binding deadLetterBinding = configuration.congestionDeadLetterBinding(deadLetterQueue, deadLetterExchange);

		assertThat(mainBinding.getExchange()).isEqualTo(CongestionRabbitNames.EXCHANGE);
		assertThat(mainBinding.getRoutingKey()).isEqualTo(CongestionRabbitNames.ROUTING_KEY);
		assertThat(deadLetterQueue.getName()).isEqualTo(CongestionRabbitNames.DEAD_LETTER_QUEUE);
		assertThat(deadLetterBinding.getExchange()).isEqualTo(CongestionRabbitNames.DEAD_LETTER_EXCHANGE);
		assertThat(deadLetterBinding.getRoutingKey()).isEqualTo(CongestionRabbitNames.DEAD_LETTER_ROUTING_KEY);
	}

	@Test
	void configuresSingleManualAckConsumerWithoutAutomaticRequeue() {
		CachingConnectionFactory connectionFactory = new CachingConnectionFactory("localhost");
		SimpleRabbitListenerContainerFactory factory = configuration.congestionRabbitListenerContainerFactory(
				connectionFactory
		);
		SimpleRabbitListenerEndpoint endpoint = new SimpleRabbitListenerEndpoint();
		endpoint.setId("congestion-test");
		endpoint.setQueueNames(CongestionRabbitNames.MAIN_QUEUE);
		endpoint.setMessageListener(message -> { });

		SimpleMessageListenerContainer container = factory.createListenerContainer(endpoint);

		assertThat(container.getAcknowledgeMode()).isEqualTo(AcknowledgeMode.MANUAL);
		assertThat(ReflectionTestUtils.getField(container, "concurrentConsumers")).isEqualTo(1);
		assertThat(ReflectionTestUtils.getField(container, "prefetchCount")).isEqualTo(1);
		assertThat(ReflectionTestUtils.getField(container, "defaultRequeueRejected")).isEqualTo(false);
		connectionFactory.destroy();
	}
}
