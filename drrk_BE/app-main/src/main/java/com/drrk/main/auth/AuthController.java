package com.drrk.main.auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

	private final EmailVerificationService emailVerificationService;
	private final AuthService authService;
	private final CookieService cookieService;

	public AuthController(
			EmailVerificationService emailVerificationService,
			AuthService authService,
			CookieService cookieService
	) {
		this.emailVerificationService = emailVerificationService;
		this.authService = authService;
		this.cookieService = cookieService;
	}

	@GetMapping("/csrf")
	public CsrfResponse csrf(@RequestAttribute("_csrf") CsrfToken csrfToken) {
		return new CsrfResponse(csrfToken.getToken());
	}

	@PostMapping("/email-verifications")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void sendEmailVerification(@Valid @RequestBody EmailVerificationRequest request) {
		emailVerificationService.sendCode(request);
	}

	@PostMapping("/email-verifications/confirm")
	public EmailVerificationConfirmResponse confirmEmailVerification(
			@Valid @RequestBody EmailVerificationConfirmRequest request
	) {
		return emailVerificationService.confirm(request);
	}

	@PostMapping("/signup")
	@ResponseStatus(HttpStatus.CREATED)
	public void signup(@Valid @RequestBody SignupRequest request) {
		authService.signup(request);
	}

	@PostMapping("/login")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void login(
			@Valid @RequestBody LoginRequest request,
			HttpServletResponse response
	) {
		LoginResult result = authService.login(request);
		cookieService.addAuthCookies(response, result.accessToken(), result.refreshToken());
	}

	@PostMapping("/refresh")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void refresh(HttpServletRequest request, HttpServletResponse response) {
		RefreshResult result = authService.refresh(readCookie(request, CookieService.REFRESH_TOKEN_COOKIE));
		cookieService.addAuthCookies(response, result.accessToken(), result.refreshToken());
	}

	@PostMapping("/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void logout(HttpServletRequest request, HttpServletResponse response) {
		authService.logout(readCookie(request, CookieService.REFRESH_TOKEN_COOKIE));
		cookieService.clearAuthCookies(response);
	}

	private static String readCookie(HttpServletRequest request, String name) {
		Cookie[] cookies = request.getCookies();
		if (cookies == null) {
			return null;
		}
		for (Cookie cookie : cookies) {
			if (name.equals(cookie.getName())) {
				return cookie.getValue();
			}
		}
		return null;
	}

	public record CsrfResponse(String token) {
	}
}
