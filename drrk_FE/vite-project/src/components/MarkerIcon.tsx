import type { MarkerType } from "../types/marker";

interface Props {
  type: MarkerType;
}

/** 외부 아이콘 라이브러리 없이 인라인 SVG로 처리 */
function MarkerIcon({ type }: Props) {
  const common = {
    viewBox: "0 0 24 24",
    fill: "none",
    stroke: "currentColor",
    strokeWidth: 2,
    strokeLinecap: "round" as const,
    strokeLinejoin: "round" as const,
    "aria-hidden": true,
  };

  switch (type) {
    case "current":
      return (
        <svg {...common}>
          <circle cx="12" cy="12" r="3" fill="currentColor" stroke="none" />
          <circle cx="12" cy="12" r="8" />
        </svg>
      );
    case "movingwalk":
      return (
        <svg {...common}>
          {/* 벨트 */}
          <rect x="2" y="14" width="20" height="5" rx="2.5" />
          {/* 진행 방향 */}
          <path d="M7 9h8M12 6l3 3-3 3" />
        </svg>
      );
    case "rail":
      return (
        <svg {...common}>
          <rect x="4" y="3" width="16" height="16" rx="2" />
          <path d="M4 11h16M12 3v8M8 19l-2 3M18 22l-2-3M8 15h.01M16 15h.01" />
        </svg>
      );
  }
}

export default MarkerIcon;
