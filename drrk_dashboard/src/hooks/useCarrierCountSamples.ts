import { useEffect, useState } from "react";
import { createCarrierCountStream } from "../api/carrierCountStream";
import {
  carrierSnapshotToSample,
  carrierSnapshotToScoreSample,
  pushCarrierSample,
  type CongestionSample,
  pushScoreSample,
  type ScoreSample,
} from "../carrierSamples";
import type { CarrierCountConnectionStatus } from "../types/inference";

interface UseCarrierCountSamplesOptions {
  baseUrl?: string;
  EventSourceCtor?: typeof EventSource;
  now?: () => Date;
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
}: UseCarrierCountSamplesOptions = {}): UseCarrierCountSamplesResult {
  const [carrierSamples, setCarrierSamples] = useState<CongestionSample[]>([]);
  const [scoreSamples, setScoreSamples] = useState<ScoreSample[]>([]);
  const [connectionStatus, setConnectionStatus] =
    useState<CarrierCountConnectionStatus>("connecting");
  const apiBaseUrlConfigured = baseUrl.trim().length > 0;

  useEffect(() => {
    const stream = createCarrierCountStream({
      baseUrl,
      EventSourceCtor,
      now,
      onOpen: () => setConnectionStatus("open"),
      onError: () => setConnectionStatus("reconnecting"),
      onSnapshot: (snapshot) => {
        setCarrierSamples((current) =>
          pushCarrierSample(current, carrierSnapshotToSample(snapshot)),
        );
        const scoreSample = carrierSnapshotToScoreSample(snapshot);
        if (scoreSample !== null) {
          setScoreSamples((current) => pushScoreSample(current, scoreSample));
        }
      },
    });

    if (stream === null) {
      return undefined;
    }

    return () => stream.close();
  }, [baseUrl, EventSourceCtor, now]);

  return {
    carrierSamples,
    scoreSamples,
    connectionStatus: apiBaseUrlConfigured ? connectionStatus : "unavailable",
  };
}
