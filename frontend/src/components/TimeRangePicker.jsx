import { TIME_RANGES } from '../lib/constants'

export function TimeRangePicker({ value, onChange }) {
  return (
    <div role="radiogroup" aria-label="Time range" className="inline-flex rounded-lg border border-line bg-panel p-0.5">
      {TIME_RANGES.map((range) => {
        const isActive = range.value === value
        return (
          <button
            key={range.value}
            type="button"
            role="radio"
            aria-checked={isActive}
            onClick={() => onChange(range.value)}
            className={`rounded-md px-3 py-1 text-xs font-medium transition-colors ${
              isActive ? 'bg-zinc-700 text-zinc-50' : 'text-muted hover:text-zinc-100'
            }`}
          >
            {range.label}
          </button>
        )
      })}
    </div>
  )
}
