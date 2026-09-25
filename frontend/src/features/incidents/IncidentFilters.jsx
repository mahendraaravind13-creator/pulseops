import { useEffect, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Search, X } from 'lucide-react'
import { fetchServices } from '../../api/services'
import { queryKeys } from '../../api/queryKeys'
import { useDebouncedValue } from '../../hooks/useDebouncedValue'
import { INCIDENT_STATUSES, SEVERITIES } from '../../lib/constants'
import { toLabel } from '../../lib/format'

export function IncidentFilters({ filters, onChange, onClear, hasActiveFilters }) {
  const services = useQuery({ queryKey: queryKeys.services, queryFn: fetchServices })

  function toggleStatus(status) {
    const next = filters.status.includes(status)
      ? filters.status.filter((value) => value !== status)
      : [...filters.status, status]
    onChange({ status: INCIDENT_STATUSES.filter((value) => next.includes(value)) })
  }

  return (
    <div className="space-y-3 border-b border-line p-3">
      <div className="flex flex-wrap items-center gap-2">
        <SearchInput value={filters.q} onSearch={(q) => onChange({ q })} />
        <div role="group" aria-label="Status" className="flex flex-wrap gap-1.5">
          {INCIDENT_STATUSES.map((status) => {
            const isActive = filters.status.includes(status)
            return (
              <button
                key={status}
                type="button"
                aria-pressed={isActive}
                onClick={() => toggleStatus(status)}
                className={`rounded-full border px-3 py-1 text-xs font-medium transition-colors ${
                  isActive
                    ? 'border-emerald-500/60 bg-emerald-500/15 text-emerald-200'
                    : 'border-line text-muted hover:border-line-strong hover:text-zinc-100'
                }`}
              >
                {toLabel(status)}
              </button>
            )
          })}
        </div>
      </div>

      <div className="grid grid-cols-2 gap-2 sm:flex sm:flex-wrap sm:items-end">
        <label className="block">
          <span className="mb-1 block text-xs text-subtle">Severity</span>
          <select
            className="input sm:w-36"
            value={filters.severity}
            onChange={(event) => onChange({ severity: event.target.value })}
          >
            <option value="">All severities</option>
            {SEVERITIES.map((severity) => (
              <option key={severity} value={severity}>
                {toLabel(severity)}
              </option>
            ))}
          </select>
        </label>
        <label className="block">
          <span className="mb-1 block text-xs text-subtle">Service</span>
          <select
            className="input sm:w-48"
            value={filters.serviceId}
            onChange={(event) => onChange({ serviceId: event.target.value })}
          >
            <option value="">All services</option>
            {(services.data ?? []).map((service) => (
              <option key={service.id} value={String(service.id)}>
                {service.name}
              </option>
            ))}
          </select>
        </label>
        <label className="block">
          <span className="mb-1 block text-xs text-subtle">Opened from</span>
          <input
            type="date"
            className="input sm:w-40"
            value={filters.from}
            max={filters.to || undefined}
            onChange={(event) => onChange({ from: event.target.value })}
          />
        </label>
        <label className="block">
          <span className="mb-1 block text-xs text-subtle">Opened to</span>
          <input
            type="date"
            className="input sm:w-40"
            value={filters.to}
            min={filters.from || undefined}
            onChange={(event) => onChange({ to: event.target.value })}
          />
        </label>
        {hasActiveFilters && (
          <button
            type="button"
            onClick={onClear}
            className="inline-flex h-9 items-center gap-1 rounded-lg px-2 text-sm text-muted hover:text-zinc-100"
          >
            <X className="h-4 w-4" aria-hidden="true" />
            Clear filters
          </button>
        )}
      </div>
    </div>
  )
}

function SearchInput({ value, onSearch }) {
  const [text, setText] = useState(value)
  const debounced = useDebouncedValue(text, 300)

  useEffect(() => {
    setText(value)
  }, [value])

  useEffect(() => {
    if (debounced.trim() !== value) onSearch(debounced.trim())
    // eslint-disable-next-line react-hooks/exhaustive-deps -- only react to the debounced text
  }, [debounced])

  return (
    <label className="relative block w-full sm:w-72">
      <span className="sr-only">Search incidents</span>
      <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-subtle" aria-hidden="true" />
      <input
        type="search"
        className="input pl-9"
        placeholder="Search title or service"
        value={text}
        onChange={(event) => setText(event.target.value)}
      />
    </label>
  )
}
