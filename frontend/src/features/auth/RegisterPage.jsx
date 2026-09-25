import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useMutation } from '@tanstack/react-query'
import { ArrowRight, KeyRound } from 'lucide-react'
import { register } from '../../api/auth'
import { AgentSnippet } from '../../components/AgentSnippet'
import { Button } from '../../components/Button'
import { CodeBlock } from '../../components/CodeBlock'
import { FormField } from '../../components/FormField'
import { useAuth } from '../../hooks/useAuth'
import { getErrorMessage, getFieldErrors } from '../../lib/errors'
import { AuthLayout, ErrorBanner } from './AuthLayout'

const EMPTY_FORM = { companyName: '', fullName: '', email: '', password: '' }

function validate(form) {
  const errors = {}
  if (!form.companyName.trim()) errors.companyName = 'Company name is required'
  if (!form.fullName.trim()) errors.fullName = 'Your name is required'
  if (!/^\S+@\S+\.\S+$/.test(form.email.trim())) errors.email = 'Enter a valid email address'
  if (form.password.length < 8) errors.password = 'Password must be at least 8 characters'
  return errors
}

export function RegisterPage() {
  const [form, setForm] = useState(EMPTY_FORM)
  const [clientErrors, setClientErrors] = useState({})
  const [registration, setRegistration] = useState(null)

  const registerMutation = useMutation({
    mutationFn: register,
    onSuccess: setRegistration,
  })

  if (registration) return <RegistrationSuccess registration={registration} />

  function update(field) {
    return (event) => setForm((current) => ({ ...current, [field]: event.target.value }))
  }

  function handleSubmit(event) {
    event.preventDefault()
    const errors = validate(form)
    setClientErrors(errors)
    if (Object.keys(errors).length > 0) return
    registerMutation.mutate({
      companyName: form.companyName.trim(),
      fullName: form.fullName.trim(),
      email: form.email.trim(),
      password: form.password,
    })
  }

  const serverErrors = getFieldErrors(registerMutation.error)
  const fieldError = (field) => clientErrors[field] ?? serverErrors[field]
  const bannerMessage = registerMutation.isError ? getErrorMessage(registerMutation.error, 'Registration failed') : null

  return (
    <AuthLayout
      title="Create your organisation"
      subtitle="You will get an API key for the agent and a set of default alert rules."
      footer={
        <>
          Already have an account?{' '}
          <Link to="/login" className="font-medium text-emerald-400 hover:text-emerald-300">
            Sign in
          </Link>
        </>
      }
    >
      <ErrorBanner message={bannerMessage} />
      <form onSubmit={handleSubmit} className="space-y-4" noValidate>
        <FormField label="Company name" error={fieldError('companyName')}>
          <input className="input" autoComplete="organization" value={form.companyName} onChange={update('companyName')} />
        </FormField>
        <FormField label="Your name" error={fieldError('fullName')}>
          <input className="input" autoComplete="name" value={form.fullName} onChange={update('fullName')} />
        </FormField>
        <FormField label="Work email" error={fieldError('email')}>
          <input type="email" className="input" autoComplete="email" value={form.email} onChange={update('email')} />
        </FormField>
        <FormField label="Password" hint="At least 8 characters" error={fieldError('password')}>
          <input
            type="password"
            className="input"
            autoComplete="new-password"
            value={form.password}
            onChange={update('password')}
          />
        </FormField>
        <Button type="submit" variant="primary" className="w-full" isLoading={registerMutation.isPending}>
          Create organisation
        </Button>
      </form>
    </AuthLayout>
  )
}

function RegistrationSuccess({ registration }) {
  const { signIn } = useAuth()
  const navigate = useNavigate()

  function handleContinue() {
    signIn(registration)
    navigate('/', { replace: true })
  }

  return (
    <AuthLayout
      wide
      title={`Welcome to PulseOps, ${registration.user.fullName.split(' ')[0]}`}
      subtitle={`${registration.tenant.name} is ready. Connect your first service.`}
    >
      <div className="space-y-6">
        <section className="space-y-2">
          <h2 className="flex items-center gap-2 text-sm font-semibold text-zinc-100">
            <KeyRound className="h-4 w-4 text-emerald-400" aria-hidden="true" />
            Your API key
          </h2>
          <div className="rounded-lg border border-amber-500/30 bg-amber-500/10 px-3 py-2 text-xs text-amber-200">
            This key is shown only once. Copy it now and store it somewhere safe. You can rotate it later in Settings.
          </div>
          <CodeBlock label="API key" code={registration.apiKey} />
        </section>

        <section className="space-y-2">
          <h2 className="text-sm font-semibold text-zinc-100">Install the agent</h2>
          <AgentSnippet apiKey={registration.apiKey} />
        </section>

        <Button variant="primary" className="w-full" onClick={handleContinue}>
          Continue to dashboard
          <ArrowRight className="h-4 w-4" aria-hidden="true" />
        </Button>
      </div>
    </AuthLayout>
  )
}
