package com.drrk.main.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.drrk.domain.user.User;
import com.drrk.global.error.BusinessException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class AuthServiceTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-11T01:00:00Z"), ZoneOffset.UTC);
	private static final String SECRET = java.util.Base64.getEncoder()
			.encodeToString("12345678901234567890123456789012".getBytes());

	@Test
	void signsUpEmailUserAfterConsumingVerifiedTicket() {
		FakeUserAccountRepository users = new FakeUserAccountRepository();
		FakeEmailVerificationStore emailStore = new FakeEmailVerificationStore();
		emailStore.tickets.put("ticket-1", "member@example.com");
		AuthService service = service(users, emailStore);

		service.signup(new SignupRequest(" Member@Example.com ", "password123!", "member", "ticket-1"));

		assertTrue(emailStore.consumedTickets.containsKey("ticket-1"));
		assertEquals("member@example.com", users.savedUser.getEmail());
		assertTrue(users.savedUser.getPassword().startsWith("$2"));
	}

	@Test
	void loginIssuesAccessAndRefreshTokensWithSameSessionId() {
		FakeUserAccountRepository users = new FakeUserAccountRepository();
		FakeEmailVerificationStore emailStore = new FakeEmailVerificationStore();
		String encodedPassword = new BCryptPasswordEncoder().encode("password123!");
		User user = User.emailUser("member@example.com", encodedPassword, "member", LocalDateTime.now());
		users.usersByEmail.put("member@example.com", user);
		users.ids.put(user, 1L);
		AuthService service = service(users, emailStore);

		LoginResult result = service.login(new LoginRequest(" MEMBER@example.com ", "password123!"));

		AccessTokenClaims claims = new JwtTokenProvider(SECRET, Duration.ofMinutes(15), CLOCK)
				.parseAccessToken(result.accessToken());
		assertEquals(1L, claims.userId());
		assertEquals(result.refreshSessionId(), claims.sessionId());
		assertEquals("refresh-token", result.refreshToken());
	}

	@Test
	void loginRejectsWrongPasswordWithoutLeakingReason() {
		FakeUserAccountRepository users = new FakeUserAccountRepository();
		String encodedPassword = new BCryptPasswordEncoder().encode("password123!");
		User user = User.emailUser("member@example.com", encodedPassword, "member", LocalDateTime.now());
		users.usersByEmail.put("member@example.com", user);
		AuthService service = service(users, new FakeEmailVerificationStore());

		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> service.login(new LoginRequest("member@example.com", "wrong-password"))
		);

		assertEquals(AuthErrorCode.INVALID_CREDENTIALS, exception.getErrorCode());
	}

	@Test
	void loginTemporarilyLocksEmailAfterFiveFailures() {
		FakeUserAccountRepository users = new FakeUserAccountRepository();
		FakeLoginAttemptStore loginAttempts = new FakeLoginAttemptStore();
		String encodedPassword = new BCryptPasswordEncoder().encode("password123!");
		User user = User.emailUser("member@example.com", encodedPassword, "member", LocalDateTime.now());
		users.usersByEmail.put("member@example.com", user);
		users.ids.put(user, 1L);
		AuthService service = service(users, new FakeEmailVerificationStore(), loginAttempts);

		for (int attempt = 0; attempt < 5; attempt++) {
			assertThrows(
					BusinessException.class,
					() -> service.login(new LoginRequest("member@example.com", "wrong-password"))
			);
		}

		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> service.login(new LoginRequest("member@example.com", "password123!"))
		);

		assertEquals(AuthErrorCode.LOGIN_TEMPORARILY_LOCKED, exception.getErrorCode());
	}

	@Test
	void loginClearsFailureStateWhenPasswordMatches() {
		FakeUserAccountRepository users = new FakeUserAccountRepository();
		FakeLoginAttemptStore loginAttempts = new FakeLoginAttemptStore();
		String encodedPassword = new BCryptPasswordEncoder().encode("password123!");
		User user = User.emailUser("member@example.com", encodedPassword, "member", LocalDateTime.now());
		users.usersByEmail.put("member@example.com", user);
		users.ids.put(user, 1L);
		loginAttempts.recordFailure("member@example.com", Duration.ofMinutes(15));
		AuthService service = service(users, new FakeEmailVerificationStore(), loginAttempts);

		service.login(new LoginRequest("member@example.com", "password123!"));

		assertTrue(loginAttempts.clearedEmails.containsKey("member@example.com"));
	}

	@Test
	void signupRejectsConsumedTicketAsInvalidSignupTicket() {
		AuthService service = service(new FakeUserAccountRepository(), new FakeEmailVerificationStore());

		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> service.signup(new SignupRequest("member@example.com", "password123!", "member", "used-ticket"))
		);

		assertEquals(AuthErrorCode.INVALID_SIGNUP_TICKET, exception.getErrorCode());
	}

	private static AuthService service(FakeUserAccountRepository users, FakeEmailVerificationStore emailStore) {
		return service(users, emailStore, new FakeLoginAttemptStore());
	}

	private static AuthService service(
			FakeUserAccountRepository users,
			FakeEmailVerificationStore emailStore,
			FakeLoginAttemptStore loginAttempts
	) {
		RefreshTokenService refreshTokens = new RefreshTokenService(
				new FakeRefreshSessionStore(),
				new FixedTokenGenerator("refresh-token"),
				new Sha256TokenHasher(),
				CLOCK,
				Duration.ofDays(14)
		);
		return new AuthService(
				users,
				emailStore,
				loginAttempts,
				new BCryptPasswordEncoder(),
				refreshTokens,
				new JwtTokenProvider(SECRET, Duration.ofMinutes(15), CLOCK),
				CLOCK
		);
	}

	private static final class FakeUserAccountRepository implements UserAccountRepository {

		private final Map<String, User> usersByEmail = new HashMap<>();
		private final Map<User, Long> ids = new HashMap<>();
		private User savedUser;

		@Override
		public boolean existsByEmail(String email) {
			return usersByEmail.containsKey(email);
		}

		@Override
		public Optional<User> findByEmail(String email) {
			return Optional.ofNullable(usersByEmail.get(email));
		}

		@Override
		public Optional<User> findById(Long id) {
			return ids.entrySet().stream()
					.filter(entry -> entry.getValue().equals(id))
					.map(Map.Entry::getKey)
					.findFirst();
		}

		@Override
		public User save(User user) {
			savedUser = user;
			usersByEmail.put(user.getEmail(), user);
			ids.put(user, 1L);
			return user;
		}

		@Override
		public Long idOf(User user) {
			return ids.get(user);
		}
	}

	private static final class FakeLoginAttemptStore implements LoginAttemptStore {

		private final Map<String, Integer> failuresByEmail = new HashMap<>();
		private final Map<String, Boolean> clearedEmails = new HashMap<>();

		@Override
		public boolean isLocked(String email) {
			return failuresByEmail.getOrDefault(email, 0) >= 5;
		}

		@Override
		public int recordFailure(String email, Duration ttl) {
			int failures = failuresByEmail.getOrDefault(email, 0) + 1;
			failuresByEmail.put(email, failures);
			return failures;
		}

		@Override
		public void clear(String email) {
			failuresByEmail.remove(email);
			clearedEmails.put(email, true);
		}
	}

	private static final class FakeEmailVerificationStore implements EmailVerificationStore {

		private final Map<String, String> tickets = new HashMap<>();
		private final Map<String, String> consumedTickets = new HashMap<>();

		@Override
		public void saveCode(String email, String codeHash, Duration ttl) {
		}

		@Override
		public Optional<String> findCodeHash(String email) {
			return Optional.empty();
		}

		@Override
		public void deleteCode(String email) {
		}

		@Override
		public void saveSignupTicket(String ticket, String email, Duration ttl) {
			tickets.put(ticket, email);
		}

		@Override
		public Optional<String> consumeSignupTicket(String ticket) {
			String email = tickets.remove(ticket);
			if (email != null) {
				consumedTickets.put(ticket, email);
			}
			return Optional.ofNullable(email);
		}
	}

	private static final class FakeRefreshSessionStore implements RefreshSessionStore {

		@Override
		public void save(String tokenHash, RefreshSession session, Duration ttl) {
		}

		@Override
		public Optional<RefreshSession> findActive(String tokenHash) {
			return Optional.empty();
		}

		@Override
		public Optional<UsedRefreshToken> findUsed(String tokenHash) {
			return Optional.empty();
		}

		@Override
		public boolean existsSession(String sessionId, Long userId) {
			return true;
		}

		@Override
		public boolean rotate(
				String previousTokenHash,
				String nextTokenHash,
				RefreshSession nextSession,
				UsedRefreshToken usedToken,
				Duration ttl
		) {
			return false;
		}

		@Override
		public void deleteSession(String sessionId) {
		}

		@Override
		public void deleteAllByUserId(Long userId) {
		}
	}

	private static final class FixedTokenGenerator implements TokenGenerator {

		private final java.util.Queue<String> tokens;

		private FixedTokenGenerator(String... tokens) {
			this.tokens = new java.util.ArrayDeque<>(java.util.Arrays.asList(tokens));
		}

		@Override
		public String generate() {
			return tokens.remove();
		}
	}
}
