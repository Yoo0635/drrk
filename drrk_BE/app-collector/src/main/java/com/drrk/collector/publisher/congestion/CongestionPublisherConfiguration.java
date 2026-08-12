package com.drrk.collector.publisher.congestion;

import com.drrk.messaging.congestion.CongestionRabbitNames;
import java.time.Duration;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
public class CongestionPublisherConfiguration {

	@Bean
	DirectExchange congestionExchange() {
		return new DirectExchange(CongestionRabbitNames.EXCHANGE, true, false);
	}

	@Bean
	RabbitTemplate congestionRabbitTemplate(ConnectionFactory connectionFactory) {
		RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
		rabbitTemplate.setMandatory(true);
		return rabbitTemplate;
	}

	@Bean
	CongestionMessagePublisher congestionMessagePublisher(
			RabbitTemplate congestionRabbitTemplate,
			ObjectMapper objectMapper,
			@Value("${congestion.publisher.confirm-timeout:PT2S}") Duration confirmTimeout
	) {
		return new RabbitCongestionMessagePublisher(congestionRabbitTemplate, objectMapper, confirmTimeout);
	}
}
