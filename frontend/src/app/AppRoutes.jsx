import { Navigate, Route, Routes } from 'react-router-dom'
import { LoginPage } from '../features/auth/LoginPage'
import { RegisterPage } from '../features/auth/RegisterPage'
import { IncidentDetailPage } from '../features/incidents/IncidentDetailPage'
import { IncidentsPage } from '../features/incidents/IncidentsPage'
import { OverviewPage } from '../features/overview/OverviewPage'
import { RulesPage } from '../features/rules/RulesPage'
import { ServiceDetailPage } from '../features/services/ServiceDetailPage'
import { ServicesPage } from '../features/services/ServicesPage'
import { SettingsPage } from '../features/settings/SettingsPage'
import { NotFoundPage } from './NotFoundPage'
import { ProtectedRoute, PublicOnlyRoute } from './RouteGuards'
import { AppShell } from './layout/AppShell'

export function AppRoutes() {
  return (
    <Routes>
      <Route element={<PublicOnlyRoute />}>
        <Route path="/login" element={<LoginPage />} />
      </Route>
      <Route path="/register" element={<RegisterPage />} />

      <Route element={<ProtectedRoute />}>
        <Route element={<AppShell />}>
          <Route index element={<OverviewPage />} />
          <Route path="services" element={<ServicesPage />} />
          <Route path="services/:id" element={<ServiceDetailPage />} />
          <Route path="incidents" element={<IncidentsPage />} />
          <Route path="incidents/:id" element={<IncidentDetailPage />} />
          <Route path="rules" element={<RulesPage />} />
          <Route path="settings" element={<SettingsPage />} />
          <Route path="overview" element={<Navigate to="/" replace />} />
          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Route>
    </Routes>
  )
}
