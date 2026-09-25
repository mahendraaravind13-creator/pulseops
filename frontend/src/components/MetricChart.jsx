import {
  CartesianGrid,
  Line,
  LineChart,
  ReferenceArea,
  ReferenceLine,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'

const AXIS_COLOR = '#71717a'
const GRID_COLOR = '#27272a'
const LINE_COLOR = '#38bdf8'

// data: [{ time: epochMs, value: number | null }]
// thresholds: [{ id, value, label, color }]
// highlight: { from: epochMs, to: epochMs } shades a time window (e.g. the incident)
// highlights: [{ id, from, to, color }] shades several windows (e.g. every incident on a service)
export function MetricChart({ data, unit, thresholds = [], highlight, highlights = [], height = 220, rangeSeconds = 3600 }) {
  const isPercent = unit === '%'
  const maxValue = Math.max(0, ...data.map((point) => point.value ?? 0), ...thresholds.map((t) => t.value))
  const yMax = isPercent ? 100 : Math.ceil(maxValue * 1.15) || 1
  const showDates = rangeSeconds > 24 * 3600

  return (
    <div style={{ height }} className="w-full">
      <ResponsiveContainer width="100%" height="100%">
        <LineChart data={data} margin={{ top: 12, right: 12, bottom: 0, left: -8 }}>
          <CartesianGrid stroke={GRID_COLOR} strokeDasharray="3 3" vertical={false} />
          <XAxis
            dataKey="time"
            type="number"
            scale="time"
            domain={['dataMin', 'dataMax']}
            tickFormatter={(time) => formatTick(time, showDates)}
            stroke={AXIS_COLOR}
            tick={{ fontSize: 11 }}
            tickLine={false}
            axisLine={{ stroke: GRID_COLOR }}
            minTickGap={40}
          />
          <YAxis
            domain={[0, yMax]}
            stroke={AXIS_COLOR}
            tick={{ fontSize: 11 }}
            tickLine={false}
            axisLine={false}
            width={48}
            tickFormatter={(value) => `${value}${isPercent ? '%' : ''}`}
          />
          <Tooltip
            cursor={{ stroke: '#52525b' }}
            contentStyle={{ background: '#18181b', border: '1px solid #3f3f46', borderRadius: 8, fontSize: 12 }}
            labelStyle={{ color: '#a1a1aa' }}
            itemStyle={{ color: '#f4f4f5' }}
            labelFormatter={(time) => new Date(time).toLocaleString()}
            formatter={(value) => [formatValue(value, unit), 'Value']}
          />
          {highlight && (
            <ReferenceArea x1={highlight.from} x2={highlight.to} fill="#ef4444" fillOpacity={0.08} ifOverflow="hidden" />
          )}
          {highlights.map((window) => (
            <ReferenceArea
              key={window.id}
              x1={window.from}
              x2={window.to}
              fill={window.color}
              fillOpacity={0.1}
              ifOverflow="hidden"
            />
          ))}
          {thresholds.map((threshold) => (
            <ReferenceLine
              key={threshold.id}
              y={threshold.value}
              stroke={threshold.color}
              strokeDasharray="6 4"
              strokeWidth={1.5}
              ifOverflow="extendDomain"
              label={{ value: threshold.label, position: 'insideTopRight', fill: threshold.color, fontSize: 11 }}
            />
          ))}
          <Line
            type="monotone"
            dataKey="value"
            stroke={LINE_COLOR}
            strokeWidth={2}
            dot={false}
            activeDot={{ r: 4, strokeWidth: 2, stroke: '#111113' }}
            connectNulls={false}
            isAnimationActive={false}
          />
        </LineChart>
      </ResponsiveContainer>
    </div>
  )
}

function formatTick(time, showDates) {
  const date = new Date(time)
  if (showDates) return date.toLocaleDateString(undefined, { month: 'short', day: 'numeric' })
  return date.toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' })
}

function formatValue(value, unit) {
  if (value === null || value === undefined) return '—'
  return unit === 'ms' ? `${Number(value).toFixed(0)} ms` : `${Number(value).toFixed(1)}%`
}
