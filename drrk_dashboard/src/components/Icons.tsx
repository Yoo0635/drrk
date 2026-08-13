interface IconProps {
  size?: number;
}

export function ArrIcon({ size = 22 }: IconProps) {
  return (
    <svg viewBox="0 0 24 24" width={size} height={size} fill="#c6d8e6">
      <path d="M2 16l20-6-3 8-6-1-4 5-1-4-6-2z" />
    </svg>
  );
}

export function DepIcon({ size = 22 }: IconProps) {
  return (
    <svg viewBox="0 0 24 24" width={size} height={size} fill="#c6d8e6">
      <path d="M2 8l20 6-3-8-6 1-4-5-1 4-6 2z" />
    </svg>
  );
}

export function PlaneIcon({ size = 22 }: IconProps) {
  return (
    <svg viewBox="0 0 24 24" width={size} height={size} fill="#c6d8e6">
      <path d="M12 2c-1.4 0-2.5 1.6-2.5 3.6v3.2L2 13v2.4l7.5-2v4.2L7 19.6V22l5-1.2 5 1.2v-2.4l-2.5-2v-4.2l7.5 2V13l-7.5-4.2V5.6C14.5 3.6 13.4 2 12 2z" />
    </svg>
  );
}

export function TrsIcon({ size = 20 }: IconProps) {
  return (
    <svg
      viewBox="0 0 24 24"
      width={size}
      height={size}
      fill="none"
      stroke="#c9b6e8"
      strokeWidth={2}
    >
      <path d="M4 12h16M14 6l6 6-6 6" />
    </svg>
  );
}
