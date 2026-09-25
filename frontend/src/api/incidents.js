import { apiClient } from './client'

export async function fetchIncidents(params) {
  const { data } = await apiClient.get('/api/v1/incidents', { params })
  return data
}

export async function fetchIncident(id) {
  const { data } = await apiClient.get(`/api/v1/incidents/${id}`)
  return data
}

export async function acknowledgeIncident({ id, version }) {
  const { data } = await apiClient.post(`/api/v1/incidents/${id}/acknowledge`, { version })
  return data
}

export async function resolveIncident({ id, version, note }) {
  const body = note ? { version, note } : { version }
  const { data } = await apiClient.post(`/api/v1/incidents/${id}/resolve`, body)
  return data
}

export async function addIncidentNote({ id, message }) {
  const { data } = await apiClient.post(`/api/v1/incidents/${id}/notes`, { message })
  return data
}

export async function retryAnalysis(id) {
  const { data } = await apiClient.post(`/api/v1/incidents/${id}/analysis/retry`)
  return data
}

export async function retryPostmortem(id) {
  const { data } = await apiClient.post(`/api/v1/incidents/${id}/postmortem/retry`)
  return data
}
