package com.drrk.main.consumer.inference;

public record InferenceEvent(
		double t,
		double dur,
		int count,
		double conf,
		double snr
) {
}
