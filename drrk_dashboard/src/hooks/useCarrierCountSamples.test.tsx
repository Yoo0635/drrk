/**
 * @vitest-environment jsdom
 */
import { act, renderHook, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it } from "vitest";
import { useCarrierCountSamples } from "./useCarrierCountSamples";

type Listener = (event: MessageEvent) => void;

class FakeEventSource {
  static instances: FakeEventSource[] = [];

  readonly url: string;
  readonly listeners = new Map<string, Listener[]>();
  onopen: (() => void) | null = null;
  onerror: (() => void) | null = null;
  closed = false;

  constructor(url: string) {
    this.url = url;
    FakeEventSource.instances.push(this);
  }

  addEventListener(type: string, listener: Listener) {
    const listeners = this.listeners.get(type) ?? [];
    listeners.push(listener);
    this.listeners.set(type, listeners);
  }

  emitCarrierCount(data: unknown, lastEventId = "message-1") {
    const event = new MessageEvent("carrier-count", {
      data: typeof data === "string" ? data : JSON.stringify(data),
      lastEventId,
    });
    this.listeners.get("carrier-count")?.forEach((listener) => listener(event));
  }

  emitCongestionDelivery(data: unknown, lastEventId = "congestion-1") {
    const event = new MessageEvent("congestion-delivery", {
      data: typeof data === "string" ? data : JSON.stringify(data),
      lastEventId,
    });
    this.listeners.get("congestion-delivery")?.forEach((listener) => listener(event));
  }

  emitCongestionHistory(data: unknown, lastEventId = "congestion-history") {
    const event = new MessageEvent("congestion-history", {
      data: typeof data === "string" ? data : JSON.stringify(data),
      lastEventId,
    });
    this.listeners.get("congestion-history")?.forEach((listener) => listener(event));
  }

  close() {
    this.closed = true;
  }
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

  it("stores carrier and congestion samples separately from one SSE payload", async () => {
    const { result, unmount } = renderHook(() =>
      useCarrierCountSamples({
        baseUrl: "http://localhost:8080",
        EventSourceCtor: FakeEventSource as unknown as typeof EventSource,
        now: () => new Date("2026-08-13T05:30:00.000Z"),
      }),
    );

    act(() => {
      FakeEventSource.instances[0].onopen?.();
      FakeEventSource.instances[0].emitCarrierCount({
        n_carriers: 3,
        score: 0.5,
        level: "MEDIUM",
      });
      FakeEventSource.instances[0].emitCongestionDelivery({
        messageId: "congestion-1",
        calculatedAt: "2026-08-13T05:29:55Z",
        score: 0.5,
        level: "MEDIUM",
        deliveryStatus: "RECOVERED_LATE",
        retryCount: 2,
        serverNow: "2026-08-13T05:30:00Z",
      });
      FakeEventSource.instances[0].emitCarrierCount({
        n_carriers: 0,
        score: null,
        level: null,
      });
    });

    await waitFor(() => {
      expect(result.current.connectionStatus).toBe("open");
      expect(result.current.carrierSamples).toEqual([
        { value: 1, timestamp: Date.parse("2026-08-13T05:30:00.000Z") },
        { value: 0, timestamp: Date.parse("2026-08-13T05:30:00.000Z") },
      ]);
      expect(result.current.scoreSamples).toEqual([
        {
          messageId: "congestion-1",
          score: 0.5,
          level: "MEDIUM",
          timestamp: Date.parse("2026-08-13T05:29:55.000Z"),
          bucketTimestamp: Date.parse("2026-08-13T05:29:55.000Z"),
          deliveryStatus: "RECOVERED_LATE",
          retryCount: 2,
        },
      ]);
    });

    unmount();

    expect(FakeEventSource.instances[0].closed).toBe(true);
  });

  it("uses the latest clock without reconnecting the SSE stream", async () => {
    const firstNow = () => new Date("2026-08-13T05:30:00.000Z");
    const secondNow = () => new Date("2026-08-13T05:31:00.000Z");
    const { result, rerender } = renderHook(
      ({ now }) =>
        useCarrierCountSamples({
          baseUrl: "http://localhost:8080",
          EventSourceCtor: FakeEventSource as unknown as typeof EventSource,
          now,
        }),
      { initialProps: { now: firstNow } },
    );

    rerender({ now: secondNow });

    act(() => {
      FakeEventSource.instances[0].emitCarrierCount({
        n_carriers: 1,
        score: null,
        level: null,
      });
    });

    await waitFor(() => {
      expect(FakeEventSource.instances).toHaveLength(1);
      expect(result.current.carrierSamples).toEqual([
        { value: 1, timestamp: Date.parse("2026-08-13T05:31:00.000Z") },
      ]);
    });
  });

