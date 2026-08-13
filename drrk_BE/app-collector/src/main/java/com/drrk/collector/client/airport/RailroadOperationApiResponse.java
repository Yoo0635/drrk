package com.drrk.collector.client.airport;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RailroadOperationApiResponse(Response response) {

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Response(AirportApiHeader header, Body body) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Body(List<Item> items) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Item(String trnNo, String stnCd, String planArrvDttm, String accomArrvDttm, String trnClsNm) {
	}
}
