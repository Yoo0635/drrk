import { useEffect, useRef, useState } from "react";
import { createCarrierCountStream } from "../api/carrierCountStream";
import {
  carrierSnapshotToSample,
  congestionDeliveryToScoreSample,
  mergeScoreSamples,
  pruneCarrierSamples,
  pruneScoreSamples,
  pushCarrierSample,
  type CongestionSample,
  type ScoreSample,
} from "../carrierSamples";
import type { CarrierCountConnectionStatus } from "../types/inference";

interface UseCarrierCountSamplesOptions {
  baseUrl?: string;
  EventSourceCtor?: typeof EventSource;
  monotonicNow?: () => number;
  staleAfterMs?: number;
}

interface UseCarrierCountSamplesResult {
  carrierSamples: CongestionSample[];
  scoreSamples: ScoreSample[];
  connectionStatus: CarrierCountConnectionStatus;
  windowNow: number;
}

interface ServerClockAnchor {
  serverNow: number;
  monotonicNow: number;
}

export function useCarrierCountSamples({
  baseUrl = import.meta.env.VITE_API_BASE_URL ?? window.location.origin,
  EventSourceCtor,
  monotonicNow,
}: UseCarrierCountSamplesOptions = {}): UseCarrierCountSamplesResult {
  const [carrierSamples, setCarrierSamples] = useState<CongestionSample[]>([]);
  const [scoreSamples, setScoreSamples] = useState<ScoreSample[]>([]);
  const [connectionStatus, setConnectionStatus] =
    useState<CarrierCountConnectionStatus>("connecting");
  const [windowNow, setWindowNow] = useState(0);
  const apiBaseUrlConfigured = baseUrl.trim().length > 0;
  const monotonicNowRef = useRef(monotonicNow);
  const serverClockRef = useRef<ServerClockAnchor | null>(null);

  useEffect(() => {
    monotonicNowRef.current = monotonicNow;
  }, [monotonicNow]);

  useEffect(() => {
    const currentMonotonicNow = () => monotonicNowRef.current?.() ?? currentPerformanceNow();
    const effectiveNow = () => {
      const anchor = serverClockRef.current;
      if (anchor === null) {
        return 0;
      }
      return anchor.serverNow + (currentMonotonicNow() - anchor.monotonicNow);
    };
    const syncServerClock = (serverNow: Date) => {
      const serverNowMs = serverNow.getTime();
      serverClockRef.current = {
        serverNow: serverNowMs,
        monotonicNow: currentMonotonicNow(),
      };
      setWindowNow(serverNowMs);
      return serverNowMs;
    };
    const pruneToWindow = () => {
      if (serverClockRef.current === null) {
        return;
      }
      const nowMs = effectiveNow();
      setWindowNow(nowMs);
      setCarrierSamples((current) => pruneCarrierSamples(current, nowMs));
      setScoreSamples((current) => pruneScoreSamples(current, nowMs));
    };

    const pruneInterval = setInterval(pruneToWindow, 1000);
    const stream = createCarrierCountStream({
      baseUrl,
      EventSourceCtor,
      onOpen: () => setConnectionStatus("open"),
      onError: () => {
        setConnectionStatus("reconnecting");
      },
      onSnapshot: (snapshot) => {
        const nowMs = syncServerClock(snapshot.receivedAt);
        setCarrierSamples((current) =>
          pushCarrierSample(current, carrierSnapshotToSample(snapshot), nowMs),
        );
      },
      onCongestionDelivery: (snapshot) => {
        const nowMs = syncServerClock(snapshot.serverNow);
        setScoreSamples((current) =>
          upsertWithCurrentWindow(current, congestionDeliveryToScoreSample(snapshot), nowMs),
        );
      },
      onCongestionHistory: (snapshot) => {
        const nowMs = syncServerClock(snapshot.serverNow);
        setScoreSamples((current) =>
          mergeScoreSamples(
            current,
            snapshot.samples.map(congestionDeliveryToScoreSample),
            nowMs,
          ),
        );
      },
    });

    if (stream === null) {
      clearInterval(pruneInterval);
      return undefined;
    }

    return () => {
      clearInterval(pruneInterval);
      stream.close();
    };
  }, [baseUrl, EventSourceCtor]);

  return {
    carrierSamples,
    scoreSamples,
    connectionStatus: apiBaseUrlConfigured ? connectionStatus : "unavailable",
    windowNow,
  };
}

function upsertWithCurrentWindow(samples: ScoreSample[], sample: ScoreSample, now: number) {
  return mergeScoreSamples(samples, [sample], now);
}

function currentPerformanceNow() {
  if (typeof performance !== "undefined") {
    return performance.now();
  }
  return Date.now();
}
