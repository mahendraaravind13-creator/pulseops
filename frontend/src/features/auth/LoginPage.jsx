import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { useMutation } from '@tanstack/react-query'
import { login } from '../../api/auth'
import { Button } from '../../components/Button'
import { FormField } from '../../components/FormField'
import { useAuth } from '../../hooks/useAuth'
import { getErrorMessage, getStatus } from '../../lib/errors'
import { AuthLayout, ErrorBanner } from './AuthLayout'

export function LoginPage() {
  const { signIn } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [form, setForm] = useState({ email: '', password: '' })

  const loginMutation = useMutation({
    mutationFn: login,
    onSuccess: (response) => {
      signIn(response)
      navigate(location.state?.from ?? '/', { replace: true })
    },
  })

  function update(field) {
    return (event) => setForm((current) => ({ ...current, [field]: event.target.value }))
  }

  function handleSubmit(event) {
    event.preventDefault()
    loginMutation.mutate({ email: form.email.trim(), password: form.password })
  }

  const errorMessage = loginMutation.isError
    ? getStatus(loginMutation.error) === 401
      ? 'Incorrect email or password.'
      : getErrorMessage(loginMutation.error, 'Sign in failed')
    : null

  return (
    <AuthLayout
      title="Sign in"
      subtitle="Monitor your services and manage incidents."
      footer={
        <>
          New to PulseOps?{' '}
          <Link to="/register" className="font-medium text-emerald-400 hover:text-emerald-300">
            Create an organisation
          </Link>
        </>
      }
    >
      <ErrorBanner message={errorMessage} />
      <form onSubmit={handleSubmit} className="space-y-4" noValidate>
        <FormField label="Email">
          <input
            type="email"
            className="input"
            autoComplete="email"
            required
            value={form.email}
            onChange={update('email')}
          />
        </FormField>
        <FormField label="Password">
          <input
            type="password"
            className="input"
            autoComplete="current-password"
            required
            value={form.password}
            onChange={update('password')}
          />
        </FormField>
        <Button
          type="submit"
          variant="primary"
          className="w-full"
          isLoading={loginMutation.isPending}
          disabled={!form.email || !form.password}
        >
          Sign in
        </Button>
      </form>
    </AuthLayout>
  )
}
