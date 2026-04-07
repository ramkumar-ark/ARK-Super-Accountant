import { describe, it, expect } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { Navbar } from './Navbar'

describe('Navbar', () => {
  it('renders the brand name', () => {
    render(<Navbar />)
    expect(screen.getByText('Super Accountant')).toBeInTheDocument()
  })

  it('renders desktop nav links', () => {
    render(<Navbar />)
    expect(screen.getByRole('link', { name: 'Features' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'For CA Firms' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'How It Works' })).toBeInTheDocument()
  })

  it('renders Features link pointing to #features', () => {
    render(<Navbar />)
    expect(screen.getByRole('link', { name: 'Features' })).toHaveAttribute('href', '#features')
  })

  it('renders For CA Firms link pointing to #who-its-for', () => {
    render(<Navbar />)
    expect(screen.getByRole('link', { name: 'For CA Firms' })).toHaveAttribute('href', '#who-its-for')
  })

  it('renders How It Works link pointing to #how-it-works', () => {
    render(<Navbar />)
    expect(screen.getByRole('link', { name: 'How It Works' })).toHaveAttribute('href', '#how-it-works')
  })

  it('renders Log In link pointing to /login', () => {
    render(<Navbar />)
    expect(screen.getByRole('link', { name: 'Log In' })).toHaveAttribute('href', '/login')
  })

  it('renders Get Started Free CTA pointing to /signup', () => {
    render(<Navbar />)
    // First match is the desktop CTA
    const ctaLinks = screen.getAllByRole('link', { name: 'Get Started Free' })
    expect(ctaLinks[0]).toHaveAttribute('href', '/signup')
  })

  it('hamburger button has aria-label "Open menu" initially', () => {
    render(<Navbar />)
    expect(screen.getByRole('button', { name: 'Open menu' })).toBeInTheDocument()
  })

  it('mobile drawer opens on hamburger click', () => {
    render(<Navbar />)
    const hamburger = screen.getByRole('button', { name: 'Open menu' })
    expect(screen.getAllByRole('link', { name: 'Features' })).toHaveLength(1)
    fireEvent.click(hamburger)
    // Mobile drawer adds a second set of links
    expect(screen.getAllByRole('link', { name: 'Features' })).toHaveLength(2)
  })

  it('hamburger aria-expanded is false by default, true when open', () => {
    render(<Navbar />)
    const hamburger = screen.getByRole('button', { name: 'Open menu' })
    expect(hamburger).toHaveAttribute('aria-expanded', 'false')
    fireEvent.click(hamburger)
    expect(screen.getByRole('button', { name: 'Close menu' })).toHaveAttribute('aria-expanded', 'true')
  })
})
