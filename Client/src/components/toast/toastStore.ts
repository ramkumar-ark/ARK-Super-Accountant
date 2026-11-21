import { create } from 'zustand'

/**
 * Global toast notification system (design-system/MASTER.md → "Toast
 * Notifications"). In-house, backed by Zustand — no third-party toast lib.
 *
 * Usage from any component or plain module:
 *   import { toast } from '@/components/toast/toastStore'
 *   toast.success('Saved')
 *   toast.error('Upload failed', { title: 'Something went wrong', duration: 6000 })
 *   const id = toast.info('Working…'); toast.dismiss(id)
 *
 * Mount `<ToastHost />` once at the app root (see main.tsx). The host sits
 * above the router outlet, so toasts survive route navigation.
 * Types: success (green left border), error (red), warning (amber), info (blue).
 * Default auto-dismiss is 4s; pass `duration` to override (0 = no auto-dismiss).
 */

export type ToastType = 'success' | 'error' | 'warning' | 'info'

export interface ToastOptions {
  /** Optional bold heading rendered above the message. */
  title?: string
  /** Auto-dismiss delay in ms. Defaults to 4000; 0 disables auto-dismiss. */
  duration?: number
}

export interface ToastItem {
  id: string
  type: ToastType
  message: string
  title?: string
}

export const DEFAULT_TOAST_DURATION = 4000

interface ToastState {
  toasts: ToastItem[]
  dismiss: (id: string) => void
}

export const useToastStore = create<ToastState>((set) => ({
  toasts: [],
  dismiss: (id) => set((state) => ({ toasts: state.toasts.filter((t) => t.id !== id) })),
}))

const timers = new Map<string, ReturnType<typeof setTimeout>>()
let seq = 0

function clearTimer(id: string): void {
  const timer = timers.get(id)
  if (timer !== undefined) {
    clearTimeout(timer)
    timers.delete(id)
  }
}

function push(type: ToastType, message: string, options?: ToastOptions): string {
  const id = `toast-${++seq}`
  const duration = options?.duration ?? DEFAULT_TOAST_DURATION
  useToastStore.setState((state) => ({
    toasts: [...state.toasts, { id, type, message, title: options?.title }],
  }))
  if (duration > 0) {
    timers.set(
      id,
      setTimeout(() => {
        clearTimer(id)
        useToastStore.getState().dismiss(id)
      }, duration),
    )
  }
  return id
}

export const toast = {
  success: (message: string, options?: ToastOptions) => push('success', message, options),
  error: (message: string, options?: ToastOptions) => push('error', message, options),
  warning: (message: string, options?: ToastOptions) => push('warning', message, options),
  info: (message: string, options?: ToastOptions) => push('info', message, options),
  dismiss: (id: string) => {
    clearTimer(id)
    useToastStore.getState().dismiss(id)
  },
  dismissAll: () => {
    for (const id of [...timers.keys()]) clearTimer(id)
    useToastStore.setState({ toasts: [] })
  },
}
