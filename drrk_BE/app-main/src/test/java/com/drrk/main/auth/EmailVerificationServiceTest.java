package com.drrk.main.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.drrk.global.error.BusinessException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class EmailVerificationServiceTest {

	@Test
	void deletesSavedCodeWhenEmailDeliveryFails() {
		FakeEmailVerificationStore store = new FakeEmailVerificationStore();
		EmailVerificationService service = new EmailVerificationService(
				store,
				new EmptyUserAccountRepository(),
				new BCryptPasswordEncoder(),
				(email, code) -> {
					throw new BusinessException(AuthErrorCode.EMAIL_DELIVERY_FAILED);
				}
		);

		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> service.sendCode(new EmailVerificationRequest(" Member@Example.com "))
		);

		assertEquals(AuthErrorCode.EMAIL_DELIVERY_FAILED, exception.getErrorCode());
		assertTrue(store.deletedCodes.containsKey("member@example.com"));
	}

	private static final class FakeEmailVerificationStore implements EmailVerificationStore {

		private final Map<String, String> codeHashes = new HashMap<>();
		private final Map<String, String> deletedCodes = new HashMap<>();

		@Override
		public void saveCode(String email, String codeHash, Duration ttl) {
			codeHashes.put(email, codeHash);
		}

		@Override
		public Optional<String> findCodeHash(String email) {
			return Optional.ofNullable(codeHashes.get(email));
		}

		@Override
		public void deleteCode(String email) {
			deletedCodes.put(email, codeHashes.remove(email));
		}

		@Override
		public void saveSignupTicket(String ticket, String email, Duration ttl) {
		}

		@Override
		public Optional<String> consumeSignupTicket(String ticket) {
			return Optional.empty();
		}
	}

	private static final class EmptyUserAccountRepository implements UserAccountRepository {

		@Override
		public boolean existsByEmail(String email) {
			return false;
		}

		@Override
		public Optional<com.drrk.domain.user.User> findByEmail(String email) {
			return Optional.empty();
		}

		@Override
		public Optional<com.drrk.domain.user.User> findById(Long id) {
			return Optional.empty();
		}

		@Override
		public com.drrk.domain.user.User save(com.drrk.domain.user.User user) {
			return user;
		}

		@Override
		public Long idOf(com.drrk.domain.user.User user) {
			return 1L;
		}
	}
}
