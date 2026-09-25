import { useMutation, useQueryClient } from '@tanstack/react-query'
import {
  acknowledgeIncident,
  addIncidentNote,
  resolveIncident,
  retryAnalysis,
  retryPostmortem,
} from '../../api/incidents'
import { queryKeys } from '../../api/queryKeys'
import { useToast } from '../../hooks/useToast'
import { getErrorMessage, getStatus } from '../../lib/errors'

export function useIncidentActions(incidentId) {
  const queryClient = useQueryClient()
  const toast = useToast()
  const detailKey = queryKeys.incident(incidentId)

  function applyUpdatedIncident(incident) {
    queryClient.setQueryData(detailKey, incident)
    queryClient.invalidateQueries({ queryKey: queryKeys.incidentLists })
    queryClient.invalidateQueries({ queryKey: queryKeys.overview })
    queryClient.invalidateQueries({ queryKey: queryKeys.services })
  }

  function handleError(fallback) {
    return (error) => {
      if (getStatus(error) === 409) {
        toast.error('This incident changed, reloaded latest')
        queryClient.invalidateQueries({ queryKey: detailKey })
        return
      }
      toast.error(getErrorMessage(error, fallback))
    }
  }

  function makeMutation(mutationFn, successMessage, errorMessage) {
    return {
      mutationFn,
      onSuccess: (incident) => {
        applyUpdatedIncident(incident)
        toast.success(successMessage)
      },
      onError: handleError(errorMessage),
    }
  }

  const acknowledge = useMutation(
    makeMutation((version) => acknowledgeIncident({ id: incidentId, version }), 'Incident acknowledged', 'Could not acknowledge incident'),
  )
  const resolve = useMutation(
    makeMutation(({ version, note }) => resolveIncident({ id: incidentId, version, note }), 'Incident resolved', 'Could not resolve incident'),
  )
  const addNote = useMutation(
    makeMutation((message) => addIncidentNote({ id: incidentId, message }), 'Note added', 'Could not add note'),
  )
  const retryAnalysisMutation = useMutation(
    makeMutation(() => retryAnalysis(incidentId), 'Analysis restarted', 'Could not retry analysis'),
  )
  const retryPostmortemMutation = useMutation(
    makeMutation(() => retryPostmortem(incidentId), 'Post-mortem regeneration started', 'Could not retry post-mortem'),
  )

  return {
    acknowledge,
    resolve,
    addNote,
    retryAnalysis: retryAnalysisMutation,
    retryPostmortem: retryPostmortemMutation,
  }
}
