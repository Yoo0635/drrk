/**
 * @vitest-environment jsdom
 */
import { act, renderHook } from "@testing-library/react";
import { beforeEach, describe, expect, it } from "vitest";
import { useCarrierCountStream } from "./useCarrierCountStream";

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

  emitCarrierCount(spaceId: string, carrierCount: number, messageId: string) {
    const event = new MessageEvent("carrier-count", {
      data: JSON.stringify({ space_id: spaceId, n_carriers: carrierCount }),
      lastEventId: messageId,
    });
    this.listeners.get("carrier-count")?.forEach((listener) => listener(event));
  }

  close() {
    this.closed = true;
  }
}

describe("useCarrierCountStream", () => {
  beforeEach(() => {
    FakeEventSource.instances = [];
  });

  it("keeps one EventSource and upserts snapshots by space", () => {
    const receivedTimes = [
      new Date("2026-08-13T05:30:00.000Z"),
      new Date("2026-08-13T05:30:10.000Z"),
      new Date("2026-08-13T05:30:20.000Z"),
    ];
    const now = () => receivedTimes.shift() ?? new Date("2026-08-13T05:30:30.000Z");
    const { result, unmount } = renderHook(() =>
      useCarrierCountStream({
        baseUrl: "http://localhost:8080",
        EventSourceCtor: FakeEventSource as unknown as typeof EventSource,
        now,
      }),
    );

    expect(FakeEventSource.instances).toHaveLength(1);
    expect(result.current.connectionStatus).toBe("connecting");

    act(() => {
      FakeEventSource.instances[0].onopen?.();
    });

    expect(result.current.connectionStatus).toBe("open");

    act(() => {
      FakeEventSource.instances[0].emitCarrierCount("desk01", 3, "message-1");
      FakeEventSource.instances[0].emitCarrierCount("desk02", 1, "message-2");
      FakeEventSource.instances[0].emitCarrierCount("desk01", 5, "message-3");
    });

    expect(result.current.snapshotsBySpace).toEqual({
      desk01: {
        spaceId: "desk01",
        carrierCount: 5,
        messageId: "message-3",
        receivedAt: new Date("2026-08-13T05:30:20.000Z"),
      },
      desk02: {
        spaceId: "desk02",
        carrierCount: 1,
        messageId: "message-2",
        receivedAt: new Date("2026-08-13T05:30:10.000Z"),
      },
    });
    expect(result.current.lastReceivedAt).toEqual(new Date("2026-08-13T05:30:20.000Z"));

    act(() => {
      FakeEventSource.instances[0].onerror?.();
    });

    expect(result.current.connectionStatus).toBe("reconnecting");

    unmount();
    expect(FakeEventSource.instances[0].closed).toBe(true);
  });

  it("marks the stream unavailable when no API base URL is configured", () => {
    const { result } = renderHook(() =>
      useCarrierCountStream({
        baseUrl: "",
        EventSourceCtor: FakeEventSource as unknown as typeof EventSource,
      }),
    );

    expect(result.current.connectionStatus).toBe("unavailable");
    expect(FakeEventSource.instances).toHaveLength(0);
  });
});
