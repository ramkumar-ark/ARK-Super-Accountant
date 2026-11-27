import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { StatutoryConfigPage } from './StatutoryConfigPage'

vi.mock('@/components/AppShell', () => ({
  AppShell: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}))
vi.mock('@/lib/statutoryAdminApi', async (importOriginal) => {
  const mod = await importOriginal<typeof import('@/lib/statutoryAdminApi')>()
  return {
    ...mod,
    getStatutorySections: vi.fn(),
    getStatutoryAudit: vi.fn().mockResolvedValue([]),
    createStatutoryRule: vi.fn(),
    createStatutorySection: vi.fn(),
    setStatutorySectionActive: vi.fn(),
    deleteStatutorySection: vi.fn(),
  }
})

import {
  getStatutorySections,
  createStatutoryRule,
} from '@/lib/statutoryAdminApi'
import type { StatutorySectionsResponse } from '@/lib/statutoryAdminApi'

const RULE = {
  id: 'rule-1',
  ruleContext: 'DEFAULT',
  rateCompany: 2,
  rateNonCompany: 1,
  thresholdPerTransaction: null,
  thresholdAnnual: null,
  thresholdMonthly: null,
  thresholdExcessOnly: null,
  higherRateIfNoPan: 20,
  effectiveFrom: '2026-04-01',
  effectiveTo: null,
  legalReference: '393(1) [Table: Sl. No. 6(i)]',
  sourceCitation: 'Income-tax Act 2025 s.393',
  seeded: true,
  createdBy: null,
  updatedAt: '2026-01-01T00:00:00Z',
}

const SECTIONS: StatutorySectionsResponse = {
  canEdit: false,
  sections: [
    {
      id: 's-194c',
      statute: 'INCOME_TAX',
      code: '194C',
      description: 'Payments to contractors and sub-contractors',
      rpu26qCode: '94C',
      rpu140Code: '1024',
      rpu140PayeeSplit: false,
      sortOrder: 2,
      active: true,
      seeded: true,
      rules: [RULE],
    },
    {
      id: 's-b2cl',
      statute: 'CGST',
      code: 'B2CL_THRESHOLD',
      description: 'GST B2CL threshold',
      rpu26qCode: null,
      rpu140Code: null,
      rpu140PayeeSplit: false,
      sortOrder: 1,
      active: true,
      seeded: true,
      rules: [
        { ...RULE, rateCompany: null, rateNonCompany: null, thresholdAnnual: 500000 },
      ],
    },
  ],
}

function renderPage(canEdit = false, base: StatutorySectionsResponse = SECTIONS) {
  const data = { ...base, canEdit }
  vi.mocked(getStatutorySections).mockResolvedValue(data)
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={qc}>
      <StatutoryConfigPage />
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe('StatutoryConfigPage', () => {
  it('shows sections and rule rows for a non-admin with no controls', async () => {
    renderPage(false)
    expect(await screen.findByText('194C')).toBeInTheDocument()
    expect(screen.getByText('Payments to contractors and sub-contractors')).toBeInTheDocument()
    expect(screen.getAllByText(/2026-04-01/).length).toBeGreaterThan(0)
    expect(screen.queryByText('Clone to new dated row')).not.toBeInTheDocument()
    expect(screen.queryByText('Delete')).not.toBeInTheDocument()
  })

  it('reveals the source citation through the source disclosure', async () => {
    const user = userEvent.setup()
    renderPage(false)
    await screen.findByText('194C')
    await user.click(screen.getAllByRole('button', { name: 'Source' })[0])
    expect(await screen.findByText(/Income-tax Act 2025 s\.393/)).toBeInTheDocument()
  })

  it('hides the audit log tab for non-admins', async () => {
    renderPage(false)
    await screen.findByText('194C')
    expect(screen.queryByRole('tab', { name: /Audit log/ })).not.toBeInTheDocument()
  })

  it('shows the audit log tab for admins', async () => {
    renderPage(true)
    await screen.findByText('194C')
    expect(screen.getByRole('tab', { name: /Audit log/ })).toBeInTheDocument()
  })

  it('lists GST parameters in the GST tab', async () => {
    renderPage(false)
    await screen.findByText('194C')
    await userEvent.setup().click(screen.getByRole('tab', { name: /GST parameters/ }))
    expect(await screen.findByText('B2CL_THRESHOLD')).toBeInTheDocument()
  })

  it('opens a prefilled clone form and validates the financial-year start inline', async () => {
    const user = userEvent.setup()
    renderPage(true)
    await screen.findByText('194C')
    await user.click(screen.getByText('Clone to new dated row'))
    // Prefilled from the row.
    expect(screen.getByDisplayValue('2')).toBeInTheDocument()
    await user.type(screen.getByLabelText(/Effective from/), '2027-04-02')
    await user.type(screen.getByLabelText(/Source citation/), 'Finance Act 2026')
    await user.click(screen.getByRole('button', { name: 'Add rule' }))
    expect(
      await screen.findByText(/effectiveFrom must be a financial-year start \(1 April\) for a regime change/),
    ).toBeInTheDocument()
    expect(createStatutoryRule).not.toHaveBeenCalled()
  })

  it('submits a valid clone with the entered values and shows server errors', async () => {
    const user = userEvent.setup()
    vi.mocked(createStatutoryRule).mockRejectedValueOnce({
      response: { data: { message: 'rows are append-only' } },
    })
    renderPage(true)
    await screen.findByText('194C')
    await user.click(screen.getByText('Clone to new dated row'))
    await user.type(screen.getByLabelText(/Effective from/), '2027-04-01')
    await user.type(screen.getByLabelText(/Source citation/), 'Finance Act 2026')
    await user.click(screen.getByRole('button', { name: 'Add rule' }))
    await waitFor(() => expect(createStatutoryRule).toHaveBeenCalledWith('s-194c', expect.objectContaining({
      effectiveFrom: '2027-04-01',
      sourceCitation: 'Finance Act 2026',
      effectiveDateKind: 'FINANCIAL_YEAR_START',
    })))
    expect(await screen.findByText(/rows are append-only/)).toBeInTheDocument()
  })

  it('carries per-transaction threshold and excess-only through a clone', async () => {
    const user = userEvent.setup()
    renderPage(true, {
      ...SECTIONS,
      sections: SECTIONS.sections.map((sec) =>
        sec.code === '194C'
          ? { ...sec, rules: [{ ...RULE, thresholdPerTransaction: 30000, thresholdExcessOnly: true }] }
          : sec,
      ),
    })
    await screen.findByText('194C')
    await user.click(screen.getByText('Clone to new dated row'))
    await user.type(screen.getByLabelText(/Effective from/), '2027-04-01')
    await user.type(screen.getByLabelText(/Source citation/), 'Finance Act 2026')
    await user.click(screen.getByRole('button', { name: 'Add rule' }))
    await waitFor(() => expect(createStatutoryRule).toHaveBeenCalledWith('s-194c', expect.objectContaining({
      thresholdPerTransaction: 30000,
      thresholdExcessOnly: true,
    })))
  })

  it('shows no delete for seeded sections and a warning when backdating', async () => {
    renderPage(true)
    await screen.findByText('194C')
    expect(screen.queryByRole('button', { name: 'Delete' })).not.toBeInTheDocument()
  })
})
