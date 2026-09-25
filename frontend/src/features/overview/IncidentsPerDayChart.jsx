import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { Skeleton } from '../../components/Skeleton'

function formatDay(date) {
  return new Date(`${date}T00:00:00Z`).toLocaleDateString(undefined, { weekday: 'short', timeZone: 'UTC' })
}

export function IncidentsPerDayChart({ data, isLoading, isError }) {
  const total = (data ?? []).reduce((sum, day) => sum + day.count, 0)

  return (
    <section className="card p-4" aria-labelledby="incidents-per-day-title">
      <div className="mb-3 flex items-baseline justify-between">
        <h2 id="incidents-per-day-title" className="text-sm font-semibold text-zinc-100">
          Incidents per day
        </h2>
        {data && <span className="text-xs text-muted">{total} in the last 7 days</span>}
      </div>
      {isLoading && <Skeleton className="h-52 w-full" />}
      {isError && <p className="py-16 text-center text-sm text-muted">Chart unavailable</p>}
      {data && (
        <div className="h-52">
          <ResponsiveContainer width="100%" height="100%">
            <BarChart data={data} margin={{ top: 4, right: 4, bottom: 0, left: -24 }}>
              <CartesianGrid stroke="#27272a" strokeDasharray="3 3" vertical={false} />
              <XAxis
                dataKey="date"
                tickFormatter={formatDay}
                stroke="#71717a"
                tick={{ fontSize: 11 }}
                tickLine={false}
                axisLine={{ stroke: '#27272a' }}
              />
              <YAxis allowDecimals={false} stroke="#71717a" tick={{ fontSize: 11 }} tickLine={false} axisLine={false} />
              <Tooltip
                cursor={{ fill: 'rgba(255,255,255,0.04)' }}
                contentStyle={{ background: '#18181b', border: '1px solid #3f3f46', borderRadius: 8, fontSize: 12 }}
                labelStyle={{ color: '#a1a1aa' }}
                itemStyle={{ color: '#f4f4f5' }}
                labelFormatter={(date) => new Date(`${date}T00:00:00Z`).toLocaleDateString(undefined, { timeZone: 'UTC' })}
                formatter={(value) => [value, 'Incidents']}
              />
              <Bar dataKey="count" fill="#f87171" radius={[4, 4, 0, 0]} maxBarSize={28} isAnimationActive={false} />
            </BarChart>
          </ResponsiveContainer>
        </div>
      )}
    </section>
  )
}
