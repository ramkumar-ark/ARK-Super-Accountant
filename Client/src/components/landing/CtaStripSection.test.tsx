import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { CtaStripSection } from './CtaStripSection'

describe('CtaStripSection', () => {
  it('renders the CTA heading', () => {
    render(<CtaStripSection />)
    expect(screen.getByRole('heading', { level: 2 })).toHaveTextContent(
      'Ready to put your accounting on autopilot?'
    )
  })

  it('renders the sub-text', () => {
    render(<CtaStripSection />)
    expect(screen.getByText(/automated their compliance and reclaimed their time/i)).toBeInTheDocument()
  })

  it('renders Get Started Free link pointing to /signup', () => {
    render(<CtaStripSection />)
    expect(screen.getByRole('link', { name: 'Get Started Free' })).toHaveAttribute('href', '/signup')
  })

  it('renders Secure trust badge', () => {
    render(<CtaStripSection />)
    expect(screen.getByText('Secure')).toBeInTheDocument()
  })

  it('renders No credit card required trust badge', () => {
    render(<CtaStripSection />)
    expect(screen.getByText('No credit card required')).toBeInTheDocument()
  })

  it('renders Your data stays yours trust badge', () => {
    render(<CtaStripSection />)
    expect(screen.getByText('Your data stays yours')).toBeInTheDocument()
  })
})
