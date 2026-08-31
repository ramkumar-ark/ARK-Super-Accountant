import { render, screen } from '@testing-library/react'
import { describe, it, expect } from 'vitest'
import { RoleBadge } from './RoleBadge'

describe('RoleBadge', () => {
  it('renders Owner label for ROLE_OWNER', () => {
    render(<RoleBadge role="ROLE_OWNER" />)
    const badge = screen.getByRole('status')
    expect(badge.textContent).toBe('Owner')
    expect(badge.getAttribute('aria-label')).toBe('Your role: Owner')
  })

  it('renders Accountant label for ROLE_ACCOUNTANT', () => {
    render(<RoleBadge role="ROLE_ACCOUNTANT" />)
    const badge = screen.getByRole('status')
    expect(badge.textContent).toBe('Accountant')
    expect(badge.getAttribute('aria-label')).toBe('Your role: Accountant')
  })

  it('renders Operator label for ROLE_OPERATOR', () => {
    render(<RoleBadge role="ROLE_OPERATOR" />)
    const badge = screen.getByRole('status')
    expect(badge.textContent).toBe('Operator')
    expect(badge.getAttribute('aria-label')).toBe('Your role: Operator')
  })

  it('renders CA Auditor label for ROLE_AUDITOR_CA', () => {
    render(<RoleBadge role="ROLE_AUDITOR_CA" />)
    const badge = screen.getByRole('status')
    expect(badge.textContent).toBe('CA Auditor')
    expect(badge.getAttribute('aria-label')).toBe('Your role: CA Auditor')
  })

  it('renders nothing for unknown role', () => {
    const { container } = render(<RoleBadge role="ROLE_UNKNOWN" />)
    expect(container.firstChild).toBeNull()
  })

  it('renders nothing when role is undefined', () => {
    const { container } = render(<RoleBadge role={undefined} />)
    expect(container.firstChild).toBeNull()
  })
})
