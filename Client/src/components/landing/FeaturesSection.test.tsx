import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { FeaturesSection } from './FeaturesSection'

describe('FeaturesSection', () => {
  it('renders WHAT WE DO label', () => {
    render(<FeaturesSection />)
    expect(screen.getByText('WHAT WE DO')).toBeInTheDocument()
  })

  it('renders section heading', () => {
    render(<FeaturesSection />)
    expect(screen.getByRole('heading', { level: 2 })).toHaveTextContent(
      'Everything your accounting team needs, automated.'
    )
  })

  it('renders Accounting Analytics card', () => {
    render(<FeaturesSection />)
    expect(screen.getByText('Accounting Analytics')).toBeInTheDocument()
  })

  it('renders Process Automation card', () => {
    render(<FeaturesSection />)
    expect(screen.getByText('Process Automation')).toBeInTheDocument()
  })

  it('renders GST & IT Reports card', () => {
    render(<FeaturesSection />)
    expect(screen.getByText('GST & IT Reports')).toBeInTheDocument()
  })

  it('renders Reconciliation card', () => {
    render(<FeaturesSection />)
    expect(screen.getByText('Reconciliation')).toBeInTheDocument()
  })

  it('renders Tax Return Filing card', () => {
    render(<FeaturesSection />)
    expect(screen.getByText('Tax Return Filing')).toBeInTheDocument()
  })

  it('renders Multi-Role Collaboration card', () => {
    render(<FeaturesSection />)
    expect(screen.getByText('Multi-Role Collaboration')).toBeInTheDocument()
  })

  it('renders Invoice Intelligence card', () => {
    render(<FeaturesSection />)
    expect(screen.getByText('Invoice Intelligence')).toBeInTheDocument()
  })

  it('renders the section with id="features"', () => {
    render(<FeaturesSection />)
    expect(document.getElementById('features')).toBeInTheDocument()
  })
})
