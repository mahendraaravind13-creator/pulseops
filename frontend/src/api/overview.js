import { apiClient } from './client'

export async function fetchOverview() {
  const { data } = await apiClient.get('/api/v1/overview')
  return data
}
