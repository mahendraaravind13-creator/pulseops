import { ChevronLeft, ChevronRight } from 'lucide-react'
import { Button } from './Button'

export function Pagination({ page, onPageChange }) {
  if (!page || page.totalElements === 0) return null
  const { number, size, totalElements, totalPages } = page
  const first = number * size + 1
  const last = Math.min((number + 1) * size, totalElements)

  return (
    <nav
      className="flex flex-wrap items-center justify-between gap-3 border-t border-line px-4 py-3 text-sm text-muted"
      aria-label="Pagination"
    >
      <p>
        Showing <span className="text-zinc-200">{first}</span>–<span className="text-zinc-200">{last}</span> of{' '}
        <span className="text-zinc-200">{totalElements}</span>
      </p>
      <div className="flex items-center gap-2">
        <Button size="sm" icon={ChevronLeft} disabled={number === 0} onClick={() => onPageChange(number - 1)}>
          Previous
        </Button>
        <span className="tabular-nums">
          Page {number + 1} of {Math.max(totalPages, 1)}
        </span>
        <Button size="sm" disabled={number + 1 >= totalPages} onClick={() => onPageChange(number + 1)}>
          Next
          <ChevronRight className="h-4 w-4" aria-hidden="true" />
        </Button>
      </div>
    </nav>
  )
}
