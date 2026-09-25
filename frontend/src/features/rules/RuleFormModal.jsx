import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { createRule, updateRule } from '../../api/rules'
import { fetchServices } from '../../api/services'
import { queryKeys } from '../../api/queryKeys'
import { Button } from '../../components/Button'
import { FormField } from '../../components/FormField'
import { Modal } from '../../components/Modal'
import { useToast } from '../../hooks/useToast'
import { METRIC_OPTIONS, METRICS, OPERATORS, SEVERITIES } from '../../lib/constants'
import { getErrorMessage, getFieldErrors } from '../../lib/errors'
import { toLabel } from '../../lib/format'
import { describeRule } from '../../lib/rules'

const EMPTY_FORM = {
  name: '',
  serviceId: '',
  metric: 'CPU',
  operator: 'GT',
  threshold: '85',
  durationSeconds: '60',
  severity: 'WARNING',
  enabled: true,
}

function toForm(rule) {
  if (!rule) return EMPTY_FORM
  return {
    name: rule.name,
    serviceId: rule.serviceId === null ? '' : String(rule.serviceId),
    metric: rule.metric,
    operator: rule.operator,
    threshold: String(rule.threshold),
    durationSeconds: String(rule.durationSeconds),
    severity: rule.severity,
    enabled: rule.enabled,
  }
}

function toPayload(form) {
  return {
    name: form.name.trim(),
    serviceId: form.serviceId === '' ? null : Number(form.serviceId),
    metric: form.metric,
    operator: form.operator,
    threshold: Number(form.threshold),
    durationSeconds: Number(form.durationSeconds),
    severity: form.severity,
    enabled: form.enabled,
  }
}

function validate(form) {
  const errors = {}
  if (!form.name.trim()) errors.name = 'Name is required'
  else if (form.name.trim().length > 100) errors.name = 'Keep the name under 100 characters'

  const threshold = Number(form.threshold)
  if (form.threshold === '' || Number.isNaN(threshold)) errors.threshold = 'Enter a number'
  else if (threshold < 0) errors.threshold = 'Must be 0 or more'
  else if (METRICS[form.metric].unit === '%' && threshold > 100) errors.threshold = 'Percentages go up to 100'

  const duration = Number(form.durationSeconds)
  if (form.durationSeconds === '' || !Number.isInteger(duration)) errors.durationSeconds = 'Enter whole seconds'
  else if (duration < 0 || duration > 3600) errors.durationSeconds = 'Must be between 0 and 3600'
  return errors
}

