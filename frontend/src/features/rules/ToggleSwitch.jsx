export function ToggleSwitch({ checked, onChange, label, disabled = false }) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      aria-label={label}
      disabled={disabled}
      onClick={() => onChange(!checked)}
      className={`relative inline-flex h-5 w-9 shrink-0 items-center rounded-full transition-colors disabled:cursor-wait disabled:opacity-60 ${
        checked ? 'bg-emerald-500' : 'bg-zinc-700'
      }`}
    >
      <span
        className={`inline-block h-4 w-4 rounded-full bg-white shadow transition-transform ${checked ? 'translate-x-4' : 'translate-x-0.5'}`}
      />
    </button>
  )
}
