import type {
  CarrierCountSnapshot,
  CongestionDeliverySnapshot,
  CongestionDeliveryStatus,
} from "./types/inference";

export const CARRIER_SAMPLE_COUNT = 120;
export const SAMPLE_WINDOW_MS = 10 * 60 * 1000;
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
  bucketTimestamp: number;
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
  const timestamp = calculatedAt.getTime();
  return {
    messageId,
    score,
    level,
    timestamp,
    bucketTimestamp: toScoreBucket(timestamp),
    deliveryStatus,
    retryCount,
  };
}

export function pushCarrierSample(
  samples: CongestionSample[],
  sample: CongestionSample,
  now = sample.timestamp,
  limit = CARRIER_SAMPLE_COUNT,
): CongestionSample[] {
  if (!isValidCongestionSample(sample)) {
    return pruneCarrierSamples(samples, now, limit);
  }

  return pruneCarrierSamples([...samples, sample], now, limit);
}

export function mergeScoreSamples(
  samples: ScoreSample[],
  incoming: ScoreSample[],
  now: number,
  limit = CARRIER_SAMPLE_COUNT,
): ScoreSample[] {
  return incoming.reduce(
    (current, sample) => upsertScoreSample(current, sample, now, limit),
    pruneScoreSamples(samples, now, limit),
  );
}

export function upsertScoreSample(
  samples: ScoreSample[],
  sample: ScoreSample,
  now = sample.timestamp,
  limit = CARRIER_SAMPLE_COUNT,
): ScoreSample[] {
  if (!isValidScoreSample(sample)) {
    return pruneScoreSamples(samples, now, limit);
  }

  const bucketed = { ...sample, bucketTimestamp: toScoreBucket(sample.timestamp) };
  const byBucket = new Map<number, ScoreSample>();
  for (const current of samples) {
    if (current.messageId === bucketed.messageId) {
      continue;
    }
    const normalized = {
      ...current,
      bucketTimestamp: toScoreBucket(current.timestamp),
    };
    const winner = betterBucketSample(byBucket.get(normalized.bucketTimestamp), normalized);
    byBucket.set(normalized.bucketTimestamp, winner);
  }
  const winner = betterBucketSample(byBucket.get(bucketed.bucketTimestamp), bucketed);
  byBucket.set(bucketed.bucketTimestamp, winner);

  return pruneScoreSamples([...byBucket.values()], now, limit);
}

export function pruneCarrierSamples(
  samples: CongestionSample[],
  now: number,
  limit = CARRIER_SAMPLE_COUNT,
): CongestionSample[] {
  const windowStart = now - SAMPLE_WINDOW_MS;
  return samples
    .filter((sample) => isValidCongestionSample(sample))
    .filter((sample) => sample.timestamp > windowStart && sample.timestamp <= now)
    .sort((left, right) => left.timestamp - right.timestamp)
    .slice(-limit);
}

export function pruneScoreSamples(
  samples: ScoreSample[],
  now: number,
  limit = CARRIER_SAMPLE_COUNT,
): ScoreSample[] {
  const windowStart = now - SAMPLE_WINDOW_MS;
  return samples
    .filter((sample) => isValidScoreSample(sample))
    .filter((sample) => sample.timestamp > windowStart && sample.timestamp <= now)
    .sort(compareScoreSamples)
    .slice(-limit);
}

function betterBucketSample(current: ScoreSample | undefined, incoming: ScoreSample) {
  if (current === undefined) {
    return incoming;
  }
  if (incoming.timestamp !== current.timestamp) {
    return incoming.timestamp > current.timestamp ? incoming : current;
  }
  return incoming.messageId > current.messageId ? incoming : current;
}

function compareScoreSamples(left: ScoreSample, right: ScoreSample) {
  const byTimestamp = left.timestamp - right.timestamp;
  if (byTimestamp !== 0) {
    return byTimestamp;
  }
  return left.messageId.localeCompare(right.messageId);
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
