import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { KeyRound, RefreshCw } from 'lucide-react'
import { rotateApiKey } from '../../api/settings'
import { queryKeys } from '../../api/queryKeys'
import { AgentSnippet } from '../../components/AgentSnippet'
import { Button } from '../../components/Button'
import { CodeBlock } from '../../components/CodeBlock'
import { ConfirmDialog } from '../../components/ConfirmDialog'
import { Modal } from '../../components/Modal'
import { useToast } from '../../hooks/useToast'
import { getErrorMessage } from '../../lib/errors'
import { SettingsCard } from './SettingsCard'

export function ApiKeyCard({ apiKeyPrefix, isOwner }) {
  const toast = useToast()
  const queryClient = useQueryClient()
  const [isConfirmOpen, setConfirmOpen] = useState(false)
  const [newKey, setNewKey] = useState(null)

  const rotateMutation = useMutation({
    mutationFn: rotateApiKey,
    onSuccess: (result) => {
      setConfirmOpen(false)
      setNewKey(result.apiKey)
      queryClient.invalidateQueries({ queryKey: queryKeys.settings })
      toast.success('API key rotated')
    },
    onError: (error) => toast.error(getErrorMessage(error, 'Could not rotate API key')),
  })

  return (
    <SettingsCard
      title="Agent API key"
      description="Agents send this key in the X-API-Key header. Only the prefix is stored in plain text."
    >
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-2 rounded-lg border border-line bg-black/40 px-3 py-2 font-mono text-sm text-zinc-200">
          <KeyRound className="h-4 w-4 text-subtle" aria-hidden="true" />
          {apiKeyPrefix}
          <span className="text-subtle">••••••••</span>
        </div>
        {isOwner && (
          <Button variant="danger" size="sm" icon={RefreshCw} onClick={() => setConfirmOpen(true)}>
            Rotate key
          </Button>
        )}
      </div>

      <ConfirmDialog
        isOpen={isConfirmOpen}
        title="Rotate API key?"
        message="The current key stops working immediately. Every agent must be restarted with the new key."
        confirmLabel="Rotate key"
        isPending={rotateMutation.isPending}
        onConfirm={() => rotateMutation.mutate()}
        onCancel={() => setConfirmOpen(false)}
      />

      <Modal
        isOpen={Boolean(newKey)}
        onClose={() => setNewKey(null)}
        title="Your new API key"
        size="lg"
        footer={
          <Button variant="primary" onClick={() => setNewKey(null)}>
            I have saved the key
          </Button>
        }
      >
        <div className="space-y-4">
          <div className="rounded-lg border border-amber-500/30 bg-amber-500/10 px-3 py-2 text-xs text-amber-200">
            This key is shown only once. Copy it now.
          </div>
          {newKey && <CodeBlock label="API key" code={newKey} />}
          {newKey && <AgentSnippet apiKey={newKey} />}
        </div>
      </Modal>
    </SettingsCard>
  )
}
