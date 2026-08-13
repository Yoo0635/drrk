/**
 * @vitest-environment jsdom
 */
import { renderHook } from "@testing-library/react";
import { beforeEach, describe, expect, it } from "vitest";
import { useCarrierCountSamples } from "./useCarrierCountSamples";

class FakeEventSource {
  static instances: FakeEventSource[] = [];

  readonly url: string;
  onopen: (() => void) | null = null;
  onerror: (() => void) | null = null;

  constructor(url: string) {
    this.url = url;
    FakeEventSource.instances.push(this);
  }

  addEventListener() {}

  close() {}
}

describe("useCarrierCountSamples", () => {
  beforeEach(() => {
    FakeEventSource.instances = [];
  });

  it("opens the SSE stream on the current origin by default", () => {
    renderHook(() =>
      useCarrierCountSamples({
        EventSourceCtor: FakeEventSource as unknown as typeof EventSource,
      }),
    );

    expect(FakeEventSource.instances[0]?.url).toBe(
      "http://localhost:3000/api/v1/inference/carriers/stream",
    );
  });
});
