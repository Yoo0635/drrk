import type { RouteData, RouteId } from "../types/route";
import "./RouteSelector.css";

interface Props {
  routes: RouteData[];
  selectedRouteId: RouteId;
  onChange: (routeId: RouteId) => void;
}

export default function RouteSelector({ routes, selectedRouteId, onChange }: Props) {
  return (
    <nav className="route-selector" aria-label="이동 경로 선택">
      {routes.map((route) => {
        const selected = route.id === selectedRouteId;

        return (
          <button
            key={route.id}
            type="button"
            className={`route-selector__button ${selected ? "is-selected" : ""}`}
            aria-pressed={selected}
            onClick={() => onChange(route.id)}
          >
            {route.label}
          </button>
        );
      })}
    </nav>
  );
}
