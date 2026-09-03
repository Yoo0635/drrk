import { useEffect, useRef, useState } from "react";
import { createCarrierCountStream } from "../api/carrierCountStream";
import {
  carrierSnapshotToSample,
  congestionDeliveryToScoreSample,
  pushCarrierSample,
  type CongestionSample,
  upsertScoreSample,
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
}

export function useCarrierCountSamples({
  baseUrl = import.meta.env.VITE_API_BASE_URL ?? window.location.origin,
  EventSourceCtor,
  now,
  staleAfterMs = 6000,
}: UseCarrierCountSamplesOptions = {}): UseCarrierCountSamplesResult {
  const [carrierSamples, setCarrierSamples] = useState<CongestionSample[]>([]);
  const [scoreSamples, setScoreSamples] = useState<ScoreSample[]>([]);
  const [connectionStatus, setConnectionStatus] =
    useState<CarrierCountConnectionStatus>("connecting");
  const apiBaseUrlConfigured = baseUrl.trim().length > 0;
  const nowRef = useRef(now);

  useEffect(() => {
    nowRef.current = now;
  }, [now]);

  useEffect(() => {
    let staleTimeout: ReturnType<typeof setTimeout> | null = null;
    const clearSamples = () => {
      setCarrierSamples([]);
      setScoreSamples([]);
    };
    const resetStaleTimeout = () => {
      if (staleTimeout !== null) {
        clearTimeout(staleTimeout);
      }
      staleTimeout = setTimeout(() => {
        clearSamples();
      }, staleAfterMs);
    };

    const stream = createCarrierCountStream({
      baseUrl,
      EventSourceCtor,
      now: () => nowRef.current?.() ?? new Date(),
      onOpen: () => setConnectionStatus("open"),
      onError: () => {
        setConnectionStatus("reconnecting");
      },
      onSnapshot: (snapshot) => {
        resetStaleTimeout();
        setCarrierSamples((current) =>
          pushCarrierSample(current, carrierSnapshotToSample(snapshot)),
        );
      },
      onCongestionDelivery: (snapshot) => {
        resetStaleTimeout();
        setScoreSamples((current) =>
          upsertScoreSample(current, congestionDeliveryToScoreSample(snapshot)),
        );
      },
    });

    if (stream === null) {
      if (staleTimeout !== null) {
        clearTimeout(staleTimeout);
      }
      return undefined;
    }

    return () => {
      if (staleTimeout !== null) {
        clearTimeout(staleTimeout);
      }
      stream.close();
    };
  }, [baseUrl, EventSourceCtor, staleAfterMs]);

  return {
    carrierSamples,
    scoreSamples,
    connectionStatus: apiBaseUrlConfigured ? connectionStatus : "unavailable",
  };
}
