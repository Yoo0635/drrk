package com.drrk.collector.client.airport;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * 승객예고 API는 다른 두 공항 API와 달리 {@code response} 래퍼 없이
 * {@code {"header":…, "body":…}} 를 최상위로 내려준다.
 * 운영 중 래퍼가 붙어 내려오는 경우도 있어 두 형태를 모두 받는다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PassengerForecastApiResponse(
		Response response,
		AirportApiHeader header,
		Body body
) {

	/**
	 * 래퍼 유무와 무관하게 헤더를 돌려준다.
	 */
	public AirportApiHeader effectiveHeader() {
		if (response != null && response.header() != null) {
			return response.header();
		}
		return header;
	}

	/**
	 * 래퍼 유무와 무관하게 본문을 돌려준다.
	 */
	public Body effectiveBody() {
		if (response != null && response.body() != null) {
			return response.body();
		}
		return body;
	}

	public List<Item> items() {
		Body effectiveBody = effectiveBody();
		if (effectiveBody == null || effectiveBody.items() == null || effectiveBody.items().item() == null) {
			return List.of();
		}
		return effectiveBody.items().item();
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Response(AirportApiHeader header, Body body) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Body(Items items) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Items(List<Item> item) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Item(String adate, String atime, String t1eg1) {
	}
}
