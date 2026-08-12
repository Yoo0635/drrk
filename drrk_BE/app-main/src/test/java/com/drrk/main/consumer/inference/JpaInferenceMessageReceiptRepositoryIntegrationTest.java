package com.drrk.main.consumer.inference;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@DataJpaTest(properties = {
		"spring.jpa.hibernate.ddl-auto=none",
		"spring.sql.init.mode=always"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "INFERENCE_TEST_DB_URL", matches = ".+")
class JpaInferenceMessageReceiptRepositoryIntegrationTest {

	private static final InferenceWindowMessage MESSAGE = new InferenceWindowMessage(
			"8c530c6c-f819-4ad6-b687-760dc698c617",
			"desk01",
			1755000000.0,
			10,
			List.of(
					new InferenceEvent(1754999993.2, 3.4, 2, 0.81, 24.6),
					new InferenceEvent(1754999997.8, 2.9, 1, 0.93, 21.2)
			),
			2,
			3,
			0.42,
			null
	);
	private static final String RAW_PAYLOAD = """
			{"message_id":"8c530c6c-f819-4ad6-b687-760dc698c617","space_id":"desk01"}
			""";

	@Autowired
	private JpaInferenceMessageReceiptRepository repository;

	@DynamicPropertySource
	static void databaseProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", () -> System.getenv("INFERENCE_TEST_DB_URL"));
		registry.add("spring.datasource.username", () -> System.getenv("INFERENCE_TEST_DB_USER"));
		registry.add("spring.datasource.password", () -> System.getenv("INFERENCE_TEST_DB_PASSWORD"));
	}

	@Test
	void insertsTheFirstMessageAndIgnoresTheSameMessageIdAtomically() {
		Instant receivedAt = Instant.parse("2026-08-13T01:30:00Z");

		boolean firstInsert = repository.insertIfAbsent(MESSAGE, RAW_PAYLOAD, receivedAt);
		boolean duplicateInsert = repository.insertIfAbsent(MESSAGE, RAW_PAYLOAD, receivedAt.plusSeconds(1));

		assertThat(firstInsert).isTrue();
		assertThat(duplicateInsert).isFalse();
	}
}
