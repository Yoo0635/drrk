package com.drrk.collector.congestion;

import com.drrk.messaging.congestion.CongestionCalculatedMessage;

public interface CongestionCalculator {

	CongestionCalculatedMessage calculate(CongestionInputs inputs);
}
