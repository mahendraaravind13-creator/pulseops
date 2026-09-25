import { apiClient } from './client'

export async function fetchNotifications(limit = 10) {
  const { data } = await apiClient.get('/api/v1/notifications', { params: { limit } })
  return data
}

export async function markNotificationRead(id) {
  await apiClient.post(`/api/v1/notifications/${id}/read`)
}

export async function markAllNotificationsRead() {
  await apiClient.post('/api/v1/notifications/read-all')
}
