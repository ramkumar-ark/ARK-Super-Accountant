import { Copy, CheckCircle } from 'lucide-react'
import { useState } from 'react'

export function InviteTokenDisplay({ token, expiresAt }: { token: string; expiresAt: string }) {
  const [copied, setCopied] = useState(false)

  async function handleCopy() {
    await navigator.clipboard.writeText(token)
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }

  const expiryDate = new Date(expiresAt).toLocaleDateString('en-IN', { year: 'numeric', month: 'short', day: 'numeric' })

  return (
    <div className="mt-4 bg-[var(--color-surface)] border border-[var(--color-border)] rounded-[var(--radius-md)] p-4">
      <p className="text-sm text-[var(--color-text-primary)] mb-2 font-medium">Invite Link Generated</p>
      <div className="flex items-center gap-2">
        <input
          type="text"
          readOnly
          value={token}
          className="flex-1 h-11 px-3 font-mono text-sm bg-[var(--color-surface-raised)] border border-[var(--color-border)] rounded-[var(--radius-md)] text-[var(--color-text-primary)]"
        />
        <button
          onClick={handleCopy}
          aria-label="Copy invite link to clipboard"
          className="flex items-center gap-1.5 h-11 px-3 rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-surface)] hover:bg-[var(--color-surface-raised)] text-sm text-[var(--color-text-primary)] transition-colors"
        >
          {copied ? <CheckCircle size={14} className="text-[var(--color-success)]" /> : <Copy size={14} />}
          <span>Copy</span>
        </button>
      </div>
      <p className="mt-2 text-xs text-[var(--color-text-muted)]">Expires in 7 days ({expiryDate})</p>
      <p className="mt-1 text-xs text-[var(--color-warning)]">Share this link privately. It can only be used once.</p>
    </div>
  )
}
