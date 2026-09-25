export const queryKeys = {
  overview: ['overview'],
  services: ['services'],
  service: (id) => ['services', id],
  serviceMetrics: (id, range) => ['services', id, 'metrics', range],
  incidents: (params) => ['incidents', 'list', params],
  incidentLists: ['incidents', 'list'],
  incident: (id) => ['incidents', 'detail', id],
  rules: ['rules'],
  notifications: ['notifications'],
  settings: ['settings'],
  webhookDeliveries: ['settings', 'webhook-deliveries'],
}
