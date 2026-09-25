import { agentInstallCommand } from '../lib/constants'
import { CodeBlock } from './CodeBlock'

export function AgentSnippet({ apiKey }) {
  return (
    <div className="space-y-2">
      <p className="text-sm text-muted">
        Run the agent on the host you want to monitor. It reports a metric sample every few seconds.
      </p>
      <CodeBlock label="Terminal" code={agentInstallCommand(apiKey)} />
    </div>
  )
}
