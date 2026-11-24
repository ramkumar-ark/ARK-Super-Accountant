import { describe, it, expect } from 'vitest'
import { apiErrorMessage } from '@/lib/apiError'

describe('apiErrorMessage', () => {
  it('prefers response.data.error over message', () => {
    const err = { response: { data: { error: 'E1', message: 'M1' } } }
    expect(apiErrorMessage(err, 'F')).toBe('E1')
  })

  it('uses response.data.message when error is absent', () => {
    const err = { response: { data: { message: 'Unknown TDS section: 194X' } } }
    expect(apiErrorMessage(err, 'F')).toBe('Unknown TDS section: 194X')
  })

  it('returns fallback when neither error nor message present', () => {
    const err = { response: { data: {} } }
    expect(apiErrorMessage(err, 'F')).toBe('F')
  })

  it('returns fallback for non-axios errors', () => {
    expect(apiErrorMessage(new Error('boom'), 'F')).toBe('F')
    expect(apiErrorMessage('boom', 'F')).toBe('F')
  })

  it('falls through to fallback when message is empty string', () => {
    const err = { response: { data: { message: '' } } }
    expect(apiErrorMessage(err, 'F')).toBe('F')
  })
})
