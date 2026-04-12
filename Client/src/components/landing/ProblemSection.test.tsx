import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { ProblemSection } from './ProblemSection'

describe('ProblemSection', () => {
  it('renders THE PROBLEM label', () => {
    render(<ProblemSection />)
    expect(screen.getByText('THE PROBLEM')).toBeInTheDocument()
  })

  it('renders the section heading', () => {
    render(<ProblemSection />)
    expect(screen.getByRole('heading', { level: 2 })).toHaveTextContent(
      'Manual accounting is costing you more than you think.'
    )
  })

  it('renders Hours lost to data entry card', () => {
    render(<ProblemSection />)
    expect(screen.getByText('Hours lost to data entry')).toBeInTheDocument()
  })

  it('renders Missed compliance deadlines card', () => {
    render(<ProblemSection />)
    expect(screen.getByText('Missed compliance deadlines')).toBeInTheDocument()
  })

  it('renders No single source of truth card', () => {
    render(<ProblemSection />)
    expect(screen.getByText('No single source of truth')).toBeInTheDocument()
  })

  it('renders all 3 card body texts', () => {
    render(<ProblemSection />)
    expect(screen.getByText(/days keying transactions/i)).toBeInTheDocument()
    expect(screen.getByText(/one missed date means penalties/i)).toBeInTheDocument()
    expect(screen.getByText(/different versions of the same data/i)).toBeInTheDocument()
  })
})
