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
        serverNow: new Date("2026-08-14T00:00:15.000Z"),
      }),
    ).toEqual({
      messageId: "message-1",
      score: 0.5,
      level: "MEDIUM",
      timestamp: Date.parse("2026-08-14T00:00:12.345Z"),
      bucketTimestamp: Date.parse("2026-08-14T00:00:10.000Z"),
      deliveryStatus: "RECOVERED_LATE",
      retryCount: 2,
    });
  });

  it("keeps only the latest 120 samples", () => {
    const samples = Array.from({ length: 121 }, (_, index): CongestionSample => ({
      value: index % 2 === 0 ? 0 : 1,
      timestamp: index,
    })).reduce<CongestionSample[]>(
      (current, sample) => pushCarrierSample(current, sample, sample.timestamp, 120),
      [],
    );

    expect(samples).toHaveLength(120);
    expect(samples[0].timestamp).toBe(1);
    expect(samples.at(-1)?.timestamp).toBe(120);
  });

  it("keeps only valid score samples", () => {
    const samples = [
      score("one", 0.2, "LOW", 5_000),
      score("bad-nan", Number.NaN, "LOW", 10_000),
      score("bad-score", 1.1, "HIGH", 15_000),
      score("four", 0.8, "HIGH", 20_000),
    ].reduce<ScoreSample[]>(
      (current, sample) => upsertScoreSample(current, sample, sample.timestamp, 30),
      [],
    );

    expect(samples).toEqual([
      score("one", 0.2, "LOW", 5_000),
      score("four", 0.8, "HIGH", 20_000),
    ]);
  });

  it("upserts late recovery into its timestamp bucket and keeps chronological order", () => {
    const liveAtTen = score("live-10", 0.3, "LOW", 10_000);
    const liveAtTwenty = score("live-20", 0.8, "HIGH", 20_000);
    const recoveredAtFifteen = {
      ...score("recovered-15", 0.5, "MEDIUM", 17_999),
      deliveryStatus: "RECOVERED_LATE" as const,
      retryCount: 2,
    };

    const result = upsertScoreSample(
      [liveAtTen, liveAtTwenty],
      recoveredAtFifteen,
      20_000,
    );

    expect(result.map(({ messageId, timestamp, bucketTimestamp }) => ({ messageId, timestamp, bucketTimestamp }))).toEqual([
      { messageId: "live-10", timestamp: 10_000, bucketTimestamp: 10_000 },
      { messageId: "recovered-15", timestamp: 17_999, bucketTimestamp: 15_000 },
      { messageId: "live-20", timestamp: 20_000, bucketTimestamp: 20_000 },
    ]);
  });

  it("replaces an existing message instead of adding a duplicate point", () => {
    const original = score("same-message", 0.3, "LOW", 10_000);

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
        bucketTimestamp: 10_000,
        deliveryStatus: "RECOVERED_LATE",
        retryCount: 1,
      },
    ]);
  });

  it("drops samples outside the ten minute window", () => {
    const now = Date.parse("2026-08-14T00:10:00.000Z");
    const result = upsertScoreSample(
      [score("expired", 0.2, "LOW", now - 600_000)],
      score("fresh", 0.8, "HIGH", now - 599_000),
      now,
    );

    expect(result.map((sample) => sample.messageId)).toEqual(["fresh"]);
  });
});

function score(
  messageId: string,
  scoreValue: number,
  level: string,
  timestamp: number,
): ScoreSample {
  return {
    messageId,
    score: scoreValue,
    level,
    timestamp,
    bucketTimestamp: Math.floor(timestamp / 5000) * 5000,
    deliveryStatus: "LIVE",
    retryCount: 0,
  };
}
