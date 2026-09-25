import { useCallback, useEffect, useMemo, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { setUnauthorizedHandler } from '../../api/client'
import { clearAuth, loadAuth, saveAuth } from '../../lib/auth-storage'
import { AuthContext } from './authContext'

export function AuthProvider({ children }) {
  const queryClient = useQueryClient()
  const [auth, setAuth] = useState(() => loadAuth())

  const signIn = useCallback((response) => {
    const next = { token: response.token, user: response.user, tenant: response.tenant }
    saveAuth(next)
    setAuth(next)
  }, [])

  const signOut = useCallback(() => {
    clearAuth()
    setAuth(null)
    queryClient.clear()
  }, [queryClient])

  useEffect(() => {
    setUnauthorizedHandler(signOut)
    return () => setUnauthorizedHandler(() => {})
  }, [signOut])

  const value = useMemo(
    () => ({
      token: auth?.token ?? null,
      user: auth?.user ?? null,
      tenant: auth?.tenant ?? null,
      isAuthenticated: Boolean(auth?.token),
      isOwner: auth?.user?.role === 'OWNER',
      signIn,
      signOut,
    }),
    [auth, signIn, signOut],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
