import type { CarrierCountSnapshot } from "./types/inference";

export const CARRIER_SAMPLE_COUNT = 30;

export interface CongestionSample {
  value: 0 | 1;
  timestamp: number;
}

export interface ScoreSample {
  score: number;
  level: string;
  timestamp: number;
}

export function carrierSnapshotToSample({
  carrierCount,
  receivedAt,
}: Pick<CarrierCountSnapshot, "carrierCount" | "receivedAt">): CongestionSample {
  return {
    value: carrierCount > 0 ? 1 : 0,
    timestamp: receivedAt.getTime(),
  };
}

export function carrierSnapshotToScoreSample({
  congestionScore,
  congestionLevel,
  receivedAt,
}: Pick<
  CarrierCountSnapshot,
  "congestionScore" | "congestionLevel" | "receivedAt"
>): ScoreSample | null {
  if (congestionScore === null || congestionLevel === null) {
    return null;
  }

  return {
    score: congestionScore,
    level: congestionLevel,
    timestamp: receivedAt.getTime(),
  };
}

export function pushCarrierSample(
  samples: CongestionSample[],
  sample: CongestionSample,
  limit = CARRIER_SAMPLE_COUNT,
): CongestionSample[] {
  if (!isValidCongestionSample(sample)) {
    return samples;
  }

  return [...samples, sample].slice(-limit);
}

export function pushScoreSample(
  samples: ScoreSample[],
  sample: ScoreSample,
  limit = CARRIER_SAMPLE_COUNT,
): ScoreSample[] {
  if (!isValidScoreSample(sample)) {
    return samples;
  }

  return [...samples, sample].slice(-limit);
}

function isValidCongestionSample(sample: CongestionSample) {
  return (
    (sample.value === 0 || sample.value === 1) &&
    Number.isFinite(sample.timestamp)
  );
}

function isValidScoreSample(sample: ScoreSample) {
  return (
    Number.isFinite(sample.score) &&
    sample.score >= 0 &&
    sample.score <= 1 &&
    sample.level.trim().length > 0 &&
    Number.isFinite(sample.timestamp)
  );
}
