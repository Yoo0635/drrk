package com.drrk.main.auth;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class AuthConfiguration {

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	JwtTokenProvider jwtTokenProvider(AuthProperties properties, Clock clock) {
		return new JwtTokenProvider(properties.jwtSecret(), properties.accessTokenTtl(), clock);
	}

	@Bean
	RefreshTokenService refreshTokenService(
			RefreshSessionStore store,
			TokenGenerator tokenGenerator,
			Sha256TokenHasher tokenHasher,
			Clock clock,
			AuthProperties properties
	) {
		return new RefreshTokenService(store, tokenGenerator, tokenHasher, clock, properties.refreshTokenTtl());
	}
}
