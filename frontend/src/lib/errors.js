export function getProblem(error) {
  const data = error?.response?.data
  return data && typeof data === 'object' ? data : null
}

export function getErrorMessage(error, fallback = 'Something went wrong') {
  const problem = getProblem(error)
  if (problem?.detail) return problem.detail
  if (problem?.title) return problem.title
  if (error?.response?.status === 429) return 'Too many requests. Please wait a moment.'
  if (error && !error.response && error.message) return 'Cannot reach the server. Check your connection.'
  return fallback
}

export function getFieldErrors(error) {
  return getProblem(error)?.errors ?? {}
}

export function getStatus(error) {
  return error?.response?.status ?? null
}
