import { useEffect, useState } from 'react'
import { api } from '@/lib/api'
import { useAuthStore } from '@/store/authStore'

interface ValidationSettings {
  requireHsnSacOnExpense: boolean
  updatedBy: string | null
  updatedAt: string | null
}

const URL = '/v1/validation-settings'

/** Per-organization validation switches. Everyone can read; only accountants and owners can change. */
export function ValidationSettingsPanel() {
  const role = useAuthStore((s) => s.user?.role)
  const canEdit = role === 'ROLE_ACCOUNTANT' || role === 'ROLE_OWNER'
  const [settings, setSettings] = useState<ValidationSettings | null>(null)
  const [loadError, setLoadError] = useState('')
  const [saving, setSaving] = useState(false)
  const [saveError, setSaveError] = useState('')

  useEffect(() => {
    let cancelled = false
    api
      .get(URL)
      .then((res) => {
        if (!cancelled) {
          setSettings({
            requireHsnSacOnExpense: res.data?.requireHsnSacOnExpense !== false,
            updatedBy: res.data?.updatedBy ?? null,
            updatedAt: res.data?.updatedAt ?? null,
          })
        }
      })
      .catch(() => {
        if (!cancelled) setLoadError('Could not load validation settings.')
      })
    return () => {
      cancelled = true
    }
  }, [])

  async function toggle(value: boolean) {
    setSaving(true)
    setSaveError('')
    try {
      const res = await api.put(URL, { requireHsnSacOnExpense: value })
      setSettings({
        requireHsnSacOnExpense: res.data.requireHsnSacOnExpense,
        updatedBy: res.data.updatedBy ?? null,
        updatedAt: res.data.updatedAt ?? null,
      })
    } catch {
      setSaveError('Could not save the setting. Try again.')
    } finally {
      setSaving(false)
    }
  }

  if (loadError) return <p role="alert" className="text-sm text-[var(--color-danger)]">{loadError}</p>
  if (!settings) return <p className="text-sm text-[var(--color-text-secondary)]">Loading settings…</p>

  return (
    <div className="max-w-2xl rounded-[var(--radius-md)] border border-[var(--color-border)] p-4">
      <label className="flex items-start gap-3">
        <input
          type="checkbox"
          className="mt-1"
          checked={settings.requireHsnSacOnExpense}
          disabled={!canEdit || saving}
          onChange={(e) => toggle(e.target.checked)}
        />
        <span>
          <span className="block text-sm font-medium">Require HSN/SAC on GST-applicable expense ledgers</span>
          <span className="block text-sm text-[var(--color-text-secondary)]">
            When off, expense ledgers are not flagged for a missing HSN/SAC code. Income ledgers are unaffected.
            Applies from the next upload.
          </span>
        </span>
      </label>
      {!canEdit && (
        <p className="mt-3 text-xs text-[var(--color-text-secondary)]">
          Only an accountant or owner can change this setting.
        </p>
      )}
      {settings.updatedBy && (
        <p className="mt-3 text-xs text-[var(--color-text-secondary)]">
          Last changed by {settings.updatedBy}
          {settings.updatedAt ? ` on ${new Date(settings.updatedAt).toLocaleString()}` : ''}.
        </p>
      )}
      {saveError && <p role="alert" className="mt-3 text-sm text-[var(--color-danger)]">{saveError}</p>}
    </div>
  )
}
