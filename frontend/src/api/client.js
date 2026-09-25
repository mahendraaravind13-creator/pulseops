import axios from 'axios'
import { getToken } from '../lib/auth-storage'

export const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '',
  headers: { 'Content-Type': 'application/json' },
})

let handleUnauthorized = () => {}

export function setUnauthorizedHandler(handler) {
  handleUnauthorized = handler
}

apiClient.interceptors.request.use((config) => {
  const token = getToken()
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    const isUnauthorized = error.response?.status === 401
    if (isUnauthorized && !error.config?.skipAuthRedirect) {
      handleUnauthorized()
    }
    return Promise.reject(error)
  },
)
