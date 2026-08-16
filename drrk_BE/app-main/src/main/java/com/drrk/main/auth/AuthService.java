package com.drrk.main.auth;

import com.drrk.domain.user.LoginType;
import com.drrk.domain.user.User;
import com.drrk.domain.user.UserStatus;
import com.drrk.global.error.BusinessException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

	private static final int MAX_LOGIN_FAILURES = 5;
	private static final java.time.Duration LOGIN_FAILURE_TTL = java.time.Duration.ofMinutes(15);

	private final UserAccountRepository users;
	private final EmailVerificationStore emailVerificationStore;
	private final LoginAttemptStore loginAttempts;
	private final PasswordEncoder passwordEncoder;
	private final RefreshTokenService refreshTokens;
	private final JwtTokenProvider jwtTokenProvider;
	private final Clock clock;

	public AuthService(
			UserAccountRepository users,
			EmailVerificationStore emailVerificationStore,
			LoginAttemptStore loginAttempts,
			PasswordEncoder passwordEncoder,
			RefreshTokenService refreshTokens,
			JwtTokenProvider jwtTokenProvider,
			Clock clock
	) {
		this.users = users;
		this.emailVerificationStore = emailVerificationStore;
		this.loginAttempts = loginAttempts;
		this.passwordEncoder = passwordEncoder;
		this.refreshTokens = refreshTokens;
		this.jwtTokenProvider = jwtTokenProvider;
		this.clock = clock;
	}

	@Transactional
	public void signup(SignupRequest request) {
		String email = normalizeEmail(request.email());
		if (users.existsByEmail(email)) {
			throw new BusinessException(AuthErrorCode.DUPLICATE_EMAIL);
		}
		String ticketEmail = emailVerificationStore.consumeSignupTicket(request.signupTicket())
				.orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_SIGNUP_TICKET));
		if (!email.equals(ticketEmail)) {
			throw new BusinessException(AuthErrorCode.INVALID_SIGNUP_TICKET);
		}

		User user = User.emailUser(
				email,
				passwordEncoder.encode(request.password()),
				request.nickname().trim(),
				LocalDateTime.ofInstant(clock.instant(), java.time.ZoneOffset.UTC)
		);
		users.save(user);
	}

	@Transactional(readOnly = true)
	public LoginResult login(LoginRequest request) {
		String email = normalizeEmail(request.email());
		if (loginAttempts.isLocked(email)) {
			throw new BusinessException(AuthErrorCode.LOGIN_TEMPORARILY_LOCKED);
		}
		User user = users.findByEmail(email)
				.filter(candidate -> candidate.getLoginType() == LoginType.EMAIL)
				.filter(candidate -> candidate.getStatus() == UserStatus.ACTIVE)
				.orElse(null);
		if (user == null || !passwordEncoder.matches(request.password(), user.getPassword())) {
			int failures = loginAttempts.recordFailure(email, LOGIN_FAILURE_TTL);
			if (failures >= MAX_LOGIN_FAILURES) {
				throw new BusinessException(AuthErrorCode.LOGIN_TEMPORARILY_LOCKED);
			}
			throw new BusinessException(AuthErrorCode.INVALID_CREDENTIALS);
		}
		loginAttempts.clear(email);
		Long userId = users.idOf(user);
		IssuedRefreshToken refreshToken = refreshTokens.issue(userId);
		String accessToken = jwtTokenProvider.createAccessToken(userId, refreshToken.sessionId());

		return new LoginResult(accessToken, refreshToken.token(), refreshToken.sessionId());
	}

	public RefreshResult refresh(String refreshToken) {
		RotatedRefreshToken rotated = refreshTokens.rotate(refreshToken);
		String accessToken = jwtTokenProvider.createAccessToken(rotated.userId(), rotated.sessionId());
		return new RefreshResult(accessToken, rotated.token());
	}

	public void logout(String refreshToken) {
		if (refreshToken != null && !refreshToken.isBlank()) {
			refreshTokens.deleteByRefreshToken(refreshToken);
		}
	}

	@Transactional(readOnly = true)
	public CurrentUserResponse currentUser(AuthenticatedUser authenticatedUser) {
		User user = users.findById(authenticatedUser.userId())
				.filter(candidate -> candidate.getStatus() == UserStatus.ACTIVE)
				.orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_ACCESS_TOKEN));

		return new CurrentUserResponse(users.idOf(user), user.getEmail(), user.getNickname());
	}

	private static String normalizeEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}
}
