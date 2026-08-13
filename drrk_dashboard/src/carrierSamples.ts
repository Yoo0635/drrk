import type { CarrierCountSnapshot } from "./types/inference";

export const CARRIER_SAMPLE_COUNT = 30;

export interface CongestionSample {
  spaceId: string;
  value: 0 | 1;
  timestamp: number;
}

export function carrierSnapshotToSample({
  spaceId,
  carrierCount,
  receivedAt,
}: Pick<CarrierCountSnapshot, "spaceId" | "carrierCount" | "receivedAt">): CongestionSample {
  return {
    spaceId: spaceId.trim(),
    value: carrierCount > 0 ? 1 : 0,
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

export function samplesToSpaceIdRows(samples: CongestionSample[]): string[] {
  return samples.map((sample) => sample.spaceId);
}

function isValidCongestionSample(sample: CongestionSample) {
  return (
    sample.spaceId.trim().length > 0 &&
    (sample.value === 0 || sample.value === 1) &&
    Number.isFinite(sample.timestamp)
  );
}
