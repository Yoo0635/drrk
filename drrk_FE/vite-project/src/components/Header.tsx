import "./Header.css";

interface Props {
  /** 데이터 기준 시각 "HH:MM" */
  updatedAt: string;
}

const LEGEND = [
  { key: "free", label: "여유" },
  { key: "normal", label: "보통" },
  { key: "busy", label: "혼잡" },
] as const;

export default function Header({ updatedAt }: Props) {
  return (
    <header className="header">
      <div className="header__brand">
        <span className="header__wordmark">drrk</span>
        <span className="header__place">인천공항 T1 · 도착층</span>
      </div>

      <div className="header__meta">
        <ul className="legend">
          {LEGEND.map((item) => (
            <li className={`legend__item legend__item--${item.key}`} key={item.key}>
              <i className="legend__dot" />
              {item.label}
            </li>
          ))}
        </ul>

        <span className="header__time tnum">{updatedAt} 기준</span>
      </div>
    </header>
  );
}
