import { useEffect, useState } from 'react'
import { getToken } from '../services/authService'

const ORDERS_URL = 'http://localhost:8080/api/orders'
const PAYMENTS_URL = 'http://localhost:8080/api/payments'
const RAZORPAY_SCRIPT_URL = 'https://checkout.razorpay.com/v1/checkout.js'
const rupeeFormatter = new Intl.NumberFormat('en-IN', {
  style: 'currency',
  currency: 'INR',
  maximumFractionDigits: 2,
})
const dateFormatter = new Intl.DateTimeFormat('en-IN', {
  dateStyle: 'medium',
  timeStyle: 'short',
})

function formatOrderDate(value) {
  if (!value) {
    return 'Date unavailable'
  }

  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? 'Date unavailable' : dateFormatter.format(date)
}

function formatStatus(status) {
  return status ? status.replaceAll('_', ' ') : 'Unknown'
}

async function requestJson(url, options) {
  const response = await fetch(url, options)
  let responseData = null

  try {
    responseData = await response.json()
  } catch {
    responseData = null
  }

  if (!response.ok) {
    throw new Error('Payment request failed.')
  }

  return responseData
}

function loadRazorpayScript() {
  if (window.Razorpay) {
    return Promise.resolve()
  }

  const existingScript = document.querySelector(`script[src="${RAZORPAY_SCRIPT_URL}"]`)
  if (existingScript) {
    return new Promise((resolve, reject) => {
      existingScript.addEventListener('load', resolve, { once: true })
      existingScript.addEventListener('error', () => reject(new Error('Checkout could not be loaded.')), { once: true })
    })
  }

  return new Promise((resolve, reject) => {
    const script = document.createElement('script')
    script.src = RAZORPAY_SCRIPT_URL
    script.async = true
    script.onload = resolve
    script.onerror = () => reject(new Error('Checkout could not be loaded.'))
    document.body.appendChild(script)
  })
}

