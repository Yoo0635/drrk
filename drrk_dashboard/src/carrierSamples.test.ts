import { describe, expect, it } from "vitest";
import {
  carrierSnapshotToSample,
  type CongestionSample,
  pushCarrierSample,
  samplesToSpaceIdRows,
} from "./carrierSamples";

describe("carrier congestion samples", () => {
  it("converts SSE carrier counts into binary congestion samples with the space id", () => {
    expect(
      carrierSnapshotToSample({
        spaceId: " gate-a ",
        carrierCount: 0,
        receivedAt: new Date("2026-08-14T00:00:05.000Z"),
      }),
    ).toEqual({
      spaceId: "gate-a",
      value: 0,
      timestamp: Date.parse("2026-08-14T00:00:05.000Z"),
    });

    expect(
      carrierSnapshotToSample({
        spaceId: "gate-b",
        carrierCount: 2,
        receivedAt: new Date("2026-08-14T00:00:10.000Z"),
      }),
    ).toMatchObject({
      spaceId: "gate-b",
      value: 1,
    });
  });

  it("keeps only the latest 30 samples", () => {
    const samples = Array.from({ length: 31 }, (_, index): CongestionSample => ({
      spaceId: `space-${index}`,
      value: index % 2 === 0 ? 0 : 1,
      timestamp: index,
    })).reduce<CongestionSample[]>(
      (current, sample) => pushCarrierSample(current, sample, 30),
      [],
    );

    expect(samples).toHaveLength(30);
    expect(samples[0].spaceId).toBe("space-1");
    expect(samples.at(-1)?.spaceId).toBe("space-30");
  });

  it("formats the latest received space ids under the chart subtitle", () => {
    const rows = samplesToSpaceIdRows([
      { spaceId: "space-a", value: 0, timestamp: 1 },
      { spaceId: "space-b", value: 1, timestamp: 2 },
    ]);

    expect(rows).toEqual(["space-a", "space-b"]);
  });
});
