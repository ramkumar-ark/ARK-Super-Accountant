import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { LandingFooter } from './LandingFooter'

describe('LandingFooter', () => {
  it('renders Super Accountant brand name', () => {
    render(<LandingFooter />)
    expect(screen.getByText('Super Accountant')).toBeInTheDocument()
  })

  it('renders tagline', () => {
    render(<LandingFooter />)
    expect(screen.getByText('Accounting on Autopilot.')).toBeInTheDocument()
  })

  it('renders copyright notice', () => {
    render(<LandingFooter />)
    expect(screen.getByText(/© 2026 Super Accountant/i)).toBeInTheDocument()
  })

  it('renders Product column heading', () => {
    render(<LandingFooter />)
    expect(screen.getByText('Product')).toBeInTheDocument()
  })

  it('renders Company column heading', () => {
    render(<LandingFooter />)
    expect(screen.getByText('Company')).toBeInTheDocument()
  })

  it('renders Legal column heading', () => {
    render(<LandingFooter />)
    expect(screen.getByText('Legal')).toBeInTheDocument()
  })

  it('renders product links', () => {
    render(<LandingFooter />)
    expect(screen.getByRole('link', { name: 'Features' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'How It Works' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'For CA Firms' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Security' })).toBeInTheDocument()
  })

  it('renders company links', () => {
    render(<LandingFooter />)
    expect(screen.getByRole('link', { name: 'About' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Contact' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Blog' })).toBeInTheDocument()
  })

  it('renders legal links', () => {
    render(<LandingFooter />)
    expect(screen.getByRole('link', { name: 'Privacy Policy' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Terms of Service' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Cookie Policy' })).toBeInTheDocument()
  })

  it('renders footer as a footer landmark', () => {
    render(<LandingFooter />)
    expect(screen.getByRole('contentinfo')).toBeInTheDocument()
  })
})
