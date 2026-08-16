package com.drrk.main.auth;

public interface EmailVerificationSender {

	void send(String email, String code);
}
