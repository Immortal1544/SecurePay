import { fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import Login from './Login'
import { loginUser } from '../services/authService'

vi.mock('../services/authService', () => ({
  loginUser: vi.fn(),
}))

describe('Login', () => {
  beforeEach(() => {
    localStorage.clear()
    loginUser.mockReset()
  })

  it('stores the returned token and user, then navigates after login', async () => {
    const response = {
      token: 'signed-test-token',
      userId: 24,
      name: 'Jamie Customer',
      email: 'jamie@example.com',
      role: 'USER',
    }
    loginUser.mockResolvedValue(response)

    render(
      <MemoryRouter initialEntries={['/login']}>
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route path="/" element={<h1>Store home</h1>} />
        </Routes>
      </MemoryRouter>,
    )

    fireEvent.change(screen.getByLabelText('Email'), { target: { value: response.email } })
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'valid-password' } })
    fireEvent.click(screen.getByRole('button', { name: 'Login' }))

    expect(await screen.findByRole('heading', { name: 'Store home' })).toBeInTheDocument()
    expect(loginUser).toHaveBeenCalledWith({ email: response.email, password: 'valid-password' })
    expect(localStorage.getItem('securepay_token')).toBe(response.token)
    expect(JSON.parse(localStorage.getItem('securepay_user'))).toEqual({
      userId: response.userId,
      name: response.name,
      email: response.email,
      role: response.role,
    })
  })
})
