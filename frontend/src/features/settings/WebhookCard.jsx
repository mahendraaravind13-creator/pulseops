import { useEffect, useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { updateWebhook } from '../../api/settings'
import { queryKeys } from '../../api/queryKeys'
import { Button } from '../../components/Button'
import { FormField } from '../../components/FormField'
import { useToast } from '../../hooks/useToast'
import { getErrorMessage, getFieldErrors } from '../../lib/errors'
import { SettingsCard } from './SettingsCard'

function validateUrl(value) {
  if (!value) return null
  try {
    const url = new URL(value)
    return url.protocol === 'http:' || url.protocol === 'https:' ? null : 'Use an http or https URL'
  } catch {
    return 'Enter a valid URL'
  }
}

export function WebhookCard({ webhookUrl, isOwner }) {
  const toast = useToast()
  const queryClient = useQueryClient()
  const [value, setValue] = useState(webhookUrl ?? '')
  const [clientError, setClientError] = useState(null)

  useEffect(() => {
    setValue(webhookUrl ?? '')
  }, [webhookUrl])

  const saveMutation = useMutation({
    mutationFn: updateWebhook,
    onSuccess: (settings) => {
      queryClient.setQueryData(queryKeys.settings, settings)
      toast.success(settings.webhookUrl ? 'Webhook saved' : 'Webhook removed')
    },
    onError: (error) => toast.error(getErrorMessage(error, 'Could not save webhook')),
  })

  function save(nextValue) {
    const trimmed = nextValue.trim()
    const error = validateUrl(trimmed)
    setClientError(error)
    if (error) return
    saveMutation.mutate(trimmed || null)
  }

  function handleSubmit(event) {
    event.preventDefault()
    save(value)
  }

  const error = clientError ?? getFieldErrors(saveMutation.error).webhookUrl
  const isUnchanged = value.trim() === (webhookUrl ?? '')

  return (
    <SettingsCard
      title="Webhook"
      description="PulseOps POSTs a JSON payload here when an incident opens or resolves, with retries."
    >
      <form onSubmit={handleSubmit} className="space-y-3" noValidate>
        <FormField label="Webhook URL" error={error} hint={isOwner ? 'Leave empty to disable.' : undefined}>
          <input
            type="url"
            className="input"
            placeholder="https://hooks.example.com/pulseops"
            value={value}
            disabled={!isOwner}
            onChange={(event) => setValue(event.target.value)}
          />
        </FormField>
        {isOwner && (
          <div className="flex flex-wrap justify-end gap-2">
            {webhookUrl && (
              <Button
                variant="ghost"
                size="sm"
                disabled={saveMutation.isPending}
                onClick={() => {
                  setValue('')
                  save('')
                }}
              >
                Remove
              </Button>
            )}
            <Button type="submit" variant="primary" size="sm" isLoading={saveMutation.isPending} disabled={isUnchanged}>
              Save webhook
            </Button>
          </div>
        )}
      </form>
    </SettingsCard>
  )
}
