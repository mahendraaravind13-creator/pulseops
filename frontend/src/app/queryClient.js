import { QueryClient } from '@tanstack/react-query'
import { getStatus } from '../lib/errors'

function shouldRetry(failureCount, error) {
  const status = getStatus(error)
  if (status && status >= 400 && status < 500) return false
  return failureCount < 2
}

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: shouldRetry,
      staleTime: 5_000,
      refetchOnWindowFocus: true,
    },
    mutations: {
      retry: false,
    },
  },
})