export function RuleFormModal({ isOpen, rule, onClose }) {
  const isEditing = Boolean(rule)
  const toast = useToast()
  const queryClient = useQueryClient()
  const [form, setForm] = useState(EMPTY_FORM)
  const [clientErrors, setClientErrors] = useState({})

  const services = useQuery({ queryKey: queryKeys.services, queryFn: fetchServices, enabled: isOpen })

  const saveMutation = useMutation({
    mutationFn: (payload) => (isEditing ? updateRule({ id: rule.id, ...payload }) : createRule(payload)),
    onSuccess: (saved) => {
      toast.success(isEditing ? `Rule "${saved.name}" updated` : `Rule "${saved.name}" created`)
      queryClient.invalidateQueries({ queryKey: queryKeys.rules })
      onClose()
    },
    onError: (error) => toast.error(getErrorMessage(error, 'Could not save rule')),
  })
  const { reset: resetMutation } = saveMutation

  useEffect(() => {
    if (!isOpen) return
    setForm(toForm(rule))
    setClientErrors({})
    resetMutation()
  }, [isOpen, rule, resetMutation])

  function update(field) {
    return (event) => {
      const value = event.target.type === 'checkbox' ? event.target.checked : event.target.value
      setForm((current) => ({ ...current, [field]: value }))
    }
  }

  function handleSubmit(event) {
    event.preventDefault()
    const errors = validate(form)
    setClientErrors(errors)
    if (Object.keys(errors).length > 0) return
    saveMutation.mutate(toPayload(form))
  }

  const serverErrors = getFieldErrors(saveMutation.error)
  const fieldError = (field) => clientErrors[field] ?? serverErrors[field]
  const selectedService = (services.data ?? []).find((service) => String(service.id) === form.serviceId)
  const preview = describeRule({
    ...form,
    serviceName: form.serviceId ? (selectedService?.name ?? rule?.serviceName ?? 'the selected service') : null,
  })

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title={isEditing ? 'Edit alert rule' : 'New alert rule'}
      size="lg"
      footer={
        <>
          <Button variant="ghost" onClick={onClose} disabled={saveMutation.isPending}>
            Cancel
          </Button>
          <Button variant="primary" type="submit" form="rule-form" isLoading={saveMutation.isPending}>
            {isEditing ? 'Save changes' : 'Create rule'}
          </Button>
        </>
      }
    >
      <form id="rule-form" onSubmit={handleSubmit} noValidate className="space-y-4">
        <FormField label="Name" error={fieldError('name')}>
          <input className="input" value={form.name} onChange={update('name')} placeholder="High CPU" maxLength={100} />
        </FormField>

        <FormField label="Service" error={fieldError('serviceId')}>
          <select className="input" value={form.serviceId} onChange={update('serviceId')}>
            <option value="">All services</option>
            {(services.data ?? []).map((service) => (
              <option key={service.id} value={String(service.id)}>
                {service.name}
              </option>
            ))}
            {rule?.serviceId && !selectedService && services.isSuccess && (
              <option value={String(rule.serviceId)}>{rule.serviceName}</option>
            )}
          </select>
        </FormField>

        <div className="grid gap-4 sm:grid-cols-3">
          <FormField label="Metric" error={fieldError('metric')}>
            <select className="input" value={form.metric} onChange={update('metric')}>
              {METRIC_OPTIONS.map((metric) => (
                <option key={metric} value={metric}>
                  {METRICS[metric].label} ({METRICS[metric].unit})
                </option>
              ))}
            </select>
          </FormField>
          <FormField label="Operator" error={fieldError('operator')}>
            <select className="input" value={form.operator} onChange={update('operator')}>
              {Object.entries(OPERATORS).map(([value, operator]) => (
                <option key={value} value={value}>
                  {operator.symbol} {operator.label}
                </option>
              ))}
            </select>
          </FormField>
          <FormField label={`Threshold (${METRICS[form.metric].unit})`} error={fieldError('threshold')}>
            <input
              type="number"
              inputMode="decimal"
              step="any"
              min="0"
              className="input"
              value={form.threshold}
              onChange={update('threshold')}
            />
          </FormField>
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          <FormField
            label="Duration (seconds)"
            hint="How long the condition must hold. 0 fires on a single sample."
            error={fieldError('durationSeconds')}
          >
            <input
              type="number"
              inputMode="numeric"
              step="1"
              min="0"
              max="3600"
              className="input"
              value={form.durationSeconds}
              onChange={update('durationSeconds')}
            />
          </FormField>
          <FormField label="Severity" error={fieldError('severity')}>
            <select className="input" value={form.severity} onChange={update('severity')}>
              {SEVERITIES.map((severity) => (
                <option key={severity} value={severity}>
                  {toLabel(severity)}
                </option>
              ))}
            </select>
          </FormField>
        </div>

        <label className="flex items-center gap-2 text-sm text-zinc-300">
          <input
            type="checkbox"
            checked={form.enabled}
            onChange={update('enabled')}
            className="h-4 w-4 rounded border-line bg-panel-raised accent-emerald-500"
          />
          Enabled
        </label>

        <div className="rounded-lg border border-line bg-black/40 px-3 py-2.5">
          <p className="text-xs font-medium uppercase tracking-wide text-subtle">Preview</p>
          <p className="mt-1 text-sm text-zinc-100" aria-live="polite">
            {preview}
          </p>
        </div>
      </form>
    </Modal>
  )
}
