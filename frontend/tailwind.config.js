/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,jsx}'],
  theme: {
    extend: {
      colors: {
        canvas: '#09090b',
        panel: '#111113',
        'panel-raised': '#18181b',
        line: '#27272a',
        'line-strong': '#3f3f46',
        muted: '#a1a1aa',
        subtle: '#71717a',
        brand: '#10b981',
      },
      fontFamily: {
        sans: ['Inter', 'ui-sans-serif', 'system-ui', '-apple-system', 'Segoe UI', 'Roboto', 'sans-serif'],
        mono: ['ui-monospace', 'SFMono-Regular', 'Menlo', 'Consolas', 'monospace'],
      },
      keyframes: {
        'toast-in': {
          from: { opacity: 0, transform: 'translateY(8px)' },
          to: { opacity: 1, transform: 'translateY(0)' },
        },
      },
      animation: {
        'toast-in': 'toast-in 150ms ease-out',
      },
    },
  },
  plugins: [],
}
