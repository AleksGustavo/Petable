export function Logo({ className = "" }: { className?: string }) {
  return (
    <span className={`inline-flex items-center gap-2 font-semibold tracking-tight ${className}`}>
      <svg viewBox="0 0 32 32" aria-hidden="true" className="size-8 shrink-0">
        <rect width="32" height="32" rx="9" className="fill-brand" />
        <g className="fill-white">
          <ellipse cx="10.5" cy="12" rx="2.3" ry="2.9" />
          <ellipse cx="16" cy="9.5" rx="2.3" ry="2.9" />
          <ellipse cx="21.5" cy="12" rx="2.3" ry="2.9" />
          <path d="M16 15.5c-3.6 0-6.5 3.2-6.5 5.8 0 1.8 1.4 2.7 3 2.7 1.3 0 2.2-.6 3.5-.6s2.2.6 3.5.6c1.6 0 3-.9 3-2.7 0-2.6-2.9-5.8-6.5-5.8Z" />
        </g>
      </svg>
      <span>
        PetConnect <span className="text-brand">Web</span>
      </span>
    </span>
  );
}
