import { apiClient } from './client'

export async function fetchSettings() {
  const { data } = await apiClient.get('/api/v1/settings')
  return data
}

export async function rotateApiKey() {
  const { data } = await apiClient.post('/api/v1/settings/api-key/rotate')
  return data
}

export async function updateWebhook(webhookUrl) {
  const { data } = await apiClient.put('/api/v1/settings/webhook', { webhookUrl })
  return data
}

export async function fetchWebhookDeliveries(limit = 20) {
  const { data } = await apiClient.get('/api/v1/settings/webhook/deliveries', { params: { limit } })
  return data
}
