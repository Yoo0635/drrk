package com.drrk.main.auth;

import java.time.Duration;
import java.util.Optional;

public interface EmailVerificationStore {

	void saveCode(String email, String codeHash, Duration ttl);

	Optional<String> findCodeHash(String email);

	void deleteCode(String email);

	void saveSignupTicket(String ticket, String email, Duration ttl);

	Optional<String> consumeSignupTicket(String ticket);
}
