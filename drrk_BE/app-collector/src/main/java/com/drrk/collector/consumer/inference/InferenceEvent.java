package com.drrk.collector.consumer.inference;

public record InferenceEvent(Double t, Double dur, Integer count, Double conf, Double snr) {
}