  it("keeps existing samples during a short quiet period", async () => {
    const { result } = renderHook(() =>
      useCarrierCountSamples({
        baseUrl: "http://localhost:8080",
        EventSourceCtor: FakeEventSource as unknown as typeof EventSource,
        now: () => new Date("2026-08-13T05:30:00.000Z"),
        staleAfterMs: 5,
      }),
    );

    act(() => {
      FakeEventSource.instances[0].onopen?.();
      FakeEventSource.instances[0].emitCarrierCount({
        n_carriers: 2,
        score: 0.25,
        level: "LOW",
      });
      FakeEventSource.instances[0].emitCongestionDelivery({
        messageId: "congestion-1",
        calculatedAt: "2026-08-13T05:30:00Z",
        score: 0.25,
        level: "LOW",
        deliveryStatus: "LIVE",
        retryCount: 0,
        serverNow: "2026-08-13T05:30:00Z",
      });
    });

    expect(result.current.carrierSamples).toEqual([
      { value: 1, timestamp: Date.parse("2026-08-13T05:30:00.000Z") },
    ]);
    expect(result.current.scoreSamples).toEqual([
      {
        messageId: "congestion-1",
        score: 0.25,
        level: "LOW",
        timestamp: Date.parse("2026-08-13T05:30:00.000Z"),
        bucketTimestamp: Date.parse("2026-08-13T05:30:00.000Z"),
        deliveryStatus: "LIVE",
        retryCount: 0,
      },
    ]);
  });

  it("merges initial congestion history without clearing existing valid points", async () => {
    const { result } = renderHook(() =>
      useCarrierCountSamples({
        baseUrl: "http://localhost:8080",
        EventSourceCtor: FakeEventSource as unknown as typeof EventSource,
        now: () => new Date("2026-08-13T05:30:00.000Z"),
      }),
    );

    act(() => {
      FakeEventSource.instances[0].emitCongestionDelivery({
        messageId: "live-point",
        calculatedAt: "2026-08-13T05:29:55Z",
        score: 0.5,
        level: "MEDIUM",
        deliveryStatus: "LIVE",
        retryCount: 0,
        serverNow: "2026-08-13T05:30:00Z",
      });
      FakeEventSource.instances[0].emitCongestionHistory({
        serverNow: "2026-08-13T05:30:01Z",
        windowStart: "2026-08-13T05:20:01Z",
        samples: [
          {
            messageId: "history-point",
            calculatedAt: "2026-08-13T05:29:50Z",
            score: 0.25,
            level: "LOW",
            deliveryStatus: "RECOVERED_LATE",
            retryCount: 1,
          },
        ],
      });
    });

    await waitFor(() => {
      expect(result.current.scoreSamples.map((sample) => sample.messageId)).toEqual([
        "history-point",
        "live-point",
      ]);
    });
  });

  it("keeps existing samples during a short SSE reconnect", async () => {
    const { result } = renderHook(() =>
      useCarrierCountSamples({
        baseUrl: "http://localhost:8080",
        EventSourceCtor: FakeEventSource as unknown as typeof EventSource,
        now: () => new Date("2026-08-13T05:30:00.000Z"),
        staleAfterMs: 1000,
      }),
    );

    act(() => {
      FakeEventSource.instances[0].onopen?.();
      FakeEventSource.instances[0].emitCarrierCount({
        n_carriers: 2,
        score: 0.25,
        level: "LOW",
      });
      FakeEventSource.instances[0].emitCongestionDelivery({
        messageId: "congestion-1",
        calculatedAt: "2026-08-13T05:30:00Z",
        score: 0.25,
        level: "LOW",
        deliveryStatus: "LIVE",
        retryCount: 0,
        serverNow: "2026-08-13T05:30:00Z",
      });
      FakeEventSource.instances[0].onerror?.();
    });

    await waitFor(() => {
      expect(result.current.connectionStatus).toBe("reconnecting");
      expect(result.current.carrierSamples).toEqual([
        { value: 1, timestamp: Date.parse("2026-08-13T05:30:00.000Z") },
      ]);
      expect(result.current.scoreSamples).toEqual([
        {
          messageId: "congestion-1",
          score: 0.25,
          level: "LOW",
          timestamp: Date.parse("2026-08-13T05:30:00.000Z"),
          bucketTimestamp: Date.parse("2026-08-13T05:30:00.000Z"),
          deliveryStatus: "LIVE",
          retryCount: 0,
        },
      ]);
    });
  });
});
