package com.drrk.main.auth;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.HandlerExceptionResolver;

@Configuration
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(
			HttpSecurity http,
			JwtTokenProvider jwtTokenProvider,
			RefreshSessionStore refreshSessionStore,
			HandlerExceptionResolver handlerExceptionResolver
	) throws Exception {
		return http
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.cors(Customizer.withDefaults())
				.csrf(csrf -> csrf
						.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
						.ignoringRequestMatchers("/api/v1/auth/login",
								"/api/v1/auth/signup", "/api/v1/auth/email-verifications",
								"/api/v1/auth/email-verifications/confirm"))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
						.requestMatchers(HttpMethod.GET, "/healthz").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/v1/auth/csrf").permitAll()
						.requestMatchers(HttpMethod.GET,
								"/api/v1/platform/congestion",
								"/api/v1/routes/recommendation",
								"/api/v1/airport-railroad/arrivals",
								"/api/v1/inference/carriers/stream").permitAll()
						.requestMatchers(HttpMethod.POST, "/api/v1/auth/**").permitAll()
						.anyRequest().authenticated())
				.addFilterBefore(
						new CookieJwtAuthenticationFilter(jwtTokenProvider, handlerExceptionResolver),
						UsernamePasswordAuthenticationFilter.class)
				.addFilterAfter(
						new SensitiveSessionFilter(refreshSessionStore, handlerExceptionResolver),
						CookieJwtAuthenticationFilter.class)
				.build();
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource(AuthProperties properties) {
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(properties.corsAllowedOrigins());
		configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		configuration.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN"));
		configuration.setAllowCredentials(true);

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", configuration);
		return source;
	}
}
