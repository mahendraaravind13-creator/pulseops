import { Button } from './Button'
import { Modal } from './Modal'

export function ConfirmDialog({
  isOpen,
  title,
  message,
  confirmLabel = 'Confirm',
  tone = 'danger',
  isPending = false,
  onConfirm,
  onCancel,
}) {
  return (
    <Modal
      isOpen={isOpen}
      onClose={onCancel}
      title={title}
      size="sm"
      footer={
        <>
          <Button variant="ghost" onClick={onCancel} disabled={isPending}>
            Cancel
          </Button>
          <Button variant={tone === 'danger' ? 'danger' : 'primary'} onClick={onConfirm} isLoading={isPending}>
            {confirmLabel}
          </Button>
        </>
      }
    >
      <p className="text-sm text-zinc-300">{message}</p>
    </Modal>
  )
}
