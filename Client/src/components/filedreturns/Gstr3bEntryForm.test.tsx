import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Gstr3bEntryForm } from './Gstr3bEntryForm'

vi.mock('@/lib/api', () => ({
  api: {
    post: vi.fn(),
  },
}))

import { api } from '@/lib/api'

const mockPost = vi.mocked(api.post)

const parsed = {
  fileId: 'f1',
  gstin: '27ABCDE1234F1Z5',
  filingPeriod: '2026-04',
  rows: [{ rowCode: '3.1a', taxable: 100000, igst: null, cgst: null, sgst: null, cess: null }],
  warnings: ['Row 3.1a: expected 5 amounts, found 3'],
  parserVersion: 'gstr3b-pdf-1',
}

describe('Gstr3bEntryForm', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockPost.mockResolvedValue({ data: {} })
  })

  it('parsedFormPrefillsAndSavesWithFileId', async () => {
    const onSaved = vi.fn()
    render(<Gstr3bEntryForm filingPeriod="2026-04" initial={parsed} onSaved={onSaved} />)

    const taxable = screen.getByLabelText('3.1a taxable') as HTMLInputElement
    expect(taxable.value).toBe('100000')
    const igst = screen.getByLabelText('3.1a igst') as HTMLInputElement
    expect(igst.value).toBe('')
    expect(screen.getByText('Row 3.1a: expected 5 amounts, found 3')).toBeInTheDocument()
    expect(screen.getByText('27ABCDE1234F1Z5')).toBeInTheDocument()

    const user = userEvent.setup()
    await user.type(igst, '18000')
    await user.click(screen.getByRole('button', { name: 'Save GSTR-3B' }))

    expect(mockPost).toHaveBeenCalledWith('/v1/filed-returns/gstr3b', {
      filingPeriod: '2026-04',
      gstin: '27ABCDE1234F1Z5',
      fileId: 'f1',
      parserVersion: 'gstr3b-pdf-1',
      rows: [{ rowCode: '3.1a', taxable: 100000, igst: 18000, cgst: null, sgst: null, cess: null }],
    })
    expect(onSaved).toHaveBeenCalled()
  })

  it('unreadablePdfFallsBackToManualForm', () => {
    const onSaved = vi.fn()
    render(
      <Gstr3bEntryForm
        filingPeriod="2026-04"
        parseError="Could not read a GSTR-3B from this PDF; enter the values manually"
        onSaved={onSaved}
      />,
    )

    expect(
      screen.getByText('Could not read a GSTR-3B from this PDF; enter the values manually'),
    ).toBeInTheDocument()
    expect(screen.getByLabelText('3.1a taxable')).toHaveValue('')
    expect(screen.getByLabelText('Filing period')).toBeInTheDocument()
  })
})
