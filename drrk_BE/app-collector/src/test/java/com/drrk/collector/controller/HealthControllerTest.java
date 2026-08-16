package com.drrk.collector.controller;

import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class HealthControllerTest {

	private MockMvc mockMvc;
	private ConnectionFactory rabbitConnectionFactory;

	@BeforeEach
	void setUp() {
		rabbitConnectionFactory = mock(ConnectionFactory.class);
		org.springframework.amqp.rabbit.connection.Connection rabbitConnection =
				mock(org.springframework.amqp.rabbit.connection.Connection.class);
		when(rabbitConnectionFactory.createConnection()).thenReturn(rabbitConnection);
		when(rabbitConnection.isOpen()).thenReturn(true);

		mockMvc = MockMvcBuilders.standaloneSetup(new HealthController(rabbitConnectionFactory)).build();
	}

	@Test
	void returnsUpStatusWithoutSecrets() throws Exception {
		mockMvc.perform(get("/healthz"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"))
				.andExpect(jsonPath("$", not(hasKey("password"))))
				.andExpect(jsonPath("$", not(hasKey("secret"))));
	}

	@Test
	void returnsServiceUnavailableWhenRabbitMqIsUnreachable() throws Exception {
		when(rabbitConnectionFactory.createConnection()).thenThrow(new IllegalStateException("rabbit down"));

		mockMvc.perform(get("/healthz"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.status").value("DOWN"))
				.andExpect(jsonPath("$", not(hasKey("password"))))
				.andExpect(jsonPath("$", not(hasKey("secret"))));
	}
}
