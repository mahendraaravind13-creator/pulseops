import { apiClient } from './client'

export async function login(credentials) {
  const { data } = await apiClient.post('/api/v1/auth/login', credentials, { skipAuthRedirect: true })
  return data
}

export async function register(payload) {
  const { data } = await apiClient.post('/api/v1/auth/register', payload, { skipAuthRedirect: true })
  return data
}

export async function fetchMe() {
  const { data } = await apiClient.get('/api/v1/auth/me')
  return data
}
