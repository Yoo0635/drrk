import type { MouseEvent as ReactMouseEvent } from "react";
import type { MapMarkerData } from "../types/marker";
import MapMarker from "./MapMarker";
import "./AirportMap.css";

interface Props {
  markers: MapMarkerData[];
  selectedId: string | null;
  onSelect: (id: string) => void;
  /** 좌표를 다 잡은 뒤 false로 넘기면 콘솔 로그가 꺼진다 */
  coordPicker?: boolean;
}

function AirportMap({ markers, selectedId, onSelect, coordPicker = true }: Props) {
  /** 지도를 클릭하면 콘솔에 % 좌표를 출력 */
  function pickCoord(e: ReactMouseEvent<HTMLDivElement>) {
    const rect = e.currentTarget.getBoundingClientRect();
    const x = ((e.clientX - rect.left) / rect.width) * 100;
    const y = ((e.clientY - rect.top) / rect.height) * 100;
    console.log(`x: ${x.toFixed(1)}, y: ${y.toFixed(1)}`);
  }

  return (
    <div className="map-frame" onClick={coordPicker ? pickCoord : undefined}>
      <img
        src="/images/airport-map.png"
        alt="인천공항 지도"
        className="map-frame__image"
        onError={() => console.error("지도 이미지를 불러오지 못했습니다")}
      />

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

export default AirportMap;
