import { QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter } from 'react-router-dom'
import { ToastProvider } from '../components/Toast'
import { AuthProvider } from '../features/auth/AuthProvider'
import { queryClient } from './queryClient'
import { AppRoutes } from './AppRoutes'

export function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <ToastProvider>
          <BrowserRouter>
            <AppRoutes />
          </BrowserRouter>
        </ToastProvider>
      </AuthProvider>
    </QueryClientProvider>
  )
}
