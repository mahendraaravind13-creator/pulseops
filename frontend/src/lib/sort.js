export function nextSort(currentSort, key, defaultDirection = 'desc') {
  if (currentSort?.key !== key) return { key, direction: defaultDirection }
  return { key, direction: currentSort.direction === 'asc' ? 'desc' : 'asc' }
}

export function parseSortParam(value, fallback) {
  const [key, direction] = (value || fallback).split(',')
  return { key, direction: direction === 'asc' ? 'asc' : 'desc' }
}

export function toSortParam(sort) {
  return `${sort.key},${sort.direction}`
}

export function compareValues(a, b) {
  if (a === b) return 0
  if (a === null || a === undefined) return 1
  if (b === null || b === undefined) return -1
  if (typeof a === 'string') return a.localeCompare(b)
  return a < b ? -1 : 1
}
