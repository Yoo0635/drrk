import type {
  CarrierCountSnapshot,
  CongestionDeliverySnapshot,
  CongestionDeliveryStatus,
} from "./types/inference";

export const CARRIER_SAMPLE_COUNT = 30;
const SCORE_BUCKET_MS = 5_000;

export interface CongestionSample {
  value: 0 | 1;
  timestamp: number;
}

export interface ScoreSample {
  messageId: string;
  score: number;
  level: string;
  timestamp: number;
  deliveryStatus: CongestionDeliveryStatus;
  retryCount: number;
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

export function congestionDeliveryToScoreSample({
  messageId,
  score,
  level,
  calculatedAt,
  deliveryStatus,
  retryCount,
}: CongestionDeliverySnapshot): ScoreSample {
  return {
    messageId,
    score,
    level,
    timestamp: toScoreBucket(calculatedAt.getTime()),
    deliveryStatus,
    retryCount,
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

export function upsertScoreSample(
  samples: ScoreSample[],
  sample: ScoreSample,
  limit = CARRIER_SAMPLE_COUNT,
): ScoreSample[] {
  if (!isValidScoreSample(sample)) {
    return samples;
  }

  const bucketed = { ...sample, timestamp: toScoreBucket(sample.timestamp) };
  const retained = samples.filter(
    (current) =>
      current.messageId !== bucketed.messageId &&
      current.timestamp !== bucketed.timestamp,
  );
  return [...retained, bucketed]
    .sort((left, right) => left.timestamp - right.timestamp)
    .slice(-limit);
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
    Number.isFinite(sample.timestamp) &&
    sample.messageId.trim().length > 0 &&
    (sample.deliveryStatus === "LIVE" || sample.deliveryStatus === "RECOVERED_LATE") &&
    Number.isInteger(sample.retryCount) &&
    sample.retryCount >= 0
  );
}

function toScoreBucket(timestamp: number) {
  return Math.floor(timestamp / SCORE_BUCKET_MS) * SCORE_BUCKET_MS;
}
