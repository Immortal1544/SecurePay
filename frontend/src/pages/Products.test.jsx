import { render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import Products from './Products'

vi.mock('../services/authService', () => ({
  getToken: () => 'customer-test-token',
}))

describe('Products', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn())
  })

  it('shows loading while products load, then renders availability from the response', async () => {
    let resolveRequest
    fetch.mockReturnValue(new Promise((resolve) => {
      resolveRequest = resolve
    }))

    render(<Products />)

    expect(screen.getByText('Loading products...')).toBeInTheDocument()

    resolveRequest({
      ok: true,
      json: async () => [
        { id: 1, name: 'Active mug', description: 'Ceramic', price: 12.5, stockQuantity: 4, active: true },
        { id: 2, name: 'Inactive mug', description: '', price: 8, stockQuantity: 0, active: false },
      ],
    })

    expect(await screen.findByRole('heading', { name: 'Active mug' })).toBeInTheDocument()
    expect(screen.getByText('Inactive')).toBeInTheDocument()
    expect(screen.getByText('Currently unavailable')).toBeInTheDocument()
    expect(screen.getAllByRole('button', { name: 'Add to Cart' })).toHaveLength(1)
    expect(fetch).toHaveBeenCalledTimes(1)
  })

  it('shows an error message when the product request fails', async () => {
    fetch.mockRejectedValue(new Error('network unavailable'))

    render(<Products />)

    expect(await screen.findByRole('alert')).toHaveTextContent('network unavailable')
  })
})
