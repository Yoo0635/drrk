/**
 * @vitest-environment jsdom
 */
import { render } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { RealtimeCongestionChart } from "./RealtimeCongestionChart";

describe("RealtimeCongestionChart", () => {
  it("draws only segments touching a recovered sample as dashed", () => {
    const { container } = render(
      <RealtimeCongestionChart
        carrierSamples={[]}
        connectionStatus="open"
        scoreSamples={[
          sample("live-0", 0, "LIVE"),
          sample("live-5", 5_000, "LIVE"),
          sample("recovered-10", 10_000, "RECOVERED_LATE"),
          sample("live-15", 15_000, "LIVE"),
        ]}
      />,
    );

    const segments = [...container.querySelectorAll('[data-series="score-segment"]')];
    expect(segments).toHaveLength(3);
    expect(segments.map((segment) => segment.getAttribute("stroke-dasharray"))).toEqual([
      null,
      "6 5",
      "6 5",
    ]);
  });

  it("positions score points in proportion to their timestamps", () => {
    const { container } = render(
      <RealtimeCongestionChart
        carrierSamples={[]}
        connectionStatus="open"
        scoreSamples={[
          sample("start", 0, "LIVE"),
          sample("middle", 5_000, "LIVE"),
          sample("end", 15_000, "LIVE"),
        ]}
      />,
    );

    const points = [...container.querySelectorAll('[data-series="score-point"]')];
    expect(points.map((point) => Number(point.getAttribute("cx")))).toEqual([393, 496, 702]);
  });
});

function sample(
  messageId: string,
  timestamp: number,
  deliveryStatus: "LIVE" | "RECOVERED_LATE",
) {
  return {
    messageId,
    score: 0.5,
    level: "MEDIUM",
    timestamp,
    deliveryStatus,
    retryCount: deliveryStatus === "LIVE" ? 0 : 1,
  };
}
