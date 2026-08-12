package com.drrk.main.auth;

import com.drrk.global.error.BusinessException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

public class SensitiveSessionFilter extends OncePerRequestFilter {

	private static final String USERS_ME_PATH = "/api/v1/users/me";

	private final RefreshSessionStore refreshSessionStore;
	private final HandlerExceptionResolver exceptionResolver;

	public SensitiveSessionFilter(RefreshSessionStore refreshSessionStore, HandlerExceptionResolver exceptionResolver) {
		this.refreshSessionStore = refreshSessionStore;
		this.exceptionResolver = exceptionResolver;
	}

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain
	) throws ServletException, IOException {
		if (!isSensitive(request)) {
			filterChain.doFilter(request, response);
			return;
		}

		try {
			Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
			Object principal = authentication == null ? null : authentication.getPrincipal();
			if (!(principal instanceof AuthenticatedUser user)) {
				throw new BusinessException(AuthErrorCode.INVALID_ACCESS_TOKEN);
			}

			try {
				if (!refreshSessionStore.existsSession(user.sessionId(), user.userId())) {
					throw new BusinessException(AuthErrorCode.AUTH_SESSION_REVOKED);
				}
			} catch (RedisConnectionFailureException exception) {
				throw new BusinessException(AuthErrorCode.AUTH_SESSION_CHECK_UNAVAILABLE, exception);
			}
		} catch (BusinessException exception) {
			exceptionResolver.resolveException(request, response, null, exception);
			return;
		}

		filterChain.doFilter(request, response);
	}

	private static boolean isSensitive(HttpServletRequest request) {
		return "GET".equals(request.getMethod()) && USERS_ME_PATH.equals(request.getRequestURI());
	}
}
