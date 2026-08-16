import { describe, expect, it } from "vitest";
import {
  carrierSnapshotToSample,
  carrierSnapshotToScoreSample,
  type CongestionSample,
  type ScoreSample,
  pushCarrierSample,
  pushScoreSample,
} from "./carrierSamples";

describe("carrier congestion samples", () => {
  it("converts SSE carrier counts into binary congestion samples", () => {
    expect(
      carrierSnapshotToSample({
        carrierCount: 0,
        receivedAt: new Date("2026-08-14T00:00:05.000Z"),
      }),
    ).toEqual({
      value: 0,
      timestamp: Date.parse("2026-08-14T00:00:05.000Z"),
    });

    expect(
      carrierSnapshotToSample({
        carrierCount: 2,
        receivedAt: new Date("2026-08-14T00:00:10.000Z"),
      }),
    ).toMatchObject({
      value: 1,
    });
  });

  it("converts SSE score payloads into congestion score samples only when both fields exist", () => {
    expect(
      carrierSnapshotToScoreSample({
        congestionScore: 0.5,
        congestionLevel: "MEDIUM",
        receivedAt: new Date("2026-08-14T00:00:10.000Z"),
      }),
    ).toEqual({
      score: 0.5,
      level: "MEDIUM",
      timestamp: Date.parse("2026-08-14T00:00:10.000Z"),
    });

    expect(
      carrierSnapshotToScoreSample({
        congestionScore: null,
        congestionLevel: null,
        receivedAt: new Date("2026-08-14T00:00:10.000Z"),
      }),
    ).toBeNull();
  });

  it("keeps only the latest 30 samples", () => {
    const samples = Array.from({ length: 31 }, (_, index): CongestionSample => ({
      value: index % 2 === 0 ? 0 : 1,
      timestamp: index,
    })).reduce<CongestionSample[]>(
      (current, sample) => pushCarrierSample(current, sample, 30),
      [],
    );

    expect(samples).toHaveLength(30);
    expect(samples[0].timestamp).toBe(1);
    expect(samples.at(-1)?.timestamp).toBe(30);
  });

  it("keeps only valid score samples", () => {
    const samples = [
      { score: 0.2, level: "LOW", timestamp: 1 },
      { score: Number.NaN, level: "LOW", timestamp: 2 },
      { score: 1.1, level: "HIGH", timestamp: 3 },
      { score: 0.8, level: "HIGH", timestamp: 4 },
    ].reduce<ScoreSample[]>(
      (current, sample) => pushScoreSample(current, sample, 30),
      [],
    );

    expect(samples).toEqual([
      { score: 0.2, level: "LOW", timestamp: 1 },
      { score: 0.8, level: "HIGH", timestamp: 4 },
    ]);
  });
});
