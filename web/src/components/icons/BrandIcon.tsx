import React, { SVGProps } from "react";

export interface BrandIconProps extends SVGProps<SVGSVGElement> {
  color?: string;
  strokeWidth?: number;
}

export function BrandIcon({
  className = "w-4 h-4",
  color = "currentColor",
  strokeWidth = 2.4,
  ...props
}: BrandIconProps) {
  return (
    <svg
      className={className}
      viewBox="0 0 24 24"
      fill="none"
      stroke={color}
      strokeWidth={strokeWidth}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      {...props}
    >
      <path d="M12 2a10 10 0 1 1-10 10" />
      <circle cx="12" cy="12" r="6" />
      <path d="M10.5 9.5 15 12l-4.5 2.5v-5z" fill={color} stroke="none" />
    </svg>
  );
}
