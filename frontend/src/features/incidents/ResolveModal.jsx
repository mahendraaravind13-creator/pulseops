import { useEffect, useState } from 'react'
import { Button } from '../../components/Button'
import { FormField } from '../../components/FormField'
import { Modal } from '../../components/Modal'

const MAX_NOTE_LENGTH = 2000

export function ResolveModal({ isOpen, onClose, onConfirm, isPending }) {
  const [note, setNote] = useState('')

  useEffect(() => {
    if (isOpen) setNote('')
  }, [isOpen])

  function handleSubmit(event) {
    event.preventDefault()
    onConfirm(note.trim() || undefined)
  }

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title="Resolve incident"
      description="Mark this incident as resolved. The AI will draft a post-mortem from the timeline."
      footer={
        <>
          <Button variant="ghost" onClick={onClose} disabled={isPending}>
            Cancel
          </Button>
          <Button variant="primary" type="submit" form="resolve-form" isLoading={isPending}>
            Resolve incident
          </Button>
        </>
      }
    >
      <form id="resolve-form" onSubmit={handleSubmit}>
        <FormField label="Resolution note (optional)" hint={`${note.length}/${MAX_NOTE_LENGTH}`}>
          <textarea
            className="input min-h-[110px] resize-y"
            maxLength={MAX_NOTE_LENGTH}
            placeholder="e.g. Rolled back deploy 4812"
            value={note}
            onChange={(event) => setNote(event.target.value)}
          />
        </FormField>
      </form>
    </Modal>
  )
}
