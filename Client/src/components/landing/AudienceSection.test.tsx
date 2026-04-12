import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { AudienceSection } from './AudienceSection'

describe('AudienceSection', () => {
  it('renders WHO IT\'S FOR label', () => {
    render(<AudienceSection />)
    expect(screen.getByText("WHO IT'S FOR")).toBeInTheDocument()
  })

  it('renders section heading', () => {
    render(<AudienceSection />)
    expect(screen.getByRole('heading', { level: 2 })).toHaveTextContent(
      'Built for the people who run the numbers.'
    )
  })

  it('renders For Business Owners & Managers card heading', () => {
    render(<AudienceSection />)
    expect(screen.getByText('For Business Owners & Managers')).toBeInTheDocument()
  })

  it('renders For CA Firms card heading', () => {
    render(<AudienceSection />)
    expect(screen.getByText('For CA Firms')).toBeInTheDocument()
  })

  it('renders owner sub-headline', () => {
    render(<AudienceSection />)
    expect(screen.getByText('Stay in control without being in the books.')).toBeInTheDocument()
  })

  it('renders CA firm sub-headline', () => {
    render(<AudienceSection />)
    expect(screen.getByText('Manage more clients with less effort.')).toBeInTheDocument()
  })

  it('renders owner benefit bullets', () => {
    render(<AudienceSection />)
    expect(screen.getByText('Always-current financial position')).toBeInTheDocument()
    expect(screen.getByText('Auto-generated P&L and cash flow reports')).toBeInTheDocument()
    expect(screen.getByText('Tax obligations tracked and filed on time')).toBeInTheDocument()
    expect(screen.getByText('Approve and review — without doing the work')).toBeInTheDocument()
    expect(screen.getByText('One dashboard for all your entities')).toBeInTheDocument()
  })

  it('renders CA firm benefit bullets', () => {
    render(<AudienceSection />)
    expect(screen.getByText('Centralized client data — no more file juggling')).toBeInTheDocument()
    expect(screen.getByText('Automated GST, TDS, and ITR preparation')).toBeInTheDocument()
    expect(screen.getByText('Role-based access for your team and clients')).toBeInTheDocument()
    expect(screen.getByText('Exception-based workflow — review only what needs attention')).toBeInTheDocument()
    expect(screen.getByText('Audit trail on every transaction')).toBeInTheDocument()
  })

  it('renders section with id="who-its-for"', () => {
    render(<AudienceSection />)
    expect(document.getElementById('who-its-for')).toBeInTheDocument()
  })
})
