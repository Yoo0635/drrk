import type { MouseEvent as ReactMouseEvent } from "react";
import type { MapMarkerData } from "../types/marker";
import { getMarkerLabel } from "../types/marker";
import MarkerIcon from "./MarkerIcon";
import "./MapMarker.css";

interface Props {
  marker: MapMarkerData;
  selected: boolean;
  onSelect: (id: string) => void;
}

function MapMarker({ marker, selected, onSelect }: Props) {
  function handleClick(e: ReactMouseEvent<HTMLButtonElement>) {
    e.stopPropagation(); // 지도의 좌표 로그가 함께 실행되지 않도록 차단
    onSelect(marker.id);
  }

  const className = [
    "map-marker",
    `map-marker--${marker.type}`,
    marker.status ? `map-marker--${marker.status}` : "",
    selected ? "is-selected" : "",
  ]
    .filter(Boolean)
    .join(" ");

  return (
    <button
      type="button"
      className={className}
      style={{ left: `${marker.x}%`, top: `${marker.y}%` }}
      onClick={handleClick}
      aria-pressed={selected}
      aria-label={getMarkerLabel(marker)}
    >
      <span className="map-marker__icon">
        <MarkerIcon type={marker.type} />
      </span>
      <span className="map-marker__label">{getMarkerLabel(marker)}</span>
    </button>
  );
}

export default MapMarker;
