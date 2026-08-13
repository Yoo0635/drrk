package com.drrk.main.controller;

import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Connection;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class HealthControllerTest {

	private DataSource dataSource;
	private RedisConnectionFactory redisConnectionFactory;
	private ConnectionFactory rabbitConnectionFactory;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() throws Exception {
		dataSource = mock(DataSource.class);
		Connection jdbcConnection = mock(Connection.class);
		when(dataSource.getConnection()).thenReturn(jdbcConnection);
		when(jdbcConnection.isValid(2)).thenReturn(true);

		redisConnectionFactory = mock(RedisConnectionFactory.class);
		RedisConnection redisConnection = mock(RedisConnection.class);
		when(redisConnectionFactory.getConnection()).thenReturn(redisConnection);
		when(redisConnection.ping()).thenReturn("PONG");

		rabbitConnectionFactory = mock(ConnectionFactory.class);
		org.springframework.amqp.rabbit.connection.Connection rabbitConnection =
				mock(org.springframework.amqp.rabbit.connection.Connection.class);
		when(rabbitConnectionFactory.createConnection()).thenReturn(rabbitConnection);
		when(rabbitConnection.isOpen()).thenReturn(true);

		mockMvc = MockMvcBuilders.standaloneSetup(
				new HealthController(dataSource, redisConnectionFactory, rabbitConnectionFactory)
		).build();
	}

	@Test
	void returnsUpWhenRequiredDependenciesAreReachable() throws Exception {
		mockMvc.perform(get("/healthz"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"))
				.andExpect(jsonPath("$", not(hasKey("password"))))
				.andExpect(jsonPath("$", not(hasKey("secret"))));
	}

	@Test
	void returnsServiceUnavailableWhenAnyRequiredDependencyFails() throws Exception {
		when(redisConnectionFactory.getConnection()).thenThrow(new IllegalStateException("redis down"));

		mockMvc.perform(get("/healthz"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.status").value("DOWN"))
				.andExpect(jsonPath("$", not(hasKey("password"))))
				.andExpect(jsonPath("$", not(hasKey("secret"))));
	}
}
