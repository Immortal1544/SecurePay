import { render, screen, within } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { API_BASE_URL } from '../services/apiConfig'
import Orders from './Orders'

vi.mock('../services/authService', () => ({
  getToken: () => 'customer-test-token',
}))

const orders = [
  { orderId: 9, status: 'CANCELLED', totalAmount: 80, createdAt: '2026-01-01T12:00:00', items: [] },
  { orderId: 10, status: 'PAYMENT_PENDING', totalAmount: 90, createdAt: '2026-01-02T12:00:00', items: [] },
  { orderId: 11, status: 'CREATED', totalAmount: 100, createdAt: '2026-01-03T12:00:00', items: [] },
  { orderId: 12, status: 'PROCESSING', totalAmount: 110, createdAt: '2026-01-04T12:00:00', items: [] },
  { orderId: 13, status: 'CANCELLED', totalAmount: 120, createdAt: '2026-01-05T12:00:00', items: [] },
  { orderId: 14, status: 'PAYMENT_PENDING', totalAmount: 130, createdAt: '2026-01-06T12:00:00', items: [] },
]

const payments = [
  { orderId: 9, status: 'SUCCESS', razorpayPaymentId: 'not-rendered' },
  { orderId: 10, status: 'PENDING' },
  { orderId: 12, status: 'FAILED' },
  { orderId: 13, status: 'REFUNDED' },
  { orderId: 14, status: 'CREATED' },
]

describe('Orders payment status', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn((url) => {
      if (url === `${API_BASE_URL}/api/orders`) {
        return Promise.resolve({ ok: true, json: async () => orders })
      }
      if (url === `${API_BASE_URL}/api/payments`) {
        return Promise.resolve({ ok: true, json: async () => payments })
      }
      throw new Error(`Unexpected request: ${url}`)
    }))
  })

  it('shows payment status separately from order status and preserves payment retry', async () => {
    render(<Orders />)

    expect(await screen.findByText('Successful')).toBeInTheDocument()

    const orderCard = (orderId) => screen.getByRole('heading', { name: `#${orderId}` }).closest('article')
    expect(within(orderCard(9)).getByText('CANCELLED')).toBeInTheDocument()
    expect(within(orderCard(9)).getByText('Successful')).toBeInTheDocument()
    expect(within(orderCard(10)).getByText('Pending')).toBeInTheDocument()
    expect(within(orderCard(11)).getByText('No payment recorded')).toBeInTheDocument()
    expect(within(orderCard(12)).getByText('Failed')).toBeInTheDocument()
    expect(within(orderCard(13)).getByText('Refunded')).toBeInTheDocument()
    expect(within(orderCard(14)).getByText('Created')).toBeInTheDocument()
    expect(within(orderCard(9)).queryByText('not-rendered')).not.toBeInTheDocument()
    expect(within(orderCard(10)).getByRole('button', { name: 'Pay Now' })).toBeInTheDocument()
    expect(fetch).toHaveBeenCalledWith(`${API_BASE_URL}/api/payments`, {
      headers: { Authorization: 'Bearer customer-test-token' },
    })
  })
})
