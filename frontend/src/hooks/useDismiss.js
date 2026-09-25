import { useEffect } from 'react'

export function useDismiss(ref, isOpen, onDismiss) {
  useEffect(() => {
    if (!isOpen) return undefined

    function handlePointer(event) {
      if (ref.current && !ref.current.contains(event.target)) onDismiss()
    }
    function handleKey(event) {
      if (event.key === 'Escape') onDismiss()
    }

    document.addEventListener('mousedown', handlePointer)
    document.addEventListener('keydown', handleKey)
    return () => {
      document.removeEventListener('mousedown', handlePointer)
      document.removeEventListener('keydown', handleKey)
    }
  }, [ref, isOpen, onDismiss])
}
