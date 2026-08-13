import type { MapMarkerData } from "../types/marker";
import { STATUS_LABEL, getMarkerLabel } from "../types/marker";
import "./DetailPanel.css";

interface Props {
  marker: MapMarkerData | null;
}

function DetailPanel({ marker }: Props) {
  if (!marker) {
    return <p className="detail__empty">지도 위 마커를 선택하세요</p>;
  }

  return (
    <div className="detail">
      <h2 className="detail__title">{getMarkerLabel(marker)}</h2>
      <p className="detail__value">{marker.detail ?? "데이터 없음"}</p>

      {marker.status && (
        <span className={`detail__badge detail__badge--${marker.status}`}>
          {STATUS_LABEL[marker.status]}
        </span>
      )}
    </div>
  );
}

export default DetailPanel;
