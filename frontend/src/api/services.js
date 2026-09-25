import { apiClient } from './client'

export async function fetchServices() {
  const { data } = await apiClient.get('/api/v1/services')
  return data
}

export async function fetchService(id) {
  const { data } = await apiClient.get(`/api/v1/services/${id}`)
  return data
}

export async function fetchServiceMetrics(id, range) {
  const { data } = await apiClient.get(`/api/v1/services/${id}/metrics`, { params: { range } })
  return data
}
