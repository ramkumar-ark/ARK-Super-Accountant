import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MastersTamperWarningBanner } from './MastersTamperWarningBanner'

describe('MastersTamperWarningBanner', () => {
  it('renders for non-blocking masters tamper findings', () => {
    render(
      <MastersTamperWarningBanner
        findings={[
          {
            findingType: 'TAMPER_MASTER_MODIFIED',
            blocking: false,
            message: 'GSTIN of Acme Traders changed.',
          },
        ]}
      />,
    )
    expect(
      screen.getByText(
        'A masters change affects a finalized return of another type. It does not block this workflow.',
      ),
    ).toBeInTheDocument()
    expect(screen.getByText('GSTIN of Acme Traders changed.')).toBeInTheDocument()
  })

  it('renders nothing for blocking or voucher findings', () => {
    const { container } = render(
      <MastersTamperWarningBanner
        findings={[
          { findingType: 'TAMPER_MASTER_MODIFIED', blocking: true, message: 'blocking' },
          { findingType: 'MODIFIED', blocking: false, message: 'voucher change' },
        ]}
      />,
    )
    expect(container).toBeEmptyDOMElement()
  })
})
