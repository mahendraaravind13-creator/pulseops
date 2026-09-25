import { useNow } from '../hooks/useNow'
import { formatRelativeTime } from '../lib/format'

export function RelativeTime({ iso, intervalMs = 1000, className = '' }) {
  const now = useNow(intervalMs)
  if (!iso) return <span className={className}>never</span>
  return (
    <time dateTime={iso} title={new Date(iso).toLocaleString()} className={className}>
      {formatRelativeTime(iso, now)}
    </time>
  )
}
