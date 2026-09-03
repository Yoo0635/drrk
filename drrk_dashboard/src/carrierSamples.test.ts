import { describe, expect, it } from "vitest";
import {
  carrierSnapshotToSample,
  congestionDeliveryToScoreSample,
  type CongestionSample,
  type ScoreSample,
  pushCarrierSample,
  upsertScoreSample,
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

  it("uses the original calculated timestamp for congestion score samples", () => {
    expect(
      congestionDeliveryToScoreSample({
        messageId: "message-1",
        score: 0.5,
        level: "MEDIUM",
        calculatedAt: new Date("2026-08-14T00:00:12.345Z"),
        deliveryStatus: "RECOVERED_LATE",
        retryCount: 2,
      }),
    ).toEqual({
      messageId: "message-1",
      score: 0.5,
      level: "MEDIUM",
      timestamp: Date.parse("2026-08-14T00:00:10.000Z"),
      deliveryStatus: "RECOVERED_LATE",
      retryCount: 2,
    });
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
      { messageId: "one", score: 0.2, level: "LOW", timestamp: 5_000, deliveryStatus: "LIVE" as const, retryCount: 0 },
      { messageId: "bad-nan", score: Number.NaN, level: "LOW", timestamp: 10_000, deliveryStatus: "LIVE" as const, retryCount: 0 },
      { messageId: "bad-score", score: 1.1, level: "HIGH", timestamp: 15_000, deliveryStatus: "LIVE" as const, retryCount: 0 },
      { messageId: "four", score: 0.8, level: "HIGH", timestamp: 20_000, deliveryStatus: "LIVE" as const, retryCount: 0 },
    ].reduce<ScoreSample[]>(
      (current, sample) => upsertScoreSample(current, sample, 30),
      [],
    );

    expect(samples).toEqual([
      { messageId: "one", score: 0.2, level: "LOW", timestamp: 5_000, deliveryStatus: "LIVE", retryCount: 0 },
      { messageId: "four", score: 0.8, level: "HIGH", timestamp: 20_000, deliveryStatus: "LIVE", retryCount: 0 },
    ]);
  });

  it("upserts late recovery into its timestamp bucket and keeps chronological order", () => {
    const liveAtTen: ScoreSample = {
      messageId: "live-10",
      score: 0.3,
      level: "LOW",
      timestamp: 10_000,
      deliveryStatus: "LIVE",
      retryCount: 0,
    };
    const liveAtTwenty: ScoreSample = {
      messageId: "live-20",
      score: 0.8,
      level: "HIGH",
      timestamp: 20_000,
      deliveryStatus: "LIVE",
      retryCount: 0,
    };
    const recoveredAtFifteen: ScoreSample = {
      messageId: "recovered-15",
      score: 0.5,
      level: "MEDIUM",
      timestamp: 17_999,
      deliveryStatus: "RECOVERED_LATE",
      retryCount: 2,
    };

    const result = upsertScoreSample(
      [liveAtTen, liveAtTwenty],
      recoveredAtFifteen,
    );

    expect(result.map(({ messageId, timestamp }) => ({ messageId, timestamp }))).toEqual([
      { messageId: "live-10", timestamp: 10_000 },
      { messageId: "recovered-15", timestamp: 15_000 },
      { messageId: "live-20", timestamp: 20_000 },
    ]);
  });

  it("replaces an existing message instead of adding a duplicate point", () => {
    const original: ScoreSample = {
      messageId: "same-message",
      score: 0.3,
      level: "LOW",
      timestamp: 10_000,
      deliveryStatus: "LIVE",
      retryCount: 0,
    };

    const result = upsertScoreSample([original], {
      ...original,
      score: 0.5,
      level: "MEDIUM",
      deliveryStatus: "RECOVERED_LATE",
      retryCount: 1,
    });

    expect(result).toEqual([
      {
        ...original,
        score: 0.5,
        level: "MEDIUM",
        deliveryStatus: "RECOVERED_LATE",
        retryCount: 1,
      },
    ]);
  });
});
