package com.drrk.main.controller;

import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.drrk.main.auth.EmailVerificationStore;
import com.drrk.main.auth.LoginAttemptStore;
import com.drrk.main.auth.RefreshSessionStore;
import com.drrk.main.auth.UserAccountRepository;
import com.drrk.main.consumer.inference.InferenceMessageReceiptStore;
import java.sql.Connection;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
		"spring.autoconfigure.exclude="
				+ "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
				+ "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration,"
				+ "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
				+ "org.springframework.boot.data.redis.autoconfigure.DataRedisReactiveAutoConfiguration",
		"spring.mail.host=smtp.example.com",
		"spring.mail.port=587",
		"spring.mail.username=test@example.com",
		"spring.mail.password=test-password",
		"inference.consumer.auto-startup=false",
		"congestion.consumer.auto-startup=false",
		"auth.jwt-secret=MTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTI="
})
@AutoConfigureMockMvc
class HealthControllerSecurityTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	UserAccountRepository userAccountRepository;

	@MockitoBean
	EmailVerificationStore emailVerificationStore;

	@MockitoBean
	RefreshSessionStore refreshSessionStore;

	@MockitoBean
	LoginAttemptStore loginAttemptStore;

	@MockitoBean
	InferenceMessageReceiptStore inferenceMessageReceiptStore;

	@MockitoBean
	DataSource dataSource;

	@MockitoBean
	RedisConnectionFactory redisConnectionFactory;

	@MockitoBean
	ConnectionFactory rabbitConnectionFactory;

	@BeforeEach
	void setUpReadinessMocks() throws Exception {
		Connection jdbcConnection = org.mockito.Mockito.mock(Connection.class);
		when(dataSource.getConnection()).thenReturn(jdbcConnection);
		when(jdbcConnection.isValid(2)).thenReturn(true);

		RedisConnection redisConnection = org.mockito.Mockito.mock(RedisConnection.class);
		when(redisConnectionFactory.getConnection()).thenReturn(redisConnection);
		when(redisConnection.ping()).thenReturn("PONG");

		org.springframework.amqp.rabbit.connection.Connection rabbitConnection =
				org.mockito.Mockito.mock(org.springframework.amqp.rabbit.connection.Connection.class);
		when(rabbitConnectionFactory.createConnection()).thenReturn(rabbitConnection);
		when(rabbitConnection.isOpen()).thenReturn(true);
	}

	@Test
	void exposesHealthzWithoutAuthenticationAndWithoutSecrets() throws Exception {
		mockMvc.perform(get("/healthz"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"))
				.andExpect(jsonPath("$", not(hasKey("password"))))
				.andExpect(jsonPath("$", not(hasKey("secret"))));
	}

	@Test
	void permitsCarrierCountStreamWithoutAuthentication() throws Exception {
		mockMvc.perform(get("/api/v1/inference/carriers/stream"))
				.andExpect(status().isOk());
	}

	@Test
	void permitsCorsPreflightForAuthenticatedEndpointsWithoutAuthentication() throws Exception {
		mockMvc.perform(options("/api/v1/users/me")
						.header(HttpHeaders.ORIGIN, "http://localhost:5173")
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"));
	}

	@Test
	void keepsAuthenticatedEndpointsProtectedForActualRequests() throws Exception {
		mockMvc.perform(get("/api/v1/users/me"))
				.andExpect(status().isUnauthorized());
	}
}
