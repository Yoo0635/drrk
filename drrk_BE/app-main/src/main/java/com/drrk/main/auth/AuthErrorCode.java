package com.drrk.main.auth;

import com.drrk.global.error.ErrorCode;

public enum AuthErrorCode implements ErrorCode {

	INVALID_CREDENTIALS(401, "AUTH-401-001", "이메일 또는 비밀번호가 올바르지 않습니다."),
	INVALID_ACCESS_TOKEN(401, "AUTH-401-002", "Access Token이 유효하지 않습니다."),
	INVALID_REFRESH_TOKEN(401, "AUTH-401-003", "Refresh Token이 유효하지 않습니다."),
	REFRESH_TOKEN_REUSED(401, "AUTH-401-004", "이미 사용된 Refresh Token입니다."),
	AUTH_SESSION_REVOKED(401, "AUTH-401-005", "인증 세션이 만료되었습니다."),
	EMAIL_VERIFICATION_REQUIRED(400, "AUTH-400-001", "이메일 인증이 필요합니다."),
	INVALID_EMAIL_VERIFICATION_CODE(400, "AUTH-400-002", "이메일 인증번호가 올바르지 않습니다."),
	INVALID_SIGNUP_TICKET(400, "AUTH-400-003", "가입 티켓이 유효하지 않습니다."),
	LOGIN_TEMPORARILY_LOCKED(423, "AUTH-423-001", "로그인 실패 횟수가 많아 일시적으로 잠겼습니다."),
	DUPLICATE_EMAIL(409, "AUTH-409-001", "이미 가입된 이메일입니다."),
	EMAIL_DELIVERY_FAILED(503, "AUTH-503-002", "이메일 인증번호를 발송할 수 없습니다."),
	AUTH_SESSION_CHECK_UNAVAILABLE(503, "AUTH-503-001", "인증 세션을 확인할 수 없습니다.");

	private final int status;
	private final String code;
	private final String message;

	AuthErrorCode(int status, String code, String message) {
		this.status = status;
		this.code = code;
		this.message = message;
	}

	@Override
	public int getStatus() {
		return status;
	}

	@Override
	public String getCode() {
		return code;
	}

	@Override
	public String getMessage() {
		return message;
	}
}
