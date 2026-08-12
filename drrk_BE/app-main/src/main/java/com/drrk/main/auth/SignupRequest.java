package com.drrk.main.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignupRequest(
		@NotBlank @Email String email,
		@NotBlank @BCryptPasswordSize(min = 8) String password,
		@NotBlank @Size(max = 100) String nickname,
		@NotBlank String signupTicket
) {
}
