import { apiClient } from './client'

export async function fetchRules() {
  const { data } = await apiClient.get('/api/v1/rules')
  return data
}

export async function createRule(rule) {
  const { data } = await apiClient.post('/api/v1/rules', rule)
  return data
}

export async function updateRule({ id, ...rule }) {
  const { data } = await apiClient.put(`/api/v1/rules/${id}`, rule)
  return data
}

export async function setRuleEnabled({ id, enabled }) {
  const { data } = await apiClient.patch(`/api/v1/rules/${id}/enabled`, { enabled })
  return data
}

export async function deleteRule(id) {
  await apiClient.delete(`/api/v1/rules/${id}`)
}
