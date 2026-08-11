package com.drrk.main.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import com.drrk.global.error.BusinessException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

public class CookieJwtAuthenticationFilter extends OncePerRequestFilter {

	private final JwtTokenProvider jwtTokenProvider;
	private final HandlerExceptionResolver exceptionResolver;

	public CookieJwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider, HandlerExceptionResolver exceptionResolver) {
		this.jwtTokenProvider = jwtTokenProvider;
		this.exceptionResolver = exceptionResolver;
	}

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain
	) throws ServletException, IOException {
		try {
			String token = readCookie(request, CookieService.ACCESS_TOKEN_COOKIE);
			if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
				AccessTokenClaims claims = jwtTokenProvider.parseAccessToken(token);
				AuthenticatedUser principal = new AuthenticatedUser(claims.userId(), claims.sessionId());
				UsernamePasswordAuthenticationToken authentication =
						new UsernamePasswordAuthenticationToken(principal, null, List.of());
				SecurityContextHolder.getContext().setAuthentication(authentication);
			}
		} catch (BusinessException exception) {
			exceptionResolver.resolveException(request, response, null, exception);
			return;
		}
		filterChain.doFilter(request, response);
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
}
