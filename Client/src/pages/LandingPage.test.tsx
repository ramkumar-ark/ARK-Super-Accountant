import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { LandingPage } from './LandingPage'

describe('LandingPage', () => {
  it('renders the navbar', () => {
    render(<LandingPage />)
    expect(screen.getByRole('navigation', { name: 'Main navigation' })).toBeInTheDocument()
  })

  it('renders the main content area', () => {
    render(<LandingPage />)
    expect(screen.getByRole('main')).toBeInTheDocument()
  })

  it('renders the footer', () => {
    render(<LandingPage />)
    expect(screen.getByRole('contentinfo')).toBeInTheDocument()
  })

  it('renders the hero heading', () => {
    render(<LandingPage />)
    expect(screen.getByRole('heading', { level: 1 })).toBeInTheDocument()
  })

  it('renders the features section', () => {
    render(<LandingPage />)
    expect(document.getElementById('features')).toBeInTheDocument()
  })

  it('renders the how it works section', () => {
    render(<LandingPage />)
    expect(document.getElementById('how-it-works')).toBeInTheDocument()
  })

  it('renders the audience section', () => {
    render(<LandingPage />)
    expect(document.getElementById('who-its-for')).toBeInTheDocument()
  })
})
