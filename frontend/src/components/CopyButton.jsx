import { Check, Copy } from 'lucide-react'
import { useCopyToClipboard } from '../hooks/useCopyToClipboard'
import { useToast } from '../hooks/useToast'
import { Button } from './Button'

export function CopyButton({ text, label = 'Copy', size = 'sm', variant = 'secondary' }) {
  const { copied, copy } = useCopyToClipboard()
  const toast = useToast()

  async function handleClick() {
    const ok = await copy(text)
    if (!ok) toast.error('Could not copy to clipboard')
  }

  return (
    <Button size={size} variant={variant} icon={copied ? Check : Copy} onClick={handleClick}>
      {copied ? 'Copied' : label}
    </Button>
  )
}
