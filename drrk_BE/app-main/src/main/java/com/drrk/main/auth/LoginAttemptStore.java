package com.drrk.main.auth;

import java.time.Duration;

public interface LoginAttemptStore {

	boolean isLocked(String email);

	int recordFailure(String email, Duration ttl);

	void clear(String email);
}
