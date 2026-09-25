const STORAGE_KEY = 'pulseops.auth'

export function loadAuth() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return null
    const auth = JSON.parse(raw)
    if (!auth?.token || isTokenExpired(auth.token)) {
      localStorage.removeItem(STORAGE_KEY)
      return null
    }
    return auth
  } catch {
    return null
  }
}

export function saveAuth({ token, user, tenant }) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify({ token, user, tenant }))
}

export function clearAuth() {
  localStorage.removeItem(STORAGE_KEY)
}

export function getToken() {
  return loadAuth()?.token ?? null
}

function isTokenExpired(token) {
  try {
    const payload = JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')))
    return typeof payload.exp === 'number' && payload.exp * 1000 < Date.now()
  } catch {
    return false
  }
}
