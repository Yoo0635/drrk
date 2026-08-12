package com.drrk.collector.publisher.congestion;

import static org.assertj.core.api.Assertions.assertThat;

import com.drrk.messaging.congestion.CongestionRabbitNames;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

class CongestionPublisherConfigurationTest {

	private final CongestionPublisherConfiguration configuration = new CongestionPublisherConfiguration();

	@Test
	void declaresDurableCongestionExchange() {
		DirectExchange exchange = configuration.congestionExchange();

		assertThat(exchange.getName()).isEqualTo(CongestionRabbitNames.EXCHANGE);
		assertThat(exchange.isDurable()).isTrue();
	}

	@Test
	void configuresMandatoryPublisher() {
		CachingConnectionFactory connectionFactory = new CachingConnectionFactory("localhost");
		RabbitTemplate rabbitTemplate = configuration.congestionRabbitTemplate(connectionFactory);

		assertThat(rabbitTemplate.isMandatoryFor(new Message(new byte[0]))).isTrue();
		connectionFactory.destroy();
	}
}
