import { useState } from 'react'
import { MessageSquare } from 'lucide-react'
import { Button } from '../../components/Button'

export function AddNoteForm({ onSubmit, isPending }) {
  const [message, setMessage] = useState('')

  function handleSubmit(event) {
    event.preventDefault()
    const trimmed = message.trim()
    if (!trimmed) return
    onSubmit(trimmed, { onSuccess: () => setMessage('') })
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-2">
      <label htmlFor="incident-note" className="block text-sm font-medium text-zinc-300">
        Add a note
      </label>
      <textarea
        id="incident-note"
        className="input min-h-[72px] resize-y"
        maxLength={2000}
        placeholder="What did you find?"
        value={message}
        onChange={(event) => setMessage(event.target.value)}
      />
      <div className="flex justify-end">
        <Button type="submit" size="sm" icon={MessageSquare} isLoading={isPending} disabled={!message.trim()}>
          Add note
        </Button>
      </div>
    </form>
  )
}
