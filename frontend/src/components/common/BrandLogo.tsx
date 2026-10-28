interface BrandLogoProps {
  className?: string;
  size?: number;
}

export function BrandLogo({ className = '', size = 28 }: BrandLogoProps) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 32 32"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      className={className}
      aria-hidden="true"
    >
      <defs>
        <linearGradient id="brandGradLeft" x1="0%" y1="0%" x2="100%" y2="100%">
          <stop offset="0%" stopColor="#6366F1" />
          <stop offset="100%" stopColor="#8B5CF6" />
        </linearGradient>
        <linearGradient id="brandGradRight" x1="0%" y1="0%" x2="100%" y2="100%">
          <stop offset="0%" stopColor="#EC4899" />
          <stop offset="100%" stopColor="#F59E0B" />
        </linearGradient>
        <linearGradient id="brandGradCenter" x1="0%" y1="0%" x2="100%" y2="100%">
          <stop offset="0%" stopColor="#3B82F6" />
          <stop offset="100%" stopColor="#06B6D4" />
        </linearGradient>
      </defs>
      {/* Modern faceted triangular prism mark matching reference screenshot */}
      <path
        d="M16 2L3 26h10.5L16 20l2.5 6H29L16 2z"
        fill="url(#brandGradLeft)"
        opacity="0.95"
      />
      <path
        d="M16 2l13 24h-7.5l-5.5-12-3 6-3-6-3 6h-4L16 2z"
        fill="url(#brandGradRight)"
        opacity="0.9"
      />
      <path
        d="M16 8l5 12h-10l5-12z"
        fill="url(#brandGradCenter)"
      />
    </svg>
  );
}
