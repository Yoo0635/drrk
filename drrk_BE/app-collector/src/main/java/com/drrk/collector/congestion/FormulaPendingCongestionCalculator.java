package com.drrk.collector.congestion;

import com.drrk.messaging.congestion.CongestionCalculatedMessage;
import com.drrk.messaging.congestion.CongestionInputReferences;
import java.time.Clock;
import java.util.UUID;
import java.util.function.Supplier;

public class FormulaPendingCongestionCalculator implements CongestionCalculator {

	private final Clock clock;
	private final Supplier<UUID> messageIdSupplier;

	public FormulaPendingCongestionCalculator(Clock clock, Supplier<UUID> messageIdSupplier) {
		this.clock = clock;
		this.messageIdSupplier = messageIdSupplier;
	}

	@Override
	public CongestionCalculatedMessage calculate(CongestionInputs inputs) {
		CongestionInputReferences references = new CongestionInputReferences(
				inputs.arrivalStatus().collectedAt(),
				inputs.arrivalStatus().items().size(),
				inputs.passengerForecast().collectedAt(),
				inputs.passengerForecast().items().size(),
				inputs.railroadOperation().collectedAt(),
				inputs.railroadOperation().items().size(),
				inputs.modelMeasurement().messageId(),
				inputs.modelMeasurement().measuredAt()
		);
		return CongestionCalculatedMessage.formulaPending(messageIdSupplier.get(), clock.instant(), references);
	}
}
