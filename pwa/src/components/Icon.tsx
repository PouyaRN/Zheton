import React from 'react';
import { resolveIconNodes } from '../utils/iconMap';

export interface IconProps {
  name: string;
  size?: number;
  className?: string;
  strokeWidth?: number;
  ariaLabel?: string;
}

function extractPixelSizeFromClass(className?: string): number | undefined {
  if (!className) return undefined;
  const match = className.match(/text-\[(\d+)px\]/);
  if (match) {
    const parsed = Number(match[1]);
    if (Number.isFinite(parsed) && parsed > 0) return parsed;
  }
  return undefined;
}

export const Icon: React.FC<IconProps> = ({
  name,
  size,
  className = '',
  strokeWidth = 2,
  ariaLabel,
}) => {
  const nodes = resolveIconNodes(name);
  const resolvedSize = size ?? extractPixelSizeFromClass(className) ?? 20;

  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      width={resolvedSize}
      height={resolvedSize}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={strokeWidth}
      strokeLinecap="round"
      strokeLinejoin="round"
      className={`inline-block shrink-0 ${className}`.trim()}
      aria-hidden={ariaLabel ? undefined : true}
      aria-label={ariaLabel}
      role={ariaLabel ? 'img' : undefined}
      data-icon={name}
    >
      {nodes.map(([tag, attrs], idx) => React.createElement(tag, { key: idx, ...attrs }))}
    </svg>
  );
};
