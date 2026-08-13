import type { MouseEvent as ReactMouseEvent } from "react";
import type { MapMarkerData } from "../types/marker";
import type { RouteData, RouteId } from "../types/route";
import MapMarker from "./MapMarker";
import RouteOverlay from "./RouteOverlay";
import "./AirportMap.css";

interface Props {
  markers: MapMarkerData[];
  routes: RouteData[];
  selectedRouteId: RouteId;
  selectedId: string | null;
  onSelect: (id: string) => void;
  coordPicker?: boolean;
}

export default function AirportMap({
  markers,
  routes,
  selectedRouteId,
  selectedId,
  onSelect,
  coordPicker = false,
}: Props) {
  const selectedRoute = routes.find((route) => route.id === selectedRouteId) ?? null;

  function pickCoord(e: ReactMouseEvent<HTMLDivElement>) {
    const rect = e.currentTarget.getBoundingClientRect();
    const x = ((e.clientX - rect.left) / rect.width) * 100;
    const y = ((e.clientY - rect.top) / rect.height) * 100;
    console.log(`{ x: ${x.toFixed(1)}, y: ${y.toFixed(1)} },`);
  }

  return (
    <div className="map-frame" onClick={coordPicker ? pickCoord : undefined}>
      <img
        src="/images/airport-map.png"
        alt="인천공항 지도"
        className="map-frame__image"
        onError={() => console.error("/public/images/airport-map.png 파일을 확인하세요.")}
      />

      <RouteOverlay route={selectedRoute} />

      {markers.map((marker) => (
        <MapMarker
          key={marker.id}
          marker={marker}
          selected={selectedId === marker.id}
          onSelect={onSelect}
        />
      ))}
    </div>
  );
}
