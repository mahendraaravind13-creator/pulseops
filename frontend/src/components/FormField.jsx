import { cloneElement, isValidElement, useId } from 'react'

export function FormField({ label, error, hint, children, className = '' }) {
  const id = useId()
  const hintId = hint ? `${id}-hint` : undefined
  const errorId = error ? `${id}-error` : undefined
  const describedBy = [hintId, errorId].filter(Boolean).join(' ') || undefined

  const control = isValidElement(children)
    ? cloneElement(children, {
        id,
        'aria-invalid': error ? true : undefined,
        'aria-describedby': describedBy,
        className: `${children.props.className ?? ''} ${error ? 'input-invalid' : ''}`.trim(),
      })
    : children

  return (
    <div className={className}>
      <label htmlFor={id} className="mb-1.5 block text-sm font-medium text-zinc-300">
        {label}
      </label>
      {control}
      {hint && !error && (
        <p id={hintId} className="mt-1 text-xs text-subtle">
          {hint}
        </p>
      )}
      {error && (
        <p id={errorId} className="mt-1 text-xs text-red-400">
          {error}
        </p>
      )}
    </div>
  )
}
