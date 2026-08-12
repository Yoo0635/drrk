package com.drrk.main.auth;

import com.drrk.global.error.BusinessException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class EmailVerificationService {

	private static final Duration CODE_TTL = Duration.ofMinutes(5);
	private static final Duration TICKET_TTL = Duration.ofMinutes(30);

	private final EmailVerificationStore store;
	private final UserAccountRepository users;
	private final PasswordEncoder passwordEncoder;
	private final EmailVerificationSender sender;
	private final SecureRandom secureRandom = new SecureRandom();

	public EmailVerificationService(
			EmailVerificationStore store,
			UserAccountRepository users,
			PasswordEncoder passwordEncoder,
			EmailVerificationSender sender
	) {
		this.store = store;
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.sender = sender;
	}

	public void sendCode(EmailVerificationRequest request) {
		String email = normalizeEmail(request.email());
		if (users.existsByEmail(email)) {
			throw new BusinessException(AuthErrorCode.DUPLICATE_EMAIL);
		}
		String code = "%06d".formatted(secureRandom.nextInt(1_000_000));
		store.saveCode(email, passwordEncoder.encode(code), CODE_TTL);
		try {
			sender.send(email, code);
		} catch (RuntimeException exception) {
			store.deleteCode(email);
			throw exception;
		}
	}

	public EmailVerificationConfirmResponse confirm(EmailVerificationConfirmRequest request) {
		String email = normalizeEmail(request.email());
		String codeHash = store.findCodeHash(email)
				.orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_EMAIL_VERIFICATION_CODE));
		if (!passwordEncoder.matches(request.code(), codeHash)) {
			throw new BusinessException(AuthErrorCode.INVALID_EMAIL_VERIFICATION_CODE);
		}
		store.deleteCode(email);
		String ticket = UUID.randomUUID().toString();
		store.saveSignupTicket(ticket, email, TICKET_TTL);
		return new EmailVerificationConfirmResponse(ticket);
	}

	private static String normalizeEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}
}
