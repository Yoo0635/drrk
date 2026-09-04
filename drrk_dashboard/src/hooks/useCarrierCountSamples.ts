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
  now?: () => Date;
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
  clientNow: number;
}

export function useCarrierCountSamples({
  baseUrl = import.meta.env.VITE_API_BASE_URL ?? window.location.origin,
  EventSourceCtor,
  now,
}: UseCarrierCountSamplesOptions = {}): UseCarrierCountSamplesResult {
  const [carrierSamples, setCarrierSamples] = useState<CongestionSample[]>([]);
  const [scoreSamples, setScoreSamples] = useState<ScoreSample[]>([]);
  const [connectionStatus, setConnectionStatus] =
    useState<CarrierCountConnectionStatus>("connecting");
  const [windowNow, setWindowNow] = useState(() => (now?.() ?? new Date()).getTime());
  const apiBaseUrlConfigured = baseUrl.trim().length > 0;
  const nowRef = useRef(now);
  const serverClockRef = useRef<ServerClockAnchor | null>(null);

  useEffect(() => {
    nowRef.current = now;
  }, [now]);

  useEffect(() => {
    const currentClientNow = () => (nowRef.current?.() ?? new Date()).getTime();
    const effectiveNow = () => {
      const clientNow = currentClientNow();
      const anchor = serverClockRef.current;
      return anchor === null ? clientNow : anchor.serverNow + (clientNow - anchor.clientNow);
    };
    const syncServerClock = (serverNow: Date) => {
      serverClockRef.current = {
        serverNow: serverNow.getTime(),
        clientNow: currentClientNow(),
      };
      setWindowNow(effectiveNow());
    };
    const pruneToWindow = () => {
      const nowMs = effectiveNow();
      setWindowNow(nowMs);
      setCarrierSamples((current) => pruneCarrierSamples(current, nowMs));
      setScoreSamples((current) => pruneScoreSamples(current, nowMs));
    };

    const pruneInterval = setInterval(pruneToWindow, 1000);
    const stream = createCarrierCountStream({
      baseUrl,
      EventSourceCtor,
      now: () => nowRef.current?.() ?? new Date(),
      onOpen: () => setConnectionStatus("open"),
      onError: () => {
        setConnectionStatus("reconnecting");
      },
      onSnapshot: (snapshot) => {
        const nowMs = effectiveNow();
        setCarrierSamples((current) =>
          pushCarrierSample(current, carrierSnapshotToSample(snapshot), nowMs),
        );
      },
      onCongestionDelivery: (snapshot) => {
        syncServerClock(snapshot.serverNow);
        setScoreSamples((current) =>
          upsertWithCurrentWindow(current, congestionDeliveryToScoreSample(snapshot), effectiveNow()),
        );
      },
      onCongestionHistory: (snapshot) => {
        syncServerClock(snapshot.serverNow);
        setScoreSamples((current) =>
          mergeScoreSamples(
            current,
            snapshot.samples.map(congestionDeliveryToScoreSample),
            effectiveNow(),
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
