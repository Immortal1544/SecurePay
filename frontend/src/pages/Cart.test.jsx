import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import Cart from './Cart'
import { API_BASE_URL } from '../services/apiConfig'

vi.mock('../services/authService', () => ({
  getToken: () => 'customer-test-token',
}))

describe('Cart', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn())
  })

  it('submits delivery details with the order request and shows the created order', async () => {
    const cartResponse = {
      items: [{ id: 8, productName: 'SecurePay mug', price: 25, quantity: 2, subtotal: 50 }],
      totalAmount: 50,
    }
    const orderResponse = {
      orderId: 45,
      totalAmount: 50,
      recipientName: 'Jamie Customer',
      city: 'Bengaluru',
      state: 'Karnataka',
      postalCode: '560001',
    }
    fetch.mockImplementation((url, options = {}) => {
      if (url === `${API_BASE_URL}/api/cart`) {
        return Promise.resolve({ ok: true, status: 200, json: async () => cartResponse })
      }
      if (url === `${API_BASE_URL}/api/orders` && options.method === 'POST') {
        return Promise.resolve({ ok: true, status: 201, json: async () => orderResponse })
      }
      throw new Error(`Unexpected request: ${url}`)
    })

    const user = userEvent.setup()
    render(
      <MemoryRouter>
        <Cart />
      </MemoryRouter>,
    )

    expect(await screen.findByRole('heading', { name: 'SecurePay mug' })).toBeInTheDocument()
    const deliveryDetails = {
      'Recipient name': 'Jamie Customer',
      'Phone number': '+919876543210',
      'Address line 1': '10 Market Road',
      'Address line 2 (optional)': 'Apartment 3',
      City: 'Bengaluru',
      'State / region': 'Karnataka',
      'Postal code': '560001',
      Country: 'India',
    }
    for (const [label, value] of Object.entries(deliveryDetails)) {
      await user.type(screen.getByLabelText(label), value)
    }
    await user.click(screen.getByRole('button', { name: 'Place Order' }))

    expect(await screen.findByText('Order placed successfully.')).toBeInTheDocument()
    const orderRequest = fetch.mock.calls.find(([url, options]) =>
      url === `${API_BASE_URL}/api/orders` && options.method === 'POST')
    expect(JSON.parse(orderRequest[1].body)).toEqual({
      recipientName: 'Jamie Customer',
      phoneNumber: '+919876543210',
      addressLine1: '10 Market Road',
      addressLine2: 'Apartment 3',
      city: 'Bengaluru',
      state: 'Karnataka',
      postalCode: '560001',
      country: 'India',
    })
    expect(fetch).toHaveBeenCalledTimes(2)
  })
})
