# PulseOps frontend

The dashboard for PulseOps: service health, incidents, alert rules and tenant settings.
It talks to the backend only through the REST contract in [`../docs/API.md`](../docs/API.md).

## Stack

- React 18 + Vite 5 (JavaScript)
- Tailwind CSS 3 (dark theme, colour tokens in `tailwind.config.js`)
- React Router 7 for routing
- TanStack Query 5 for server state, polling and cache invalidation
- axios (one shared instance), Recharts for charts, lucide-react for icons

## Run it

```bash
npm install
npm run dev        # http://localhost:5173, /api is proxied to http://localhost:8080
npm run build      # production bundle in dist/
npm run lint
```

All requests use relative URLs (`/api/v1/...`). Set `VITE_API_BASE_URL` only if the API lives on a
different origin.

### Docker

```bash
docker build -t pulseops-frontend .
```

The image serves `dist/` with nginx on port 80 and proxies `/api/`, `/swagger-ui`, `/v3/api-docs`
and `/actuator/` to the `app` service on port 8080 (see `nginx.conf` for how requests are balanced
across replicas).

## Folder structure

```
src/
├── api/          axios client (auth header, 401 handling) + one module per resource, query keys
├── app/          App (providers), routes, route guards, query client
│   └── layout/   AppShell, Sidebar, TopBar, UserMenu
├── components/   shared UI: DataTable, Pagination, Modal, ConfirmDialog, Toast, MetricChart, badges, ...
├── features/     one folder per area, each owns its pages and page-specific components
├── hooks/        useAuth, useToast, useNow, useDebouncedValue, useCopyToClipboard, useDismiss
└── lib/          formatters, constants, auth storage, error helpers, sorting, rule/metric helpers
```

## Where each page lives

| Route | File |
|---|---|
| `/login` | `src/features/auth/LoginPage.jsx` |
| `/register` | `src/features/auth/RegisterPage.jsx` |
| `/` | `src/features/overview/OverviewPage.jsx` |
| `/services` | `src/features/services/ServicesPage.jsx` |
| `/services/:id` | `src/features/services/ServiceDetailPage.jsx` |
| `/incidents` | `src/features/incidents/IncidentsPage.jsx` (filters in `useIncidentFilters.js`) |
| `/incidents/:id` | `src/features/incidents/IncidentDetailPage.jsx` |
| `/rules` | `src/features/rules/RulesPage.jsx` |
| `/settings` | `src/features/settings/SettingsPage.jsx` |
| Notification bell | `src/features/notifications/NotificationBell.jsx` |

## Key behaviours

- **Auth**: `{ token, user, tenant }` is stored in `localStorage` under `pulseops.auth`. The axios
  interceptor adds `Authorization: Bearer`; any 401 (except on login) signs the user out and the
  route guard sends them to `/login`.
- **Polling**: overview every 10 s, incidents and notifications every 15 s, incident detail every 5 s
  while the AI analysis or post-mortem is pending. TanStack Query pauses polling in hidden tabs.
- **Optimistic locking**: acknowledge/resolve send the incident `version`; a 409 shows a toast and
  reloads the incident.
- **Errors**: messages come from the RFC 7807 `detail` field; validation `errors` are shown per field.
