import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { getToken } from '../services/authService'

const CART_URL = 'http://localhost:8080/api/cart'
const ORDERS_URL = 'http://localhost:8080/api/orders'
const EMPTY_DELIVERY_DETAILS = {
  recipientName: '',
  phoneNumber: '',
  addressLine1: '',
  addressLine2: '',
  city: '',
  state: '',
  postalCode: '',
  country: '',
}
const rupeeFormatter = new Intl.NumberFormat('en-IN', {
  style: 'currency',
  currency: 'INR',
  maximumFractionDigits: 2,
})

function getAuthHeaders(token) {
  return {
    Authorization: `Bearer ${token}`,
    'Content-Type': 'application/json',
  }
}

async function readResponse(response) {
  if (response.status === 204) {
    return null
  }

  try {
    return await response.json()
  } catch {
    return null
  }
}

function Cart() {
  const [cart, setCart] = useState(null)
  const [isLoading, setIsLoading] = useState(true)
  const [loadingAction, setLoadingAction] = useState('')
  const [error, setError] = useState('')
  const [createdOrder, setCreatedOrder] = useState(null)
  const [deliveryDetails, setDeliveryDetails] = useState(EMPTY_DELIVERY_DETAILS)

  useEffect(() => {
    async function fetchCart() {
      const token = getToken()

      if (!token) {
        setError('Please log in to view your cart.')
        setIsLoading(false)
        return
      }

      try {
        const response = await fetch(CART_URL, {
          headers: getAuthHeaders(token),
        })

        if (!response.ok) {
          throw new Error('Unable to load cart.')
        }

        setCart(await readResponse(response))
      } catch {
        setError('Unable to load your cart. Please try again.')
      } finally {
        setIsLoading(false)
      }
    }

    fetchCart()
  }, [])

  async function updateCartItem(item, quantity) {
    if (quantity < 1) {
      return
    }

    const token = getToken()
    if (!token) {
      setError('Please log in to update your cart.')
      return
    }

    setError('')
    setLoadingAction(`update-${item.id}`)
    try {
      const response = await fetch(`${CART_URL}/items/${item.id}`, {
        method: 'PUT',
        headers: getAuthHeaders(token),
        body: JSON.stringify({ quantity }),
      })

      if (!response.ok) {
        throw new Error('Unable to update cart item.')
      }

      setCart(await readResponse(response))
    } catch {
      setError('Unable to update this item. Please try again.')
    } finally {
      setLoadingAction('')
    }
  }

  async function removeCartItem(itemId) {
    const token = getToken()
    if (!token) {
      setError('Please log in to update your cart.')
      return
    }

    setError('')
    setLoadingAction(`remove-${itemId}`)
    try {
      const response = await fetch(`${CART_URL}/items/${itemId}`, {
        method: 'DELETE',
        headers: getAuthHeaders(token),
      })

      if (!response.ok) {
        throw new Error('Unable to remove cart item.')
      }

      setCart(await readResponse(response))
    } catch {
      setError('Unable to remove this item. Please try again.')
    } finally {
      setLoadingAction('')
    }
  }

  async function clearCart() {
    const token = getToken()
    if (!token) {
      setError('Please log in to update your cart.')
      return
    }

    setError('')
    setLoadingAction('clear')
    try {
      const response = await fetch(CART_URL, {
        method: 'DELETE',
        headers: getAuthHeaders(token),
      })

      if (!response.ok) {
        throw new Error('Unable to clear cart.')
      }

      setCart((currentCart) => ({ ...currentCart, items: [], totalAmount: 0 }))
    } catch {
      setError('Unable to clear your cart. Please try again.')
    } finally {
      setLoadingAction('')
    }
  }

  function updateDeliveryDetails(event) {
    const { name, value } = event.target
    setDeliveryDetails((current) => ({ ...current, [name]: value }))
  }

  async function placeOrder(event) {
    event.preventDefault()
    if (!cartItems.length || loadingAction) {
      return
    }

    const token = getToken()
    if (!token) {
      setError('Please log in to place your order.')
      return
    }

    setError('')
    setCreatedOrder(null)
    setLoadingAction('place-order')
    try {
      const response = await fetch(ORDERS_URL, {
        method: 'POST',
        headers: getAuthHeaders(token),
        body: JSON.stringify(Object.fromEntries(
          Object.entries(deliveryDetails).map(([key, value]) => [key, value.trim()]),
        )),
      })

      if (!response.ok) {
        throw new Error('Unable to place order.')
      }

      const orderResponse = await readResponse(response)
      setCreatedOrder(orderResponse)
      setCart((currentCart) => ({ ...currentCart, items: [], totalAmount: 0 }))
    } catch {
      setError('Unable to place your order. Please check your cart and try again.')
    } finally {
      setLoadingAction('')
    }
  }

  const cartItems = cart?.items || []

  return (
    <main className="cart-page">
      <section className="cart-header" aria-labelledby="cart-heading">
        <p className="eyebrow">Your selection</p>
        <h1 id="cart-heading">Cart</h1>
        <p className="cart-description">Review the items selected for your order.</p>
      </section>

      {isLoading && <p className="cart-message">Loading your cart...</p>}
      {!isLoading && error && <p className="cart-message cart-error" role="alert">{error}</p>}
      {!isLoading && !error && createdOrder && (
        <div className="cart-message cart-success" role="status">
          <strong>Order placed successfully.</strong>
          <span>
            Order #{createdOrder.orderId} was created for {rupeeFormatter.format(Number(createdOrder.totalAmount) || 0)}.
          </span>
          <span>Delivery for {createdOrder.recipientName} to {createdOrder.city}, {createdOrder.state} {createdOrder.postalCode}.</span>
          <Link className="view-order-link" to="/orders">View your order</Link>
        </div>
      )}
      {!isLoading && !error && !createdOrder && cartItems.length === 0 && (
        <p className="cart-message">Your cart is empty. Products you select will appear here.</p>
      )}

      {!isLoading && !error && cartItems.length > 0 && (
        <section className="cart-layout" aria-label="Shopping cart">
          <div className="cart-items">
            {cartItems.map((item) => {
              const isUpdating = loadingAction === `update-${item.id}`
              const isRemoving = loadingAction === `remove-${item.id}`
              const isBusy = Boolean(loadingAction)

              return (
                <article className="cart-item" key={item.id}>
                  <div className="cart-item-main">
                    <h2>{item.productName}</h2>
                    <span>{rupeeFormatter.format(Number(item.price) || 0)} per unit</span>
                  </div>
                  <div className="cart-item-controls">
                    <div className="quantity-control" aria-label={`Quantity for ${item.productName}`}>
                      <button
                        type="button"
                        onClick={() => updateCartItem(item, item.quantity - 1)}
                        disabled={item.quantity <= 1 || isBusy}
                        aria-label={`Decrease ${item.productName} quantity`}
                      >
                        -
                      </button>
                      <span>{isUpdating ? '...' : item.quantity}</span>
                      <button
                        type="button"
                        onClick={() => updateCartItem(item, item.quantity + 1)}
                        disabled={isBusy}
                        aria-label={`Increase ${item.productName} quantity`}
                      >
                        +
                      </button>
                    </div>
                    <strong>{rupeeFormatter.format(Number(item.subtotal) || 0)}</strong>
                    <button className="remove-button" type="button" onClick={() => removeCartItem(item.id)} disabled={isBusy}>
                      {isRemoving ? 'Removing...' : 'Remove'}
                    </button>
                  </div>
                </article>
              )
            })}
          </div>

          <aside className="cart-summary" aria-label="Cart summary">
            <span className="product-detail-label">Total amount</span>
            <strong>{rupeeFormatter.format(Number(cart.totalAmount) || 0)}</strong>
            <form className="delivery-details-form" onSubmit={placeOrder}>
              <h2>Delivery details</h2>
              <label htmlFor="delivery-recipient">Recipient name</label>
              <input id="delivery-recipient" name="recipientName" autoComplete="name" maxLength="120" required value={deliveryDetails.recipientName} onChange={updateDeliveryDetails} />

              <label htmlFor="delivery-phone">Phone number</label>
              <input id="delivery-phone" name="phoneNumber" type="tel" autoComplete="tel" inputMode="tel" pattern="\+?[1-9][0-9]{7,14}" title="Enter 8 to 15 digits, optionally starting with +" maxLength="16" required value={deliveryDetails.phoneNumber} onChange={updateDeliveryDetails} />
              <small>Enter 8 to 15 digits, optionally starting with +.</small>

              <label htmlFor="delivery-address-line-1">Address line 1</label>
              <input id="delivery-address-line-1" name="addressLine1" autoComplete="address-line1" maxLength="255" required value={deliveryDetails.addressLine1} onChange={updateDeliveryDetails} />

              <label htmlFor="delivery-address-line-2">Address line 2 (optional)</label>
              <input id="delivery-address-line-2" name="addressLine2" autoComplete="address-line2" maxLength="255" value={deliveryDetails.addressLine2} onChange={updateDeliveryDetails} />

              <div className="delivery-field-grid">
                <div>
                  <label htmlFor="delivery-city">City</label>
                  <input id="delivery-city" name="city" autoComplete="address-level2" maxLength="100" required value={deliveryDetails.city} onChange={updateDeliveryDetails} />
                </div>
                <div>
                  <label htmlFor="delivery-state">State / region</label>
                  <input id="delivery-state" name="state" autoComplete="address-level1" maxLength="100" required value={deliveryDetails.state} onChange={updateDeliveryDetails} />
                </div>
                <div>
                  <label htmlFor="delivery-postal-code">Postal code</label>
                  <input id="delivery-postal-code" name="postalCode" autoComplete="postal-code" pattern="[A-Za-z0-9][A-Za-z0-9 -]{1,10}[A-Za-z0-9]" title="Enter 3 to 12 letters, digits, spaces, or hyphens" maxLength="12" required value={deliveryDetails.postalCode} onChange={updateDeliveryDetails} />
                </div>
                <div>
                  <label htmlFor="delivery-country">Country</label>
                  <input id="delivery-country" name="country" autoComplete="country-name" maxLength="100" required value={deliveryDetails.country} onChange={updateDeliveryDetails} />
                </div>
              </div>

              <button className="place-order-button" type="submit" disabled={Boolean(loadingAction)}>
                {loadingAction === 'place-order' ? 'Placing Order...' : 'Place Order'}
              </button>
            </form>
            <button className="clear-cart-button" type="button" onClick={clearCart} disabled={Boolean(loadingAction)}>
              {loadingAction === 'clear' ? 'Clearing...' : 'Clear Cart'}
            </button>
          </aside>
        </section>
      )}
    </main>
  )
}

export default Cart
