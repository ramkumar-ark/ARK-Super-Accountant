import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { DayBookPage } from './DayBookPage'

vi.mock('@/components/AppShell', () => ({
  AppShell: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}))
vi.mock('@/lib/api', () => ({
  api: {
    get: vi.fn(),
    post: vi.fn(),
  },
}))

import { api } from '@/lib/api'

const mockGet = vi.mocked(api.get)
const mockPost = vi.mocked(api.post)

const uploadsPage = {
  data: {
    content: [
      {
        id: 'job-1',
        fileName: 'daybook-april.json',
        status: 'COMPLETED',
        totalVouchersParsed: 7,
        coverageFrom: '2025-04-01',
        coverageTo: '2025-05-31',
      },
    ],
    totalElements: 1,
  },
}

describe('DayBookPage — coverage upload', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockGet.mockResolvedValue(uploadsPage as never)
  })

  it('does not query masters gate-status', async () => {
    render(<DayBookPage />)

    await waitFor(() => {
      expect(mockGet).not.toHaveBeenCalledWith('/v1/masters/gate-status')
    })
  })

  it('disables upload until file, coverage from and to are set', async () => {
    render(<DayBookPage />)

    const uploadButton = screen.getByRole('button', { name: 'Upload Day Book' })
    expect(uploadButton).toBeDisabled()

    await waitFor(() => {
      expect(mockGet).toHaveBeenCalledWith('/v1/day-book/uploads', { params: { page: 0, size: 20 } })
    })
  })

  it('posts coverageFrom and coverageTo in the form data', async () => {
    const user = userEvent.setup()
    render(<DayBookPage />)

    const file = new File(['{}'], 'daybook.json', { type: 'application/json' })
    await user.upload(screen.getByTestId('file-input'), file)
    await user.type(screen.getByLabelText('Coverage from'), '2025-04-01')
    await user.type(screen.getByLabelText('Coverage to'), '2025-04-30')

    const uploadButton = await waitFor(() => expectUploadEnabled())
    await user.click(uploadButton)

    await waitFor(() => {
      expect(mockPost).toHaveBeenCalledWith('/v1/day-book/upload', expect.any(FormData), {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      const formData = mockPost.mock.calls[0][1] as FormData
      expect(formData.get('coverageFrom')).toBe('2025-04-01')
      expect(formData.get('coverageTo')).toBe('2025-04-30')
    })
  })

  it('shows server warnings after upload', async () => {
    const user = userEvent.setup()
    mockPost.mockResolvedValue({
      data: {
        id: 'job-9',
        fileName: 'daybook.json',
        status: 'COMPLETED',
        totalVouchersParsed: 2,
        voucherSummary: [],
        warnings: [
          'First voucher is dated 2025-04-01, more than 15 days after the declared start 2025-03-01.',
        ],
      },
    } as never)
    render(<DayBookPage />)

    const file = new File(['{}'], 'daybook.json', { type: 'application/json' })
    await user.upload(screen.getByTestId('file-input'), file)
    await user.type(screen.getByLabelText('Coverage from'), '2025-03-01')
    await user.type(screen.getByLabelText('Coverage to'), '2025-04-30')

    const uploadButton = await waitFor(() => expectUploadEnabled())
    await user.click(uploadButton)

    await waitFor(() => {
      expect(
        screen.getByText(
          'First voucher is dated 2025-04-01, more than 15 days after the declared start 2025-03-01.'
        )
      ).toBeInTheDocument()
    })
  })

  it('lists previous uploads with their coverage', async () => {
    render(<DayBookPage />)

    await waitFor(() => {
      expect(screen.getByText('daybook-april.json')).toBeInTheDocument()
      expect(screen.getByText('2025-04-01 – 2025-05-31')).toBeInTheDocument()
    })
  })
})

function expectUploadEnabled() {
  const button = screen.getByRole('button', { name: 'Upload Day Book' })
  expect(button).not.toBeDisabled()
  return button
}
