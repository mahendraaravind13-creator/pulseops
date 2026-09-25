import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { BellRing, Pencil, Plus, Trash2 } from 'lucide-react'
import { deleteRule, fetchRules, setRuleEnabled } from '../../api/rules'
import { queryKeys } from '../../api/queryKeys'
import { Button } from '../../components/Button'
import { ConfirmDialog } from '../../components/ConfirmDialog'
import { DataTable } from '../../components/DataTable'
import { EmptyState } from '../../components/EmptyState'
import { ErrorState } from '../../components/ErrorState'
import { PageHeader } from '../../components/PageHeader'
import { SeverityBadge } from '../../components/SeverityBadge'
import { useAuth } from '../../hooks/useAuth'
import { useToast } from '../../hooks/useToast'
import { METRICS, OPERATORS } from '../../lib/constants'
import { getErrorMessage } from '../../lib/errors'
import { formatThreshold } from '../../lib/rules'
import { RuleFormModal } from './RuleFormModal'
import { ToggleSwitch } from './ToggleSwitch'

export function RulesPage() {
  const { isOwner } = useAuth()
  const toast = useToast()
  const queryClient = useQueryClient()
  const [editingRule, setEditingRule] = useState(null)
  const [isFormOpen, setFormOpen] = useState(false)
  const [ruleToDelete, setRuleToDelete] = useState(null)

  const rules = useQuery({ queryKey: queryKeys.rules, queryFn: fetchRules })

  const toggleMutation = useMutation({
    mutationFn: setRuleEnabled,
    onMutate: async ({ id, enabled }) => {
      await queryClient.cancelQueries({ queryKey: queryKeys.rules })
      const previous = queryClient.getQueryData(queryKeys.rules)
      queryClient.setQueryData(queryKeys.rules, (current = []) =>
        current.map((rule) => (rule.id === id ? { ...rule, enabled } : rule)),
      )
      return { previous }
    },
    onError: (error, _variables, context) => {
      queryClient.setQueryData(queryKeys.rules, context?.previous)
      toast.error(getErrorMessage(error, 'Could not update rule'))
    },
    onSuccess: (rule) => toast.success(`${rule.name} ${rule.enabled ? 'enabled' : 'disabled'}`),
    onSettled: () => queryClient.invalidateQueries({ queryKey: queryKeys.rules }),
  })

  const deleteMutation = useMutation({
    mutationFn: deleteRule,
    onSuccess: () => {
      toast.success('Rule deleted')
      setRuleToDelete(null)
      queryClient.invalidateQueries({ queryKey: queryKeys.rules })
      queryClient.invalidateQueries({ queryKey: ['incidents'] })
      queryClient.invalidateQueries({ queryKey: queryKeys.overview })
    },
    onError: (error) => toast.error(getErrorMessage(error, 'Could not delete rule')),
  })

  function openCreate() {
    setEditingRule(null)
    setFormOpen(true)
  }

  function openEdit(rule) {
    setEditingRule(rule)
    setFormOpen(true)
  }

  const columns = [
    {
      key: 'enabled',
      header: 'Enabled',
      render: (rule) => (
        <ToggleSwitch
          checked={rule.enabled}
          label={`${rule.enabled ? 'Disable' : 'Enable'} ${rule.name}`}
          disabled={toggleMutation.isPending && toggleMutation.variables?.id === rule.id}
          onChange={(enabled) => toggleMutation.mutate({ id: rule.id, enabled })}
        />
      ),
    },
    {
      key: 'name',
      header: 'Rule',
      render: (rule) => (
        <div>
          <p className="font-medium text-zinc-100">{rule.name}</p>
          <p className="text-xs text-subtle">{rule.serviceName ?? 'All services'}</p>
        </div>
      ),
    },
    {
      key: 'condition',
      header: 'Condition',
      render: (rule) => (
        <span className="font-mono text-xs text-zinc-300">
          {METRICS[rule.metric]?.label} {OPERATORS[rule.operator]?.symbol} {formatThreshold(rule.metric, rule.threshold)}
          {rule.durationSeconds > 0 ? ` for ${rule.durationSeconds}s` : ''}
        </span>
      ),
    },
    { key: 'severity', header: 'Severity', render: (rule) => <SeverityBadge severity={rule.severity} /> },
    {
      key: 'activeIncidents',
      header: 'Active',
      align: 'right',
      render: (rule) => (
        <span className={rule.activeIncidents > 0 ? 'font-semibold text-red-300' : 'text-subtle'}>{rule.activeIncidents}</span>
      ),
    },
    {
      key: 'actions',
      header: <span className="sr-only">Actions</span>,
      align: 'right',
      render: (rule) => (
        <div className="flex justify-end gap-1">
          <button
            type="button"
            onClick={() => openEdit(rule)}
            className="rounded-md p-1.5 text-muted hover:bg-zinc-800 hover:text-zinc-100"
            aria-label={`Edit ${rule.name}`}
          >
            <Pencil className="h-4 w-4" aria-hidden="true" />
          </button>
          {isOwner && (
            <button
              type="button"
              onClick={() => setRuleToDelete(rule)}
              className="rounded-md p-1.5 text-muted hover:bg-red-500/10 hover:text-red-300"
              aria-label={`Delete ${rule.name}`}
            >
              <Trash2 className="h-4 w-4" aria-hidden="true" />
            </button>
          )}
        </div>
      ),
    },
  ]

  return (
    <>
      <PageHeader
        title="Alert rules"
        description="Rules are evaluated on every sample. A breach that lasts the full duration opens an incident."
        actions={
          <Button variant="primary" icon={Plus} onClick={openCreate}>
            New rule
          </Button>
        }
      />
      <div className="card">
        {rules.isError ? (
          <ErrorState error={rules.error} onRetry={rules.refetch} title="Could not load rules" />
        ) : rules.data?.length === 0 ? (
          <EmptyState
            icon={BellRing}
            title="No alert rules"
            description="Create a rule to start opening incidents automatically."
            action={
              <Button variant="primary" size="sm" icon={Plus} onClick={openCreate}>
                New rule
              </Button>
            }
          />
        ) : (
          <DataTable caption="Alert rules" columns={columns} rows={rules.data ?? []} isLoading={rules.isPending} />
        )}
      </div>

      <RuleFormModal isOpen={isFormOpen} rule={editingRule} onClose={() => setFormOpen(false)} />

      <ConfirmDialog
        isOpen={Boolean(ruleToDelete)}
        title="Delete rule?"
        message={
          ruleToDelete
            ? `"${ruleToDelete.name}" will be deleted.${ruleToDelete.activeIncidents > 0 ? ` Its ${ruleToDelete.activeIncidents} active incident(s) will be resolved first.` : ''} This cannot be undone.`
            : ''
        }
        confirmLabel="Delete rule"
        isPending={deleteMutation.isPending}
        onConfirm={() => deleteMutation.mutate(ruleToDelete.id)}
        onCancel={() => setRuleToDelete(null)}
      />
    </>
  )
}
