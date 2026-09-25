import { useCallback, useMemo } from 'react'
import { useSearchParams } from 'react-router-dom'
import { DEFAULT_PAGE_SIZE, INCIDENT_STATUSES } from '../../lib/constants'

export const DEFAULT_SORT = 'openedAt,desc'

function readFilters(searchParams) {
  return {
    status: (searchParams.get('status') ?? '').split(',').filter((value) => INCIDENT_STATUSES.includes(value)),
    severity: searchParams.get('severity') ?? '',
    serviceId: searchParams.get('serviceId') ?? '',
    q: searchParams.get('q') ?? '',
    from: searchParams.get('from') ?? '',
    to: searchParams.get('to') ?? '',
    page: Math.max(0, Number(searchParams.get('page') ?? 0) || 0),
    sort: searchParams.get('sort') ?? DEFAULT_SORT,
  }
}

function startOfDayIso(date) {
  return new Date(`${date}T00:00:00`).toISOString()
}

function endOfDayIso(date) {
  return new Date(`${date}T23:59:59.999`).toISOString()
}

export function toApiParams(filters) {
  const params = { page: filters.page, size: DEFAULT_PAGE_SIZE, sort: filters.sort }
  if (filters.status.length > 0) params.status = filters.status.join(',')
  if (filters.severity) params.severity = filters.severity
  if (filters.serviceId) params.serviceId = filters.serviceId
  if (filters.q) params.q = filters.q
  if (filters.from) params.from = startOfDayIso(filters.from)
  if (filters.to) params.to = endOfDayIso(filters.to)
  return params
}

export function useIncidentFilters() {
  const [searchParams, setSearchParams] = useSearchParams()
  const filters = useMemo(() => readFilters(searchParams), [searchParams])

  const updateFilters = useCallback(
    (changes) => {
      setSearchParams(
        (current) => {
          const next = new URLSearchParams(current)
          const resetsPage = !('page' in changes)
          for (const [key, value] of Object.entries(changes)) {
            const serialized = Array.isArray(value) ? value.join(',') : String(value ?? '')
            const isDefault = serialized === '' || (key === 'sort' && serialized === DEFAULT_SORT) || (key === 'page' && serialized === '0')
            if (isDefault) next.delete(key)
            else next.set(key, serialized)
          }
          if (resetsPage) next.delete('page')
          return next
        },
        { replace: true },
      )
    },
    [setSearchParams],
  )

  const clearFilters = useCallback(() => setSearchParams({}, { replace: true }), [setSearchParams])

  const hasActiveFilters =
    filters.status.length > 0 || Boolean(filters.severity || filters.serviceId || filters.q || filters.from || filters.to)

  return { filters, updateFilters, clearFilters, hasActiveFilters }
}
