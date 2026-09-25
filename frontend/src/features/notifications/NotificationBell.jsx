import { useCallback, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Bell, CheckCheck, CircleCheck, Siren } from 'lucide-react'
import { fetchNotifications, markAllNotificationsRead, markNotificationRead } from '../../api/notifications'
import { queryKeys } from '../../api/queryKeys'
import { Spinner } from '../../components/Spinner'
import { useDismiss } from '../../hooks/useDismiss'
import { useNow } from '../../hooks/useNow'
import { useToast } from '../../hooks/useToast'
import { getErrorMessage } from '../../lib/errors'
import { formatRelativeTime } from '../../lib/format'

const POLL_MS = 15_000

export function NotificationBell() {
  const [isOpen, setOpen] = useState(false)
  const containerRef = useRef(null)
  const close = useCallback(() => setOpen(false), [])
  useDismiss(containerRef, isOpen, close)

  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const toast = useToast()

  const notifications = useQuery({
    queryKey: queryKeys.notifications,
    queryFn: () => fetchNotifications(10),
    refetchInterval: POLL_MS,
  })

  const invalidate = () => queryClient.invalidateQueries({ queryKey: queryKeys.notifications })

  const markRead = useMutation({
    mutationFn: markNotificationRead,
    onSuccess: invalidate,
    onError: (error) => toast.error(getErrorMessage(error, 'Could not mark notification as read')),
  })

  const markAllRead = useMutation({
    mutationFn: markAllNotificationsRead,
    onSuccess: () => {
      invalidate()
      toast.success('All notifications marked as read')
    },
    onError: (error) => toast.error(getErrorMessage(error, 'Could not mark notifications as read')),
  })

  function openNotification(item) {
    if (!item.read) markRead.mutate(item.id)
    setOpen(false)
    if (item.incidentId) navigate(`/incidents/${item.incidentId}`)
  }

  const unreadCount = notifications.data?.unreadCount ?? 0

  return (
    <div className="relative" ref={containerRef}>
      <button
        type="button"
        onClick={() => setOpen((open) => !open)}
        aria-haspopup="true"
        aria-expanded={isOpen}
        aria-label={unreadCount > 0 ? `Notifications, ${unreadCount} unread` : 'Notifications'}
        className="relative rounded-lg p-2 text-muted hover:bg-zinc-800 hover:text-zinc-100"
      >
        <Bell className="h-5 w-5" aria-hidden="true" />
        {unreadCount > 0 && (
          <span className="absolute -right-0.5 -top-0.5 flex h-4 min-w-4 items-center justify-center rounded-full bg-red-500 px-1 text-[10px] font-bold text-white">
            {unreadCount > 99 ? '99+' : unreadCount}
          </span>
        )}
      </button>

      {isOpen && (
        <div className="fixed inset-x-2 top-14 z-30 rounded-xl border border-line bg-panel-raised shadow-2xl sm:absolute sm:inset-x-auto sm:right-0 sm:top-auto sm:mt-2 sm:w-96">
          <div className="flex items-center justify-between border-b border-line px-4 py-2.5">
            <p className="text-sm font-semibold text-zinc-100">Notifications</p>
            <button
              type="button"
              onClick={() => markAllRead.mutate()}
              disabled={unreadCount === 0 || markAllRead.isPending}
              className="inline-flex items-center gap-1 text-xs font-medium text-emerald-400 hover:text-emerald-300 disabled:cursor-not-allowed disabled:text-subtle"
            >
              <CheckCheck className="h-3.5 w-3.5" aria-hidden="true" />
              Mark all read
            </button>
          </div>
          <NotificationList
            query={notifications}
            onOpen={openNotification}
            onMarkRead={(id) => markRead.mutate(id)}
            isMarking={markRead.isPending}
          />
        </div>
      )}
    </div>
  )
}

function NotificationList({ query, onOpen, onMarkRead, isMarking }) {
  const now = useNow(10_000)

  if (query.isPending) {
    return (
      <div className="flex justify-center py-8 text-muted">
        <Spinner />
      </div>
    )
  }
  if (query.isError) {
    return (
      <div className="px-4 py-6 text-center text-sm text-muted">
        <p>{getErrorMessage(query.error, 'Could not load notifications')}</p>
        <button
          type="button"
          onClick={() => query.refetch()}
          className="mt-2 text-xs font-medium text-emerald-400 hover:text-emerald-300"
        >
          Retry
        </button>
      </div>
    )
  }
  const items = query.data.items ?? []
  if (items.length === 0) {
    return <p className="px-4 py-8 text-center text-sm text-muted">You are all caught up.</p>
  }

  return (
    <ul className="max-h-[60vh] divide-y divide-line overflow-y-auto">
      {items.map((item) => {
        const Icon = item.type === 'INCIDENT_RESOLVED' ? CircleCheck : Siren
        const iconColor = item.type === 'INCIDENT_RESOLVED' ? 'text-emerald-400' : 'text-red-400'
        return (
          <li key={item.id} className={`flex gap-3 px-4 py-3 ${item.read ? '' : 'bg-zinc-800/40'}`}>
            <Icon className={`mt-0.5 h-4 w-4 shrink-0 ${iconColor}`} aria-hidden="true" />
            <button type="button" onClick={() => onOpen(item)} className="min-w-0 flex-1 text-left">
              <p className={`text-sm ${item.read ? 'text-zinc-300' : 'font-medium text-zinc-50'}`}>{item.title}</p>
              {item.body && <p className="mt-0.5 line-clamp-2 text-xs text-muted">{item.body}</p>}
              <p className="mt-1 text-[11px] text-subtle">{formatRelativeTime(item.createdAt, now)}</p>
            </button>
            {!item.read && (
              <button
                type="button"
                onClick={() => onMarkRead(item.id)}
                disabled={isMarking}
                className="self-start rounded p-1 text-subtle hover:bg-zinc-700 hover:text-zinc-100 disabled:opacity-50"
                aria-label={`Mark "${item.title}" as read`}
                title="Mark as read"
              >
                <span className="block h-2 w-2 rounded-full bg-emerald-400" aria-hidden="true" />
              </button>
            )}
          </li>
        )
      })}
    </ul>
  )
}
