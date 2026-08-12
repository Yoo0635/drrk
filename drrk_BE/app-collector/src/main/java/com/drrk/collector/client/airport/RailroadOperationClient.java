package com.drrk.collector.client.airport;

import com.drrk.collector.congestion.RailroadOperationSnapshot;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

public class RailroadOperationClient {

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
	private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;

	private final RestClient restClient;
	private final AirportApiProperties properties;
	private final RailroadOperationMapper mapper;
	private final Clock clock;

	public RailroadOperationClient(
			RestClient.Builder builder,
			AirportApiProperties properties,
			RailroadOperationMapper mapper,
			Clock clock
	) {
		this.restClient = builder.baseUrl(properties.railroad().url()).build();
		this.properties = properties;
		this.mapper = mapper;
		this.clock = clock;
	}

	public RailroadOperationSnapshot fetch() {
		RailroadOperationApiResponse response = restClient.get()
				.uri(uriBuilder -> buildUri(uriBuilder).build())
				.retrieve()
				.body(RailroadOperationApiResponse.class);
		return mapper.map(response, clock.instant());
	}

	private UriBuilder buildUri(UriBuilder uriBuilder) {
		AirportApiProperties.RailroadSource railroad = properties.railroad();
		uriBuilder.path("/getAirportRailroad")
				.queryParam("pageNo", 1)
				.queryParam("numOfRows", properties.numOfRows())
				.queryParam("type", "json")
				.queryParam("drvDt", LocalDate.now(clock.withZone(SEOUL)).format(DATE_FORMAT));
		if (StringUtils.hasText(railroad.trainClass())) {
			uriBuilder.queryParam("trainClsf", railroad.trainClass());
		}
		if (StringUtils.hasText(railroad.stationCode())) {
			uriBuilder.queryParam("stnCd", railroad.stationCode());
		}
		return uriBuilder.queryParam("serviceKey", railroad.key());
	}
}
