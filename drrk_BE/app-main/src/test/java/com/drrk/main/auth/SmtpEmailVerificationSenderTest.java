package com.drrk.main.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.drrk.global.error.BusinessException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

class SmtpEmailVerificationSenderTest {

	@Test
	void sendsVerificationCodeWithoutLoggingOrReturningIt() {
		JavaMailSender mailSender = org.mockito.Mockito.mock(JavaMailSender.class);
		SmtpEmailVerificationSender sender = new SmtpEmailVerificationSender(mailSender, "ieum@example.com");

		sender.send("member@example.com", "123456");

		ArgumentCaptor<SimpleMailMessage> message = ArgumentCaptor.forClass(SimpleMailMessage.class);
		verify(mailSender).send(message.capture());
		assertEquals("ieum@example.com", message.getValue().getFrom());
		assertEquals("member@example.com", message.getValue().getTo()[0]);
		assertEquals("[드르륵] 이메일 인증번호", message.getValue().getSubject());
		assertTrue(message.getValue().getText().contains("123456"));
	}

	@Test
	void convertsMailFailureToAuthError() {
		JavaMailSender mailSender = org.mockito.Mockito.mock(JavaMailSender.class);
		doThrow(new MailSendException("smtp unavailable")).when(mailSender).send(any(SimpleMailMessage.class));
		SmtpEmailVerificationSender sender = new SmtpEmailVerificationSender(mailSender, "ieum@example.com");

		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> sender.send("member@example.com", "123456")
		);

		assertEquals(AuthErrorCode.EMAIL_DELIVERY_FAILED, exception.getErrorCode());
	}
}
