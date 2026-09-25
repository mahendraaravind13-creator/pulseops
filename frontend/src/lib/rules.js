import { METRICS, OPERATORS } from './constants'

export function formatThreshold(metric, threshold) {
  const unit = METRICS[metric]?.unit ?? ''
  return unit === 'ms' ? `${threshold} ms` : `${threshold}${unit}`
}

export function describeRule({ severity, metric, operator, threshold, durationSeconds, serviceName }) {
  const metricLabel = METRICS[metric]?.label ?? metric
  const symbol = OPERATORS[operator]?.symbol ?? operator
  const value = threshold === '' || threshold === null || threshold === undefined ? '?' : formatThreshold(metric, threshold)
  const scope = serviceName ? `on ${serviceName}` : 'on all services'
  const seconds = Number(durationSeconds)
  const timing = seconds > 0 ? `for ${seconds}s` : 'on a single sample'
  return `Fire ${severity} when ${metricLabel} ${symbol} ${value} ${timing} ${scope}`
}

export function rulesForService(rules, serviceId) {
  return rules.filter((rule) => rule.enabled && (rule.serviceId === null || rule.serviceId === serviceId))
}
