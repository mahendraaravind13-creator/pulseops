export function Skeleton({ className = '' }) {
  return <div className={`animate-pulse rounded-md bg-zinc-800/70 ${className}`} aria-hidden="true" />
}
