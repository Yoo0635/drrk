package com.drrk.collector.consumer.inference;

public record InferenceEvent(double t, double dur, int count, double conf, double snr) {
}
