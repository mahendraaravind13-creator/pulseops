export const METRICS = {
  CPU: { label: 'CPU', field: 'cpu', unit: '%' },
  MEMORY: { label: 'Memory', field: 'memory', unit: '%' },
  DISK: { label: 'Disk', field: 'disk', unit: '%' },
  LATENCY_MS: { label: 'Latency', field: 'latencyMs', unit: 'ms' },
  ERROR_RATE: { label: 'Error rate', field: 'errorRate', unit: '%' },
}

export const METRIC_OPTIONS = Object.keys(METRICS)

export const OPERATORS = {
  GT: { symbol: '>', label: 'greater than' },
  LT: { symbol: '<', label: 'less than' },
}

export const SEVERITIES = ['CRITICAL', 'WARNING']

export const INCIDENT_STATUSES = ['OPEN', 'ACKNOWLEDGED', 'RESOLVED']

export const TIME_RANGES = [
  { value: '15m', label: '15m', seconds: 15 * 60 },
  { value: '1h', label: '1h', seconds: 60 * 60 },
  { value: '6h', label: '6h', seconds: 6 * 60 * 60 },
  { value: '24h', label: '24h', seconds: 24 * 60 * 60 },
  { value: '7d', label: '7d', seconds: 7 * 24 * 60 * 60 },
]

export const DEFAULT_PAGE_SIZE = 20

// The agent must talk to whichever origin serves this dashboard (localhost in development, the public
// hostname when deployed), so the URL is read from the page instead of being hard-coded.
export function agentInstallCommand(apiKey = '<your-api-key>') {
  const url = typeof window === 'undefined' ? 'http://localhost:8088' : window.location.origin
  return `python pulseops_agent.py --api-key ${apiKey} --service checkout-api --url ${url}`
}
