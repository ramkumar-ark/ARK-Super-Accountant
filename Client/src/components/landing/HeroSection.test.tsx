import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { HeroSection } from './HeroSection'

describe('HeroSection', () => {
  it('renders an h1 heading', () => {
    render(<HeroSection />)
    expect(screen.getByRole('heading', { level: 1 })).toBeInTheDocument()
  })

  it('renders "Autopilot" in the headline', () => {
    render(<HeroSection />)
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('Autopilot')
  })

  it('renders the sub-headline', () => {
    render(<HeroSection />)
    expect(screen.getByText(/automates your books, reports, and tax filings/i)).toBeInTheDocument()
  })

  it('renders Get Started Free CTA linking to /signup', () => {
    render(<HeroSection />)
    const cta = screen.getByRole('link', { name: 'Get Started Free' })
    expect(cta).toHaveAttribute('href', '/signup')
  })

  it('renders See How It Works link pointing to #how-it-works', () => {
    render(<HeroSection />)
    const link = screen.getByRole('link', { name: 'See How It Works' })
    expect(link).toHaveAttribute('href', '#how-it-works')
  })

  it('renders Secure & Compliant trust badge', () => {
    render(<HeroSection />)
    expect(screen.getByText(/Secure & Compliant/i)).toBeInTheDocument()
  })

  it('renders No credit card required trust badge', () => {
    render(<HeroSection />)
    expect(screen.getByText(/No credit card required/i)).toBeInTheDocument()
  })

  it('renders GST & IT ready trust badge', () => {
    render(<HeroSection />)
    expect(screen.getByText(/GST & IT ready/i)).toBeInTheDocument()
  })

  it('renders the eyebrow pill', () => {
    render(<HeroSection />)
    expect(screen.getByText(/GST · Income Tax · Reconciliation · Filings/i)).toBeInTheDocument()
  })
})
