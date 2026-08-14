package com.drrk.collector.client.airport;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 공공데이터포털 serviceKey 정규화.
 *
 * <p>포털은 같은 키를 Encoding 키({@code aB%2Bc%3D})와 Decoding 키({@code aB+c=}) 두 형태로 발급한다.
 * 어느 쪽을 넣어도 최종 요청에서 정확히 한 번만 인코딩되도록 맞춘다. 이 처리를 빠뜨리면
 * {@code %2B} 가 {@code %252B} 로 이중 인코딩되거나 {@code +} 가 공백으로 해석돼 403이 난다.</p>
 */
public final class AirportServiceKeys {

	private AirportServiceKeys() {
	}

	public static String encode(String key) {
		if (key == null || key.isBlank()) {
			return "";
		}
		String raw = key.trim();
		if (raw.contains("%")) {
			try {
				raw = URLDecoder.decode(raw, StandardCharsets.UTF_8);
			} catch (IllegalArgumentException exception) {
				// 이미 디코딩된 키에 %가 들어 있는 비정상 케이스 — 원문 그대로 인코딩한다.
				raw = key.trim();
			}
		}
		return URLEncoder.encode(raw, StandardCharsets.UTF_8);
	}
}
