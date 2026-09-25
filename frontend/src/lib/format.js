import { METRICS } from './constants'

const EMPTY = '—'

export function formatDuration(totalSeconds) {
  if (totalSeconds === null || totalSeconds === undefined || Number.isNaN(totalSeconds)) return EMPTY
  const seconds = Math.max(0, Math.round(totalSeconds))
  const units = [
    ['d', 86400],
    ['h', 3600],
    ['m', 60],
    ['s', 1],
  ]
  const parts = []
  let remaining = seconds
  for (const [suffix, size] of units) {
    const amount = Math.floor(remaining / size)
    remaining -= amount * size
    if (amount > 0 || parts.length > 0) parts.push(`${amount}${suffix}`)
    if (parts.length === 2) break
  }
  if (parts.length === 0) return '0s'
  return parts.filter((part, index) => index === 0 || !part.startsWith('0')).join(' ')
}

export function secondsBetween(fromIso, toIso) {
  if (!fromIso) return null
  const end = toIso ? new Date(toIso).getTime() : Date.now()
  return (end - new Date(fromIso).getTime()) / 1000
}

export function formatRelativeTime(iso, now = Date.now()) {
  if (!iso) return 'never'
  const seconds = Math.round((now - new Date(iso).getTime()) / 1000)
  if (seconds < 5) return 'just now'
  if (seconds < 60) return `${seconds}s ago`
  const minutes = Math.floor(seconds / 60)
  if (minutes < 60) return `${minutes}m ago`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours}h ago`
  const days = Math.floor(hours / 24)
  return `${days}d ago`
}

export function formatDateTime(iso) {
  if (!iso) return EMPTY
  return new Date(iso).toLocaleString(undefined, {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

export function formatPercent(value) {
  if (value === null || value === undefined) return EMPTY
  return `${Number(value).toFixed(1)}%`
}

export function formatMetricValue(metric, value) {
  if (value === null || value === undefined) return EMPTY
  const unit = METRICS[metric]?.unit
  if (unit === 'ms') return `${Number(value).toFixed(0)} ms`
  return formatPercent(value)
}

export function formatNumber(value) {
  if (value === null || value === undefined) return EMPTY
  return Number(value).toLocaleString()
}

export function toLabel(value) {
  if (!value) return ''
  const words = value.toLowerCase().replace(/_/g, ' ')
  return words.charAt(0).toUpperCase() + words.slice(1)
}
