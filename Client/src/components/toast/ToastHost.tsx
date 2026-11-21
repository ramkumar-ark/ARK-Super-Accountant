import { AlertTriangle, CheckCircle2, Info, X, XCircle, type LucideIcon } from 'lucide-react'
import { toast, useToastStore, type ToastItem, type ToastType } from './toastStore'

const TYPE_META: Record<
  ToastType,
  { borderClass: string; iconClass: string; Icon: LucideIcon; live: 'polite' | 'assertive' }
> = {
  success: {
    borderClass: 'border-l-[var(--color-success)]',
    iconClass: 'text-[var(--color-success)]',
    Icon: CheckCircle2,
    live: 'polite',
  },
  error: {
    borderClass: 'border-l-[var(--color-danger)]',
    iconClass: 'text-[var(--color-danger)]',
    Icon: XCircle,
    live: 'assertive',
  },
  warning: {
    borderClass: 'border-l-[var(--color-warning)]',
    iconClass: 'text-[var(--color-warning)]',
    Icon: AlertTriangle,
    live: 'assertive',
  },
  info: {
    borderClass: 'border-l-[var(--color-info)]',
    iconClass: 'text-[var(--color-info)]',
    Icon: Info,
    live: 'polite',
  },
}

function ToastCard({ item }: { item: ToastItem }) {
  const meta = TYPE_META[item.type]
  const { Icon } = meta
  // Errors (and warnings) interrupt; success/info stay polite.
  const role = meta.live === 'assertive' ? 'alert' : 'status'

  return (
    <div
      role={role}
      aria-live={meta.live}
      className={`toast-in pointer-events-auto flex w-full gap-3 rounded-[var(--radius-md)] border border-[var(--color-border)] border-l-4 bg-[var(--color-surface)] p-4 shadow-lg ${meta.borderClass}`}
      data-testid="toast"
      data-type={item.type}
    >
      <Icon
        size={18}
        className={`flex-shrink-0 mt-0.5 ${meta.iconClass}`}
        aria-hidden
      />
      <div className="min-w-0 flex-1">
        {item.title && (
          <p className="text-sm font-semibold text-[var(--color-text-primary)]">{item.title}</p>
        )}
        <p className="text-sm text-[var(--color-text-secondary)]">{item.message}</p>
      </div>
      <button
        type="button"
        aria-label="Dismiss notification"
        onClick={() => toast.dismiss(item.id)}
        className="flex-shrink-0 rounded-[var(--radius-sm)] p-0.5 text-[var(--color-text-muted)] transition-colors hover:text-[var(--color-text-primary)]"
      >
        <X size={14} aria-hidden />
      </button>
    </div>
  )
}

/**
 * Global toast host. Mounted once in main.tsx above the RouterProvider so
 * toasts are unaffected by route changes. Renders nothing when no toasts.
 */
export function ToastHost() {
  const toasts = useToastStore((s) => s.toasts)

  if (toasts.length === 0) return null

  return (
    <div
      aria-label="Notifications"
      className="pointer-events-none fixed top-4 right-4 z-[var(--z-toast)] flex w-[360px] max-w-[calc(100vw-2rem)] flex-col gap-2"
    >
      {toasts.map((item) => (
        <ToastCard key={item.id} item={item} />
      ))}
    </div>
  )
}