function Orders() {
  const [orders, setOrders] = useState([])
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState('')
  const [selectedOrderId, setSelectedOrderId] = useState(null)
  const [orderDetails, setOrderDetails] = useState(null)
  const [isLoadingDetails, setIsLoadingDetails] = useState(false)
  const [detailsError, setDetailsError] = useState('')
  const [processingPaymentOrderId, setProcessingPaymentOrderId] = useState(null)
  const [paymentMessage, setPaymentMessage] = useState('')
  const [paymentError, setPaymentError] = useState('')

  async function loadOrders() {
    const token = getToken()

    if (!token) {
      throw new Error('Please log in to view your orders.')
    }

    const response = await fetch(ORDERS_URL, {
      headers: {
        Authorization: `Bearer ${token}`,
      },
    })

    if (!response.ok) {
      throw new Error('Unable to load orders.')
    }

    const responseData = await response.json()
    setOrders(Array.isArray(responseData) ? responseData : [])
  }

  useEffect(() => {
    async function fetchOrders() {
      try {
        await loadOrders()
      } catch {
        setError('Unable to load your orders. Please try again.')
      } finally {
        setIsLoading(false)
      }
    }

    fetchOrders()
  }, [])

  function openRazorpayCheckout(orderId, paymentOrder, token) {
    if (!window.Razorpay) {
      throw new Error('Checkout is unavailable.')
    }

    const amountInPaise = Math.round(Number(paymentOrder.amount) * 100)
    if (!Number.isFinite(amountInPaise) || amountInPaise <= 0) {
      throw new Error('Invalid payment amount.')
    }

    return new Promise((resolve, reject) => {
      let settled = false
      const finishWithError = () => {
        if (!settled) {
          settled = true
          reject(new Error('Payment was not completed.'))
        }
      }

      const checkout = new window.Razorpay({
        key: paymentOrder.razorpayKeyId,
        amount: amountInPaise,
        currency: paymentOrder.currency,
        name: 'SecurePay',
        description: `Payment for order #${orderId}`,
        order_id: paymentOrder.razorpayOrderId,
        handler: async (checkoutResponse) => {
          try {
            const verifiedPayment = await requestJson(`${PAYMENTS_URL}/orders/${orderId}/verify`, {
              method: 'POST',
              headers: {
                Authorization: `Bearer ${token}`,
                'Content-Type': 'application/json',
              },
              body: JSON.stringify({
                razorpayPaymentId: checkoutResponse.razorpay_payment_id,
                razorpayOrderId: checkoutResponse.razorpay_order_id,
                razorpaySignature: checkoutResponse.razorpay_signature,
              }),
            })

            if (!settled) {
              settled = true
              resolve(verifiedPayment)
            }
          } catch {
            finishWithError()
          }
        },
        modal: {
          ondismiss: finishWithError,
        },
      })

      checkout.on('payment.failed', finishWithError)
      checkout.open()
    })
  }

  async function payForOrder(orderId) {
    if (processingPaymentOrderId !== null) {
      return
    }

    const token = getToken()
    if (!token) {
      setPaymentError('Please log in to make a payment.')
      return
    }

    setPaymentMessage('')
    setPaymentError('')
    setProcessingPaymentOrderId(orderId)
    try {
      await requestJson(`${PAYMENTS_URL}/orders/${orderId}`, {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${token}`,
        },
      })

      const paymentOrder = await requestJson(`${PAYMENTS_URL}/orders/${orderId}/razorpay`, {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${token}`,
        },
      })

      await loadRazorpayScript()
      await openRazorpayCheckout(orderId, paymentOrder, token)
      setPaymentMessage(`Payment successful for order #${orderId}.`)
      setPaymentError('')
      setSelectedOrderId(null)
      setOrderDetails(null)
      try {
        await loadOrders()
      } catch {
        setPaymentError('Payment succeeded, but the order list could not be refreshed.')
      }
    } catch {
      setPaymentError('Payment could not be completed. Please try again.')
    } finally {
      setProcessingPaymentOrderId(null)
    }
  }

  async function viewOrderDetails(orderId) {
    if (selectedOrderId === orderId) {
      setSelectedOrderId(null)
      setOrderDetails(null)
      setDetailsError('')
      return
    }

    const token = getToken()
    if (!token) {
      setDetailsError('Please log in to view order details.')
      return
    }

    setSelectedOrderId(orderId)
    setOrderDetails(null)
    setDetailsError('')
    setIsLoadingDetails(true)
    try {
      const response = await fetch(`${ORDERS_URL}/${orderId}`, {
        headers: {
          Authorization: `Bearer ${token}`,
        },
      })

      if (!response.ok) {
        throw new Error('Unable to load order details.')
      }

      setOrderDetails(await response.json())
    } catch {
      setDetailsError('Unable to load these order details. Please try again.')
    } finally {
      setIsLoadingDetails(false)
    }
  }

  return (
    <main className="orders-page">
      <section className="orders-header" aria-labelledby="orders-heading">
        <p className="eyebrow">Your payment history</p>
        <h1 id="orders-heading">Orders</h1>
        <p className="orders-description">Review your past orders and their current status.</p>
      </section>

      {isLoading && <p className="orders-message">Loading your orders...</p>}
      {!isLoading && error && <p className="orders-message orders-error" role="alert">{error}</p>}
      {!isLoading && paymentMessage && <p className="orders-message payment-success" role="status">{paymentMessage}</p>}
      {!isLoading && paymentError && <p className="orders-message orders-error" role="alert">{paymentError}</p>}
      {!isLoading && !error && orders.length === 0 && (
        <p className="orders-message">You have no orders yet.</p>
      )}

      {!isLoading && !error && orders.length > 0 && (
        <section className="order-list" aria-label="Order history">
          {orders.map((order) => {
            const isSelected = selectedOrderId === order.orderId
            const displayedOrder = isSelected && orderDetails ? orderDetails : order
            const canPay = order.status === 'CREATED' || order.status === 'PAYMENT_PENDING'

            return (
              <article className="order-card" key={order.orderId}>
                <div className="order-card-header">
                  <div>
                    <p className="product-detail-label">Order ID</p>
                    <h2>#{order.orderId}</h2>
                  </div>
                  <span className="order-status">{formatStatus(displayedOrder.status)}</span>
                </div>

                <div className="order-meta">
                  <div>
                    <span className="product-detail-label">Placed</span>
                    <strong>{formatOrderDate(displayedOrder.createdAt)}</strong>
                  </div>
                  <div>
                    <span className="product-detail-label">Total</span>
                    <strong>{rupeeFormatter.format(Number(displayedOrder.totalAmount) || 0)}</strong>
                  </div>
                </div>

                <div className="order-items">
                  <span className="product-detail-label">Items</span>
                  {displayedOrder.items?.map((item) => (
                    <div className="order-item" key={item.id}>
                      <div>
                        <strong>{item.productName}</strong>
                        <span>Qty {item.quantity} at {rupeeFormatter.format(Number(item.unitPrice) || 0)} each</span>
                      </div>
                      <strong>{rupeeFormatter.format(Number(item.subtotal) || 0)}</strong>
                    </div>
                  ))}
                </div>

                <button className="view-details-button" type="button" onClick={() => viewOrderDetails(order.orderId)} disabled={isLoadingDetails && isSelected}>
                  {isLoadingDetails && isSelected ? 'Loading details...' : isSelected ? 'Hide Details' : 'View Details'}
                </button>

                {isSelected && isLoadingDetails && <p className="order-detail-message">Loading order details...</p>}
                {isSelected && detailsError && <p className="order-detail-message order-detail-error" role="alert">{detailsError}</p>}
                {canPay && (
                  <div className="order-actions">
                    <button
                      className="pay-now-button"
                      type="button"
                      onClick={() => payForOrder(order.orderId)}
                      disabled={processingPaymentOrderId !== null}
                    >
                      {processingPaymentOrderId === order.orderId ? 'Opening Checkout...' : 'Pay Now'}
                    </button>
                  </div>
                )}
              </article>
            )
          })}
        </section>
      )}
    </main>
  )
}

export default Orders