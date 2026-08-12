package com.drrk.main;

import com.drrk.main.auth.EmailVerificationStore;
import com.drrk.main.auth.LoginAttemptStore;
import com.drrk.main.auth.RefreshSessionStore;
import com.drrk.main.auth.UserAccountRepository;
import com.drrk.main.consumer.inference.InferenceMessageReceiptStore;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
		"spring.autoconfigure.exclude="
				+ "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
				+ "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration,"
				+ "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
		"spring.mail.host=smtp.example.com",
		"spring.mail.port=587",
		"spring.mail.username=test@example.com",
		"spring.mail.password=test-password",
		"inference.consumer.auto-startup=false",
		"auth.jwt-secret=MTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTI="
})
class MainApplicationTests {

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

	@Test
	void contextLoads() {
	}

}
