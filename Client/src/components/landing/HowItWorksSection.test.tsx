import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { HowItWorksSection } from './HowItWorksSection'

describe('HowItWorksSection', () => {
  it('renders HOW IT WORKS label', () => {
    render(<HowItWorksSection />)
    expect(screen.getByText('HOW IT WORKS')).toBeInTheDocument()
  })

  it('renders section heading', () => {
    render(<HowItWorksSection />)
    expect(screen.getByRole('heading', { level: 2 })).toHaveTextContent(
      'From raw data to filed returns — automatically.'
    )
  })

  it('renders step 01 Import Your Data', () => {
    render(<HowItWorksSection />)
    expect(screen.getByText('Import Your Data')).toBeInTheDocument()
  })

  it('renders step 02 Automated Processing', () => {
    render(<HowItWorksSection />)
    expect(screen.getByText('Automated Processing')).toBeInTheDocument()
  })

  it('renders step 03 Reports Generated', () => {
    render(<HowItWorksSection />)
    expect(screen.getByText('Reports Generated')).toBeInTheDocument()
  })

  it('renders step 04 Review & File', () => {
    render(<HowItWorksSection />)
    expect(screen.getByText('Review & File')).toBeInTheDocument()
  })

  it('mentions invoice upload in step 01 description', () => {
    render(<HowItWorksSection />)
    expect(screen.getByText(/upload invoices directly/i)).toBeInTheDocument()
  })

  it('renders section with id="how-it-works"', () => {
    render(<HowItWorksSection />)
    expect(document.getElementById('how-it-works')).toBeInTheDocument()
  })
})
