import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { API_BASE_URL } from './apiConfig'
import { getCurrentUser, getToken, loginUser, logoutUser } from './authService'

describe('authService', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.stubGlobal('fetch', vi.fn())
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('sends login credentials to the existing login endpoint and returns the response', async () => {
    const loginResponse = {
      token: 'test-token',
      userId: 17,
      name: 'Alex User',
      email: 'alex@example.com',
      role: 'USER',
    }
    fetch.mockResolvedValue({
      ok: true,
      json: async () => loginResponse,
    })

    await expect(loginUser({ email: 'alex@example.com', password: 'secret' }))
      .resolves.toEqual(loginResponse)

    expect(fetch).toHaveBeenCalledWith(`${API_BASE_URL}/api/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email: 'alex@example.com', password: 'secret' }),
    })
  })

  it('reads stored authentication data and removes it on logout', () => {
    const user = { userId: 17, name: 'Alex User', email: 'alex@example.com', role: 'USER' }
    localStorage.setItem('securepay_token', 'test-token')
    localStorage.setItem('securepay_user', JSON.stringify(user))

    expect(getToken()).toBe('test-token')
    expect(getCurrentUser()).toEqual(user)

    logoutUser()

    expect(getToken()).toBeNull()
    expect(getCurrentUser()).toBeNull()
  })

  it('returns null for malformed stored user data', () => {
    localStorage.setItem('securepay_user', '{invalid json')

    expect(getCurrentUser()).toBeNull()
  })
})
