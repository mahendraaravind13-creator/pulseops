import { ChevronDown, ChevronsUpDown, ChevronUp } from 'lucide-react'
import { Skeleton } from './Skeleton'

// columns: [{ key, header, render(row), sortable, align, className }]
// sort: { key, direction: 'asc' | 'desc' }
export function DataTable({
  columns,
  rows,
  getRowKey = (row) => row.id,
  isLoading = false,
  skeletonRows = 5,
  emptyMessage = 'No results',
  onRowClick,
  sort,
  onSort,
  caption,
}) {
  function handleRowClick(event, row) {
    if (!onRowClick) return
    if (event.target.closest('a, button, input, select, label')) return
    onRowClick(row)
  }

  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[640px] text-left text-sm">
        {caption && <caption className="sr-only">{caption}</caption>}
        <thead>
          <tr className="border-b border-line text-xs uppercase tracking-wide text-subtle">
            {columns.map((column) => (
              <th
                key={column.key}
                scope="col"
                className={`px-4 py-2.5 font-medium ${column.align === 'right' ? 'text-right' : ''} ${column.className ?? ''}`}
                aria-sort={ariaSort(sort, column.key)}
              >
                {column.sortable && onSort ? (
                  <SortButton column={column} sort={sort} onSort={onSort} />
                ) : (
                  column.header
                )}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {isLoading &&
            Array.from({ length: skeletonRows }, (_, index) => (
              <tr key={`skeleton-${index}`} className="border-b border-line/60">
                {columns.map((column) => (
                  <td key={column.key} className="px-4 py-3">
                    <Skeleton className="h-4 w-full max-w-[140px]" />
                  </td>
                ))}
              </tr>
            ))}

          {!isLoading && rows.length === 0 && (
            <tr>
              <td colSpan={columns.length} className="px-4 py-10 text-center text-sm text-muted">
                {emptyMessage}
              </td>
            </tr>
          )}

          {!isLoading &&
            rows.map((row) => (
              <tr
                key={getRowKey(row)}
                onClick={(event) => handleRowClick(event, row)}
                className={`border-b border-line/60 last:border-0 ${onRowClick ? 'cursor-pointer hover:bg-zinc-900/80' : ''}`}
              >
                {columns.map((column) => (
                  <td
                    key={column.key}
                    className={`px-4 py-3 align-middle ${column.align === 'right' ? 'text-right tabular-nums' : ''} ${column.className ?? ''}`}
                  >
                    {column.render ? column.render(row) : row[column.key]}
                  </td>
                ))}
              </tr>
            ))}
        </tbody>
      </table>
    </div>
  )
}

function SortButton({ column, sort, onSort }) {
  const isActive = sort?.key === column.key
  const Icon = !isActive ? ChevronsUpDown : sort.direction === 'asc' ? ChevronUp : ChevronDown
  return (
    <button
      type="button"
      onClick={() => onSort(column.key)}
      className={`inline-flex items-center gap-1 uppercase tracking-wide hover:text-zinc-200 ${isActive ? 'text-zinc-200' : ''} ${column.align === 'right' ? 'flex-row-reverse' : ''}`}
    >
      {column.header}
      <Icon className="h-3.5 w-3.5" aria-hidden="true" />
    </button>
  )
}

function ariaSort(sort, key) {
  if (sort?.key !== key) return undefined
  return sort.direction === 'asc' ? 'ascending' : 'descending'
}
