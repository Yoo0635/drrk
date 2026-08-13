import {
  CONGESTION,
  FORECAST,
  RAIL,
  ROUTE_TIMES,
} from "../data/dashboard";
import { IMPACT_LABEL, formatDuration } from "../types/dashboard";
import { STATUS_LABEL } from "../types/marker";
import "./Dashboard.css";

/** 영향도 → 배지 스타일 */
const IMPACT_CLASS = {
  low: "badge--free",
  medium: "badge--normal",
  high: "badge--busy",
} as const;

export default function Dashboard() {
  /** 가장 빠른 경로를 프라이머리로 강조 */
  const fastestId = ROUTE_TIMES.reduce((min, cur) =>
    cur.seconds < min.seconds ? cur : min
  ).id;

  return (
    <section className="dashboard">
      {/* 실시간 혼잡도 */}
      <div className="panel">
        <h3 className="panel__title">실시간 혼잡도</h3>

        {CONGESTION.map((item) => (
          <div className="row" key={item.id}>
            <span className="row__label">{item.label} 무빙워크</span>
            <span className={`status status--${item.status}`}>
              <i className="status__dot" />
              {STATUS_LABEL[item.status]}
            </span>
          </div>
        ))}
      </div>

      {/* 경로별 예상시간 */}
      <div className="panel">
        <h3 className="panel__title">경로별 예상시간</h3>

        {ROUTE_TIMES.map((item) => {
          const fastest = item.id === fastestId;
          return (
            <div className="row" key={item.id}>
              <span className="row__label">
                {item.label} 경로
                {fastest && <span className="tag">최단</span>}
              </span>
              <span className={`row__value tnum ${fastest ? "is-primary" : ""}`}>
                {formatDuration(item.seconds)}
              </span>
            </div>
          );
        })}
      </div>

      {/* 공항철도 */}
      <div className="panel">
        <h3 className="panel__title">공항철도</h3>

        <p className="panel__caption">다음 열차</p>
        <p className="panel__headline tnum">{RAIL.nextDeparture}</p>

        <span className={`badge ${RAIL.boardable ? "badge--free" : "badge--busy"}`}>
          {RAIL.boardable ? "탑승 가능" : "탑승 어려움"}
        </span>
      </div>

      {/* 입국 혼잡 예보 */}
      <div className="panel">
        <h3 className="panel__title">입국 혼잡 예보</h3>

        <p className="panel__caption">{FORECAST.flightNo} 도착 예정</p>
        <p className="panel__headline tnum">{FORECAST.eta}</p>

        <span className={`badge ${IMPACT_CLASS[FORECAST.impact]}`}>
          {IMPACT_LABEL[FORECAST.impact]}
        </span>
      </div>
    </section>
  );
}
