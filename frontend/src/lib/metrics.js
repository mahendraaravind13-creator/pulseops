import { METRICS, OPERATORS } from './constants'
import { formatThreshold } from './rules'

const THRESHOLD_COLORS = {
  CRITICAL: '#f87171',
  WARNING: '#fbbf24',
}

// Converts API points to chart rows. When two consecutive points are more than two buckets apart
// (the agent stopped reporting), a null row is inserted so the line breaks instead of drawing
// a straight segment through time where no data exists.
export function toChartData(points, field, bucketSeconds) {
  const rows = []
  const maxGapMs = bucketSeconds ? bucketSeconds * 2 * 1000 : Infinity
  for (const point of points ?? []) {
    const time = new Date(point.t).getTime()
    const previous = rows[rows.length - 1]
    if (previous && time - previous.time > maxGapMs) {
      rows.push({ time: previous.time + bucketSeconds * 1000, value: null })
    }
    rows.push({ time, value: point[field] ?? null })
  }
  return rows
}

export function hasAnyValue(points, field) {
  return (points ?? []).some((point) => point[field] !== null && point[field] !== undefined)
}

export function thresholdLine({ id, name, metric, operator, threshold, severity }) {
  const symbol = OPERATORS[operator]?.symbol ?? ''
  const prefix = name ? `${name} ` : ''
  return {
    id,
    value: threshold,
    label: `${prefix}${symbol} ${formatThreshold(metric, threshold)}`,
    color: THRESHOLD_COLORS[severity] ?? THRESHOLD_COLORS.WARNING,
  }
}

export function thresholdsForMetric(rules, metric) {
  return rules.filter((rule) => rule.metric === metric).map(thresholdLine)
}

export function metricField(metric) {
  return METRICS[metric]?.field
}
