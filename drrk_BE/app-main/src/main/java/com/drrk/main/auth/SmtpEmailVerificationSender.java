package com.drrk.main.auth;

import com.drrk.global.error.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class SmtpEmailVerificationSender implements EmailVerificationSender {

	private final JavaMailSender mailSender;
	private final String from;

	public SmtpEmailVerificationSender(JavaMailSender mailSender, @Value("${spring.mail.username}") String from) {
		this.mailSender = mailSender;
		this.from = from;
	}

	@Override
	public void send(String email, String code) {
		SimpleMailMessage message = new SimpleMailMessage();
		message.setFrom(from);
		message.setTo(email);
		message.setSubject("[드르륵] 이메일 인증번호");
		message.setText("이메일 인증번호는 %s입니다.\n5분 이내에 입력해주세요.".formatted(code));

		try {
			mailSender.send(message);
		} catch (MailException exception) {
			throw new BusinessException(AuthErrorCode.EMAIL_DELIVERY_FAILED, exception);
		}
	}
}
