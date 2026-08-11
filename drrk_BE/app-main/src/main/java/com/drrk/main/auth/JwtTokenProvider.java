package com.drrk.main.auth;

import com.drrk.global.error.BusinessException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class JwtTokenProvider {

	private static final String HMAC_ALGORITHM = "HmacSHA256";
	private static final String JWT_ALGORITHM = "HS256";

	private final byte[] secret;
	private final Duration accessTtl;
	private final Clock clock;

	public JwtTokenProvider(String base64Secret, Duration accessTtl, Clock clock) {
		this.secret = Base64.getDecoder().decode(base64Secret);
		if (secret.length < 32) {
			throw new IllegalArgumentException("JWT secret must be at least 32 bytes");
		}
		this.accessTtl = accessTtl;
		this.clock = clock;
	}

	public String createAccessToken(Long userId, String sessionId) {
		Instant now = clock.instant();
		Instant expiresAt = now.plus(accessTtl);
		Map<String, Object> header = Map.of(
				"alg", JWT_ALGORITHM,
				"typ", "JWT"
		);
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("sub", userId.toString());
		payload.put("sid", sessionId);
		payload.put("jti", UUID.randomUUID().toString());
		payload.put("token_type", "access");
		payload.put("iat", now.getEpochSecond());
		payload.put("exp", expiresAt.getEpochSecond());

		String unsignedToken = encodeJson(header) + "." + encodeJson(payload);
		return unsignedToken + "." + sign(unsignedToken);
	}

	public AccessTokenClaims parseAccessToken(String token) {
		try {
			String[] parts = token.split("\\.", -1);
			if (parts.length != 3) {
				throw new BusinessException(AuthErrorCode.INVALID_ACCESS_TOKEN);
			}
			String unsignedToken = parts[0] + "." + parts[1];
			if (!constantTimeEquals(sign(unsignedToken), parts[2])) {
				throw new BusinessException(AuthErrorCode.INVALID_ACCESS_TOKEN);
			}

			Map<String, String> payload = parseJsonObject(new String(decode(parts[1]), StandardCharsets.UTF_8));
			if (!"access".equals(payload.get("token_type"))) {
				throw new BusinessException(AuthErrorCode.INVALID_ACCESS_TOKEN);
			}

			Instant expiresAt = Instant.ofEpochSecond(Long.parseLong(payload.get("exp")));
			if (!expiresAt.isAfter(clock.instant())) {
				throw new BusinessException(AuthErrorCode.INVALID_ACCESS_TOKEN);
			}

			return new AccessTokenClaims(
					Long.valueOf(String.valueOf(payload.get("sub"))),
					payload.get("sid"),
					expiresAt
			);
		} catch (BusinessException exception) {
			throw exception;
		} catch (Exception exception) {
			throw new BusinessException(AuthErrorCode.INVALID_ACCESS_TOKEN, exception);
		}
	}

	private String encodeJson(Map<String, Object> value) {
		return encode(toJsonObject(value).getBytes(StandardCharsets.UTF_8));
	}

	private String sign(String unsignedToken) {
		try {
			Mac mac = Mac.getInstance(HMAC_ALGORITHM);
			mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
			return encode(mac.doFinal(unsignedToken.getBytes(StandardCharsets.UTF_8)));
		} catch (Exception exception) {
			throw new IllegalStateException("Failed to sign JWT", exception);
		}
	}

	private static String encode(byte[] value) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
	}

	private static byte[] decode(String value) {
		return Base64.getUrlDecoder().decode(value);
	}

	private static boolean constantTimeEquals(String expected, String actual) {
		return MessageDigestSupport.constantTimeEquals(
				expected.getBytes(StandardCharsets.UTF_8),
				actual.getBytes(StandardCharsets.UTF_8)
		);
	}

	private static String toJsonObject(Map<String, Object> value) {
		StringBuilder builder = new StringBuilder("{");
		boolean first = true;
		for (Map.Entry<String, Object> entry : value.entrySet()) {
			if (!first) {
				builder.append(',');
			}
			first = false;
			builder.append('"').append(escape(entry.getKey())).append('"').append(':');
			Object fieldValue = entry.getValue();
			if (fieldValue instanceof Number) {
				builder.append(fieldValue);
			} else {
				builder.append('"').append(escape(String.valueOf(fieldValue))).append('"');
			}
		}
		return builder.append('}').toString();
	}

	private static Map<String, String> parseJsonObject(String json) {
		Map<String, String> values = new LinkedHashMap<>();
		String body = json.substring(1, json.length() - 1);
		if (body.isBlank()) {
			return values;
		}
		for (String field : body.split(",")) {
			String[] parts = field.split(":", 2);
			String key = unquote(parts[0]);
			String value = parts[1].startsWith("\"") ? unquote(parts[1]) : parts[1];
			values.put(key, value);
		}
		return values;
	}

	private static String unquote(String value) {
		return value.substring(1, value.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\");
	}

	private static String escape(String value) {
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}
}
