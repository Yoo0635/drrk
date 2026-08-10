package com.drrk.domain.user;

import static jakarta.persistence.EnumType.STRING;
import static jakarta.persistence.GenerationType.IDENTITY;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

	@Id
	@GeneratedValue(strategy = IDENTITY)
	@Column(name = "id", nullable = false)
	private Long id;

	@Column(name = "email", nullable = false, length = 320)
	private String email;

	@Column(name = "password", nullable = true, length = 255)
	private String password;

	@Enumerated(STRING)
	@Column(name = "login_type", nullable = false, length = 20)
	private LoginType loginType;

	@Column(name = "provider_user_id", nullable = true, length = 255)
	private String providerUserId;

	@Column(name = "nickname", nullable = false, length = 100)
	private String nickname;

	@Enumerated(STRING)
	@Column(name = "status", nullable = false, length = 20)
	private UserStatus status;

	@Column(name = "email_verified_at", nullable = true)
	private LocalDateTime emailVerifiedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "deleted_at", nullable = true)
	private LocalDateTime deletedAt;
}
