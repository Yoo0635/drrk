import { beforeEach, describe, expect, it, vi } from "vitest";
import { createCarrierCountStream } from "./carrierCountStream";
import type { CarrierCountSnapshot, CongestionDeliverySnapshot } from "../types/inference";

type Listener = (event: MessageEvent) => void;

class FakeEventSource {
  static instances: FakeEventSource[] = [];

  readonly url: string;
  readonly listeners = new Map<string, Listener[]>();
  closed = false;
  onopen: (() => void) | null = null;
  onerror: (() => void) | null = null;

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

describe("createCarrierCountStream", () => {
  beforeEach(() => {
    FakeEventSource.instances = [];
  });

  it("does not open EventSource when the API base URL is blank", () => {
    const onSnapshot = vi.fn();

    const stream = createCarrierCountStream({
      baseUrl: "",
      EventSourceCtor: FakeEventSource as unknown as typeof EventSource,
      onSnapshot,
    });

    expect(stream).toBeNull();
    expect(FakeEventSource.instances).toHaveLength(0);
  });

  it("opens the carrier-count SSE endpoint with a normalized URL", () => {
    createCarrierCountStream({
      baseUrl: "http://localhost:8080/",
      EventSourceCtor: FakeEventSource as unknown as typeof EventSource,
      onSnapshot: vi.fn(),
    });

    expect(FakeEventSource.instances[0]?.url).toBe(
      "http://localhost:8080/api/v1/inference/carriers/stream",
    );
    expect(FakeEventSource.instances[0]?.listeners.has("carrier-count")).toBe(true);
    expect(FakeEventSource.instances[0]?.listeners.has("congestion-delivery")).toBe(true);
  });

  it("forwards valid congestion deliveries with the server calculated timestamp", () => {
    const onCongestionDelivery = vi.fn<(snapshot: CongestionDeliverySnapshot) => void>();
    createCarrierCountStream({
      baseUrl: "http://localhost:8080",
      EventSourceCtor: FakeEventSource as unknown as typeof EventSource,
      onSnapshot: vi.fn(),
      onCongestionDelivery,
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

    expect(onCongestionDelivery).toHaveBeenCalledExactlyOnceWith({
      messageId: "congestion-1",
      calculatedAt: new Date("2026-08-13T05:29:55Z"),
      score: 0.5,
      level: "MEDIUM",
      deliveryStatus: "RECOVERED_LATE",
      retryCount: 2,
      serverNow: new Date("2026-08-13T05:30:00Z"),
    });
  });

  it("forwards congestion history snapshots from the initial SSE event", () => {
    const onCongestionHistory = vi.fn();
    createCarrierCountStream({
      baseUrl: "http://localhost:8080",
      EventSourceCtor: FakeEventSource as unknown as typeof EventSource,
      onSnapshot: vi.fn(),
      onCongestionHistory,
    });

    FakeEventSource.instances[0].emitCongestionHistory({
      serverNow: "2026-08-13T05:30:00Z",
      windowStart: "2026-08-13T05:20:00Z",
      samples: [
        {
          messageId: "congestion-1",
          calculatedAt: "2026-08-13T05:29:55Z",
          score: 0.5,
          level: "MEDIUM",
          deliveryStatus: "LIVE",
          retryCount: 0,
        },
      ],
    });

    expect(onCongestionHistory).toHaveBeenCalledExactlyOnceWith({
      serverNow: new Date("2026-08-13T05:30:00Z"),
      windowStart: new Date("2026-08-13T05:20:00Z"),
      samples: [
        {
          messageId: "congestion-1",
          calculatedAt: new Date("2026-08-13T05:29:55Z"),
          score: 0.5,
          level: "MEDIUM",
          deliveryStatus: "LIVE",
          retryCount: 0,
          serverNow: new Date("2026-08-13T05:30:00Z"),
        },
      ],
    });
  });

  it("stores only valid carrier-count events as snapshots", () => {
    const onSnapshot = vi.fn<(snapshot: CarrierCountSnapshot) => void>();
    createCarrierCountStream({
      baseUrl: "http://localhost:8080",
      EventSourceCtor: FakeEventSource as unknown as typeof EventSource,
      onSnapshot,
    });
    const eventSource = FakeEventSource.instances[0];

    eventSource.emitCarrierCount(
      { n_carriers: 3, score: 0.5, level: "MEDIUM", serverNow: "2026-08-13T05:30:00Z" },
      "message-1",
    );
    eventSource.emitCarrierCount(
      { n_carriers: 1, score: null, level: null, serverNow: "2026-08-13T05:30:01Z" },
      "message-2",
    );
    eventSource.emitCarrierCount(
      { n_carriers: -1, score: 0.2, level: "LOW", serverNow: "2026-08-13T05:30:02Z" },
      "bad-count",
    );
    eventSource.emitCarrierCount(
      { n_carriers: 2, score: 1.2, level: "HIGH", serverNow: "2026-08-13T05:30:02Z" },
      "bad-score",
    );
    eventSource.emitCarrierCount(
      { n_carriers: 2, score: 0.2, level: null, serverNow: "2026-08-13T05:30:02Z" },
      "bad-level",
    );
    eventSource.emitCarrierCount(
      { n_carriers: 2, score: 0.2, level: "LOW" },
      "bad-server-now",
    );
    eventSource.emitCarrierCount("not-json", "bad-json");

    expect(onSnapshot).toHaveBeenCalledTimes(2);
    expect(onSnapshot).toHaveBeenNthCalledWith(1, {
      carrierCount: 3,
      congestionScore: 0.5,
      congestionLevel: "MEDIUM",
      messageId: "message-1",
      receivedAt: new Date("2026-08-13T05:30:00.000Z"),
    });
    expect(onSnapshot).toHaveBeenNthCalledWith(2, {
      carrierCount: 1,
      congestionScore: null,
      congestionLevel: null,
      messageId: "message-2",
      receivedAt: new Date("2026-08-13T05:30:01.000Z"),
    });
  });

  it("forwards connection events and closes the active EventSource", () => {
    const onOpen = vi.fn();
    const onError = vi.fn();
    const stream = createCarrierCountStream({
      baseUrl: "http://localhost:8080",
      EventSourceCtor: FakeEventSource as unknown as typeof EventSource,
      onSnapshot: vi.fn(),
      onOpen,
      onError,
    });
    const eventSource = FakeEventSource.instances[0];

    eventSource.onopen?.();
    eventSource.onerror?.();
    stream?.close();

    expect(onOpen).toHaveBeenCalledOnce();
    expect(onError).toHaveBeenCalledOnce();
    expect(eventSource.closed).toBe(true);
  });
});
