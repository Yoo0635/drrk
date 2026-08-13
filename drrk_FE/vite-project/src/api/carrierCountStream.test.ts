import { beforeEach, describe, expect, it, vi } from "vitest";
import { createCarrierCountStream } from "./carrierCountStream";
import type { CarrierCountSnapshot } from "../types/inference";

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
  });

  it("stores only valid carrier-count events as snapshots", () => {
    const onSnapshot = vi.fn<(snapshot: CarrierCountSnapshot) => void>();
    createCarrierCountStream({
      baseUrl: "http://localhost:8080",
      EventSourceCtor: FakeEventSource as unknown as typeof EventSource,
      now: () => new Date("2026-08-13T05:30:00.000Z"),
      onSnapshot,
    });
    const eventSource = FakeEventSource.instances[0];

    eventSource.emitCarrierCount({ space_id: "desk01", n_carriers: 3 }, "message-1");
    eventSource.emitCarrierCount({ space_id: " ", n_carriers: 2 }, "bad-space");
    eventSource.emitCarrierCount({ space_id: "desk02", n_carriers: -1 }, "bad-count");
    eventSource.emitCarrierCount("not-json", "bad-json");

    expect(onSnapshot).toHaveBeenCalledTimes(1);
    expect(onSnapshot).toHaveBeenCalledWith({
      spaceId: "desk01",
      carrierCount: 3,
      messageId: "message-1",
      receivedAt: new Date("2026-08-13T05:30:00.000Z"),
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
