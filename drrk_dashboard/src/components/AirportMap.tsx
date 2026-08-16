import { STAND_DOTS, STATUS_COLOR } from "../data/dashboard";

const RUNWAY_X = [120, 158, 444, 482];

const CHIPS: Array<[number, number, string]> = [
  [30, 18, "↑ 2000m"],
  [104, 18, "↑ 2000m"],
  [440, 18, "↓ 2000m"],
  [514, 18, "↓ 2000m"],
  [30, 612, "↓ 2000m"],
  [104, 612, "↓ 2000m"],
  [440, 612, "↓ 2000m"],
  [514, 612, "↓ 2000m"],
];

const LABELS: Array<[number, number, string]> = [
  [118, 52, "16R"],
  [156, 42, "16L"],
  [446, 52, "15R"],
  [484, 52, "15L"],
  [118, 602, "34L"],
  [156, 602, "34R"],
  [446, 602, "33L"],
  [484, 602, "33R"],
];

export function AirportMap() {
  return (
    <svg
      viewBox="0 0 620 640"
      style={{ width: "100%", height: "100%", display: "block" }}
      role="img"
      aria-label="계류장 현황도"
    >
      <defs>
        <linearGradient id="fieldG" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor="#4a5c40" />
          <stop offset="1" stopColor="#3d4d36" />
        </linearGradient>
        <pattern
          id="grass"
          width="14"
          height="14"
          patternUnits="userSpaceOnUse"
        >
          <rect width="14" height="14" fill="url(#fieldG)" />
          <circle cx="4" cy="5" r="1.6" fill="#54683f" opacity={0.45} />
          <circle cx="11" cy="11" r="1.2" fill="#455737" opacity={0.5} />
        </pattern>
      </defs>

      <rect width="620" height="640" fill="url(#grass)" />

      {/* apron */}
      <rect
        x="196"
        y="34"
        width="230"
        height="574"
        rx="6"
        fill="#d9d6cd"
        stroke="#c2beb3"
      />
      <rect
        x="330"
        y="330"
        width="230"
        height="130"
        rx="4"
        fill="#cfcbc0"
        opacity={0.85}
      />
      <rect
        x="352"
        y="480"
        width="200"
        height="110"
        rx="4"
        fill="#cfcbc0"
        opacity={0.7}
      />
      <rect x="256" y="46" width="96" height="60" rx="3" fill="#c6c2b7" />
      <g stroke="#c6c2b7" strokeWidth={2} opacity={0.8}>
        <path d="M200 150h222M200 250h222M200 390h222M200 520h222" />
      </g>

      {/* runways */}
      {RUNWAY_X.map((x) => (
        <g key={x} transform={`translate(${x},0)`}>
          <rect
            x="0"
            y="60"
            width="17"
            height="520"
            fill="#2f3a33"
            stroke="#e6ece4"
            strokeWidth={1.2}
          />
          <path
            d="M8.5 70 V570"
            stroke="#e9eee7"
            strokeWidth={1}
            strokeDasharray="12 10"
          />
        </g>
      ))}

      {/* terminals */}
      <g fill="#8fb4d4" stroke="#5c86ad" strokeWidth={1.5}>
        <path d="M246 208c26-30 62-30 88 0 14 16 14 40 0 56-24 28-64 28-88 0-14-16-14-40 0-56z" />
        <rect x="262" y="272" width="56" height="34" rx="3" />
        <path d="M232 196c-16 10-22 30-16 46l14-6c-4-12 0-26 10-32z" />
        <path d="M348 196c16 10 22 30 16 46l-14-6c4-12 0-26-10-32z" />
        <path d="M262 548c18-22 42-22 60 0 10 12 10 28 0 40-16 18-44 18-60 0-10-12-10-28 0-40z" />
        <rect x="276" y="592" width="32" height="20" rx="2" />
      </g>

      {/* aircraft stands */}
      <g>
        {STAND_DOTS.map((group) =>
          group.points.map(([cx, cy], i) => (
            <circle
              key={`${group.status}-${i}`}
              cx={cx}
              cy={cy}
              r={5.2}
              fill={STATUS_COLOR[group.status]}
              stroke="rgba(0,0,0,.35)"
              strokeWidth={0.8}
            />
          )),
        )}
      </g>

      {/* runway labels */}
      <g fill="#f2f7fb" fontSize={13} fontWeight={700}>
        {LABELS.map(([x, y, t]) => (
          <text key={`${t}-${x}`} x={x} y={y} textAnchor="middle">
            {t}
          </text>
        ))}
      </g>

      {/* distance chips */}
      <g fontSize={11} fontWeight={700}>
        {CHIPS.map(([x, y, t], i) => (
          <g key={i}>
            <rect
              x={x}
              y={y}
              width="66"
              height="18"
              rx="3"
              fill="#0f1d14"
              opacity={0.85}
            />
            <text x={x + 33} y={y + 13} textAnchor="middle" fill="#7ee08f">
              {t}
            </text>
          </g>
        ))}
      </g>

      {/* compass */}
      <g
        transform="translate(560,70)"
        fill="#f2f7fb"
        fontSize={10}
        fontWeight={700}
      >
        <path d="M0-22 L6 0 L0 22 L-6 0Z" fill="#e8f1f8" opacity={0.9} />
        <text x="0" y="-26" textAnchor="middle">
          N
        </text>
        <text x="0" y="34" textAnchor="middle">
          S
        </text>
        <text x="20" y="4" textAnchor="middle">
          E
        </text>
        <text x="-20" y="4" textAnchor="middle">
          W
        </text>
      </g>
    </svg>
  );
}
