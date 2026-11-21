import { render, screen, act, fireEvent } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import {
  createMemoryHistory,
  createRootRoute,
  createRoute,
  createRouter,
  RouterProvider,
} from '@tanstack/react-router'
import { ToastHost } from './ToastHost'
import { toast } from './toastStore'

function renderHost() {
  return render(<ToastHost />)
}

describe('ToastHost', () => {
  beforeEach(() => {
    toast.dismissAll()
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
    toast.dismissAll()
  })

  it('renders nothing when there are no toasts', () => {
    renderHost()
    expect(screen.queryByTestId('toast')).not.toBeInTheDocument()
  })

  it('shows a success toast with its message', () => {
    renderHost()
    act(() => {
      toast.success('Account created successfully')
    })
    expect(screen.getByText('Account created successfully')).toBeInTheDocument()
    expect(screen.getByTestId('toast')).toHaveAttribute('data-type', 'success')
  })

  it('renders an optional title above the message', () => {
    renderHost()
    act(() => {
      toast.error('Boom', { title: 'Upload failed' })
    })
    expect(screen.getByText('Upload failed')).toBeInTheDocument()
    expect(screen.getByText('Boom')).toBeInTheDocument()
  })

  it('uses role=alert for errors and role=status for success/info', () => {
    renderHost()
    act(() => {
      toast.error('Bad thing')
      toast.success('Good thing')
      toast.info('FYI')
    })
    expect(screen.getByRole('alert')).toHaveTextContent('Bad thing')
    const statuses = screen.getAllByRole('status')
    expect(statuses.map((el) => el.textContent)).toEqual(
      expect.arrayContaining(['Good thing', 'FYI']),
    )
  })

  it('stacks multiple toasts', () => {
    renderHost()
    act(() => {
      toast.success('First')
      toast.warning('Second')
      toast.info('Third')
    })
    expect(screen.getAllByTestId('toast')).toHaveLength(3)
    expect(screen.getByText('First')).toBeInTheDocument()
    expect(screen.getByText('Second')).toBeInTheDocument()
    expect(screen.getByText('Third')).toBeInTheDocument()
  })

  it('auto-dismisses after the default 4s', () => {
    renderHost()
    act(() => {
      toast.success('Fleeting')
    })
    act(() => {
      vi.advanceTimersByTime(3999)
    })
    expect(screen.getByText('Fleeting')).toBeInTheDocument()
    act(() => {
      vi.advanceTimersByTime(1)
    })
    expect(screen.queryByText('Fleeting')).not.toBeInTheDocument()
  })

  it('honours a configurable duration', () => {
    renderHost()
    act(() => {
      toast.info('Quick', { duration: 1000 })
    })
    act(() => {
      vi.advanceTimersByTime(1000)
    })
    expect(screen.queryByText('Quick')).not.toBeInTheDocument()
  })

  it('never auto-dismisses when duration is 0', () => {
    renderHost()
    act(() => {
      toast.warning('Sticky', { duration: 0 })
    })
    act(() => {
      vi.advanceTimersByTime(60_000)
    })
    expect(screen.getByText('Sticky')).toBeInTheDocument()
  })

  it('dismisses manually via the dismiss button', () => {
    renderHost()
    act(() => {
      toast.success('Click me away')
    })
    fireEvent.click(screen.getByRole('button', { name: 'Dismiss notification' }))
    expect(screen.queryByText('Click me away')).not.toBeInTheDocument()
    // Manual dismiss also cancels the pending auto-dismiss timer.
    act(() => {
      vi.advanceTimersByTime(10_000)
    })
    expect(screen.queryByTestId('toast')).not.toBeInTheDocument()
  })

  it('keeps toasts visible across a route navigation', async () => {
    // Router loading uses real async scheduling; drop fake timers here.
    vi.useRealTimers()
    const rootRoute = createRootRoute()
    function FromPage() {
      return (
        <button type="button" onClick={() => router.history.push('/to')}>
          Go
        </button>
      )
    }
    const fromRoute = createRoute({
      getParentRoute: () => rootRoute,
      path: '/from',
      component: FromPage,
    })
    const toRoute = createRoute({
      getParentRoute: () => rootRoute,
      path: '/to',
      component: () => <p>Page two</p>,
    })
    const router = createRouter({
      routeTree: rootRoute.addChildren([fromRoute, toRoute]),
      history: createMemoryHistory({ initialEntries: ['/from'] }),
    })

    render(
      <>
        <ToastHost />
        <RouterProvider router={router} />
      </>,
    )

    await screen.findByRole('button', { name: 'Go' })
    act(() => {
      toast.success('Survives navigation')
    })
    expect(screen.getByText('Survives navigation')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Go' }))
    await screen.findByText('Page two')
    expect(screen.getByText('Survives navigation')).toBeInTheDocument()
  })
})
