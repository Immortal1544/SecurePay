import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { getToken } from '../services/authService'

const API_BASE_URL = 'http://localhost:8080'
const PRODUCT_URL = `${API_BASE_URL}/api/products`
const ADMIN_ORDER_URL = `${API_BASE_URL}/api/admin/orders`
const ADMIN_PAYMENT_URL = `${API_BASE_URL}/api/payments/admin/orders`
const EMPTY_PRODUCT = {
  name: '',
  description: '',
  price: '',
  stockQuantity: '',
  active: true,
}
const NEXT_ORDER_STATUSES = {
  CREATED: ['PAYMENT_PENDING', 'CANCELLED'],
  PAYMENT_PENDING: ['CANCELLED'],
  PAID: ['PROCESSING', 'CANCELLED'],
  PROCESSING: ['SHIPPED', 'CANCELLED'],
  SHIPPED: ['DELIVERED'],
  DELIVERED: [],
  CANCELLED: [],
}
const rupeeFormatter = new Intl.NumberFormat('en-IN', {
  style: 'currency',
  currency: 'INR',
  maximumFractionDigits: 2,
})
const dateFormatter = new Intl.DateTimeFormat('en-IN', {
  dateStyle: 'medium',
  timeStyle: 'short',
})

async function requestJson(url, options = {}) {
  const token = getToken()
  if (!token) {
    throw new Error('Your session is unavailable. Please log in again.')
  }

  const headers = {
    Authorization: `Bearer ${token}`,
    ...options.headers,
  }
  if (options.body) {
    headers['Content-Type'] = 'application/json'
  }

  const response = await fetch(url, { ...options, headers })
  let data = null
  try {
    data = await response.json()
  } catch {
    data = null
  }

  if (!response.ok) {
    if (response.status === 401) {
      throw new Error('Your session has expired. Please log in again.')
    }
    if (response.status === 403) {
      throw new Error('Your account is not authorized to use the admin console.')
    }
    throw new Error(data?.message || 'The request could not be completed. Please try again.')
  }

  return data
}

function formatDate(value) {
  if (!value) {
    return 'Date unavailable'
  }

  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? 'Date unavailable' : dateFormatter.format(date)
}

function formatStatus(status) {
  return status ? status.replaceAll('_', ' ') : 'Unknown'
}

function formatDeliveryAddress(order) {
  const cityRegion = [order.city, order.state, order.postalCode].filter(Boolean).join(', ')
  return [order.addressLine1, order.addressLine2, cityRegion, order.country].filter(Boolean).join(', ')
}

function AdminDashboard() {
  const [products, setProducts] = useState([])
  const [orders, setOrders] = useState([])
  const [productError, setProductError] = useState('')
  const [orderError, setOrderError] = useState('')
  const [productNotice, setProductNotice] = useState('')
  const [orderNotice, setOrderNotice] = useState('')
  const [isLoadingProducts, setIsLoadingProducts] = useState(true)
  const [isLoadingOrders, setIsLoadingOrders] = useState(true)
  const [productForm, setProductForm] = useState(EMPTY_PRODUCT)
  const [editingProductId, setEditingProductId] = useState(null)
  const [isSavingProduct, setIsSavingProduct] = useState(false)
  const [deactivatingProductId, setDeactivatingProductId] = useState(null)
  const [selectedOrderId, setSelectedOrderId] = useState(null)
  const [orderDetails, setOrderDetails] = useState(null)
  const [orderPayment, setOrderPayment] = useState(null)
  const [paymentLookupError, setPaymentLookupError] = useState('')
  const [isLoadingOrderDetails, setIsLoadingOrderDetails] = useState(false)
  const [updatingOrderId, setUpdatingOrderId] = useState(null)
  const [refundingOrderId, setRefundingOrderId] = useState(null)

  const loadProducts = useCallback(async () => {
    try {
      const data = await requestJson(PRODUCT_URL)
      setProducts(Array.isArray(data) ? data : [])
    } catch (error) {
      setProductError(error.message || 'Unable to load products.')
    } finally {
      setIsLoadingProducts(false)
    }
  }, [])

  const loadOrders = useCallback(async () => {
    try {
      const data = await requestJson(ADMIN_ORDER_URL)
      setOrders(Array.isArray(data) ? data : [])
    } catch (error) {
      setOrderError(error.message || 'Unable to load orders.')
    } finally {
      setIsLoadingOrders(false)
    }
  }, [])

  useEffect(() => {
    let isMounted = true
    async function loadDashboard() {
      await Promise.resolve()
      if (isMounted) {
        loadProducts()
        loadOrders()
      }
    }

    loadDashboard()
    return () => {
      isMounted = false
    }
  }, [loadProducts, loadOrders])

  function updateProductForm(event) {
    const { name, value, type, checked } = event.target
    setProductForm((current) => ({
      ...current,
      [name]: type === 'checkbox' ? checked : value,
    }))
  }

  function startEditingProduct(product) {
    setProductError('')
    setProductNotice('')
    setEditingProductId(product.id)
    setProductForm({
      name: product.name || '',
      description: product.description || '',
      price: product.price ?? '',
      stockQuantity: product.stockQuantity ?? '',
      active: Boolean(product.active),
    })
    document.getElementById('admin-product-form')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }

  function resetProductForm() {
    setEditingProductId(null)
    setProductForm(EMPTY_PRODUCT)
    setProductError('')
    setProductNotice('')
  }

  async function saveProduct(event) {
    event.preventDefault()
    setProductError('')
    setProductNotice('')
    setIsSavingProduct(true)

    const payload = {
      name: productForm.name.trim(),
      description: productForm.description.trim(),
      price: Number(productForm.price),
      stockQuantity: Number(productForm.stockQuantity),
      active: productForm.active,
    }

    try {
      await requestJson(
        editingProductId === null ? PRODUCT_URL : `${PRODUCT_URL}/${editingProductId}`,
        {
          method: editingProductId === null ? 'POST' : 'PUT',
          body: JSON.stringify(payload),
        },
      )
      setProductNotice(editingProductId === null ? 'Product created.' : 'Product updated.')
      setEditingProductId(null)
      setProductForm(EMPTY_PRODUCT)
      await loadProducts()
    } catch (error) {
      setProductError(error.message || 'Unable to save this product.')
    } finally {
      setIsSavingProduct(false)
    }
  }

  async function deactivateProduct(product) {
    if (!product.active || deactivatingProductId !== null) {
      return
    }

    setProductError('')
    setProductNotice('')
    setDeactivatingProductId(product.id)
    try {
      await requestJson(`${PRODUCT_URL}/${product.id}`, { method: 'DELETE' })
      setProductNotice(`${product.name} was deactivated.`)
      await loadProducts()
    } catch (error) {
      setProductError(error.message || 'Unable to deactivate this product.')
    } finally {
      setDeactivatingProductId(null)
    }
  }

  async function toggleOrderDetails(orderId) {
    if (selectedOrderId === orderId) {
      setSelectedOrderId(null)
      setOrderDetails(null)
      setOrderPayment(null)
      setPaymentLookupError('')
      return
    }

    setSelectedOrderId(orderId)
    setOrderDetails(null)
    setOrderPayment(null)
    setPaymentLookupError('')
    setOrderError('')
    setIsLoadingOrderDetails(true)
    try {
      const data = await requestJson(`${ADMIN_ORDER_URL}/${orderId}`)
      setOrderDetails(data)
      try {
        const payment = await requestJson(`${ADMIN_PAYMENT_URL}/${orderId}`)
        setOrderPayment(payment)
      } catch (error) {
        if (!error.message?.includes('not found')) {
          setPaymentLookupError(error.message || 'Unable to load payment status.')
        }
      }
    } catch (error) {
      setOrderError(error.message || 'Unable to load order details.')
    } finally {
      setIsLoadingOrderDetails(false)
    }
  }

  async function refundOrder(order) {
    if (refundingOrderId !== null || orderPayment?.status !== 'SUCCESS') {
      return
    }
    const confirmed = window.confirm(
      `Refund ${rupeeFormatter.format(Number(orderPayment.amount) || 0)} for order #${order.orderId}? This action cannot be undone.`,
    )
    if (!confirmed) {
      return
    }

    setOrderError('')
    setOrderNotice('')
    setRefundingOrderId(order.orderId)
    try {
      const refundedPayment = await requestJson(`${API_BASE_URL}/api/payments/orders/${order.orderId}/refund`, {
        method: 'POST',
      })
      setOrderPayment(refundedPayment)
      setOrderNotice(`Order #${order.orderId} was refunded.`)
      await loadOrders()
      try {
        const refreshedOrder = await requestJson(`${ADMIN_ORDER_URL}/${order.orderId}`)
        setOrderDetails(refreshedOrder)
      } catch (error) {
        setOrderError(`Refund completed, but the order details could not be refreshed. ${error.message || ''}`.trim())
      }
    } catch (error) {
      setOrderError(error.message || 'Unable to refund this payment.')
    } finally {
      setRefundingOrderId(null)
    }
  }

  async function updateOrderStatus(orderId, status) {
    setOrderError('')
    setOrderNotice('')
    setUpdatingOrderId(orderId)
    try {
      const updatedOrder = await requestJson(`${ADMIN_ORDER_URL}/${orderId}/status`, {
        method: 'PUT',
        body: JSON.stringify({ status }),
      })
      setOrders((current) => current.map((order) => (
        order.orderId === orderId ? { ...order, ...updatedOrder } : order
      )))
      if (selectedOrderId === orderId) {
        setOrderDetails((current) => current ? { ...current, ...updatedOrder } : current)
      }
      setOrderNotice(`Order #${orderId} updated to ${formatStatus(status)}.`)
    } catch (error) {
      setOrderError(error.message || 'Unable to update this order status.')
      await loadOrders()
    } finally {
      setUpdatingOrderId(null)
    }
  }

  return (
    <main className="admin-page">
      <header className="admin-header">
        <div>
          <p className="eyebrow">SecurePay operations</p>
          <h1>Admin Console</h1>
          <p className="admin-description">Manage the catalog and keep orders moving through fulfillment.</p>
        </div>
        <Link className="admin-back-link" to="/products">View customer catalog</Link>
      </header>

      <section className="admin-section" aria-labelledby="admin-products-heading">
        <div className="admin-section-heading">
          <div>
            <p className="eyebrow">Catalog</p>
            <h2 id="admin-products-heading">Product management</h2>
          </div>
          <span className="admin-count">{products.length} products</span>
        </div>

        <div className="admin-product-layout">
          <form id="admin-product-form" className="admin-card admin-product-form" onSubmit={saveProduct}>
            <div className="admin-card-heading">
              <h3>{editingProductId === null ? 'Create product' : `Edit product #${editingProductId}`}</h3>
              {editingProductId !== null && (
                <button className="admin-text-button" type="button" onClick={resetProductForm}>Cancel edit</button>
              )}
            </div>
            <label htmlFor="admin-product-name">Name</label>
            <input id="admin-product-name" name="name" value={productForm.name} onChange={updateProductForm} required maxLength={120} />
            <label htmlFor="admin-product-description">Description</label>
            <textarea id="admin-product-description" name="description" value={productForm.description} onChange={updateProductForm} rows="3" />
            <div className="admin-form-grid">
              <div>
                <label htmlFor="admin-product-price">Price (INR)</label>
                <input id="admin-product-price" name="price" type="number" min="0.01" step="0.01" value={productForm.price} onChange={updateProductForm} required />
              </div>
              <div>
                <label htmlFor="admin-product-stock">Stock quantity</label>
                <input id="admin-product-stock" name="stockQuantity" type="number" min="0" step="1" value={productForm.stockQuantity} onChange={updateProductForm} required />
              </div>
            </div>
            <label className="admin-checkbox-label" htmlFor="admin-product-active">
              <input id="admin-product-active" name="active" type="checkbox" checked={productForm.active} onChange={updateProductForm} />
              Product is active
            </label>
            {productError && <p className="admin-message admin-error" role="alert">{productError}</p>}
            {productNotice && <p className="admin-message admin-success" role="status">{productNotice}</p>}
            <button className="admin-primary-button" type="submit" disabled={isSavingProduct}>
              {isSavingProduct ? 'Saving...' : editingProductId === null ? 'Create product' : 'Save changes'}
            </button>
          </form>

          <div className="admin-product-list" aria-label="Products">
            {isLoadingProducts && <p className="admin-card admin-empty">Loading products...</p>}
            {!isLoadingProducts && !productError && products.length === 0 && (
              <p className="admin-card admin-empty">No products found.</p>
            )}
            {!isLoadingProducts && products.map((product) => (
              <article className="admin-card admin-product-row" key={product.id}>
                <div className="admin-row-main">
                  <div className="admin-card-heading">
                    <h3>{product.name}</h3>
                    <span className={`admin-status ${product.active ? 'admin-status-active' : 'admin-status-inactive'}`}>
                      {product.active ? 'Active' : 'Inactive'}
                    </span>
                  </div>
                  <p>{product.description || 'No description'}</p>
                  <div className="admin-product-meta">
                    <span>{rupeeFormatter.format(Number(product.price) || 0)}</span>
                    <span>{product.stockQuantity ?? 0} in stock</span>
                  </div>
                </div>
                <div className="admin-row-actions">
                  <button className="admin-secondary-button" type="button" onClick={() => startEditingProduct(product)}>Edit</button>
                  <button
                    className="admin-danger-button"
                    type="button"
                    onClick={() => deactivateProduct(product)}
                    disabled={!product.active || deactivatingProductId !== null}
                  >
                    {deactivatingProductId === product.id ? 'Deactivating...' : 'Deactivate'}
                  </button>
                </div>
              </article>
            ))}
          </div>
        </div>
      </section>

      <section className="admin-section" aria-labelledby="admin-orders-heading">
        <div className="admin-section-heading">
          <div>
            <p className="eyebrow">Fulfillment</p>
            <h2 id="admin-orders-heading">Order management</h2>
          </div>
          <span className="admin-count">{orders.length} orders</span>
        </div>

        {orderError && <p className="admin-message admin-error" role="alert">{orderError}</p>}
        {orderNotice && <p className="admin-message admin-success" role="status">{orderNotice}</p>}
        {isLoadingOrders && <p className="admin-card admin-empty">Loading orders...</p>}
        {!isLoadingOrders && orders.length === 0 && !orderError && (
          <p className="admin-card admin-empty">No orders found.</p>
        )}
        {!isLoadingOrders && orders.length > 0 && (
          <div className="admin-order-list">
            {orders.map((order) => {
              const expanded = selectedOrderId === order.orderId
              const displayedOrder = expanded && orderDetails ? orderDetails : order
              const nextStatuses = NEXT_ORDER_STATUSES[order.status] || []

              return (
                <article className="admin-card admin-order-card" key={order.orderId}>
                  <div className="admin-order-summary">
                    <div>
                      <span className="admin-label">Order</span>
                      <h3>#{order.orderId}</h3>
                    </div>
                    <div>
                      <span className="admin-label">Total</span>
                      <strong>{rupeeFormatter.format(Number(order.totalAmount) || 0)}</strong>
                    </div>
                    <div>
                      <span className="admin-label">Created</span>
                      <strong>{formatDate(order.createdAt)}</strong>
                    </div>
                    <div>
                      <span className="admin-label">Status</span>
                      <span className="admin-status admin-status-order">{formatStatus(order.status)}</span>
                    </div>
                    <div className="admin-row-actions">
                      <button className="admin-secondary-button" type="button" onClick={() => toggleOrderDetails(order.orderId)} disabled={isLoadingOrderDetails && expanded}>
                        {isLoadingOrderDetails && expanded ? 'Loading...' : expanded ? 'Hide details' : 'View details'}
                      </button>
                    </div>
                  </div>

                  {expanded && orderDetails && (
                    <div className="admin-order-details">
                      <h4>Items</h4>
                      {displayedOrder.items?.length ? displayedOrder.items.map((item) => (
                        <div className="admin-order-item" key={item.id}>
                          <span>{item.productName} - {item.quantity} x {rupeeFormatter.format(Number(item.unitPrice) || 0)}</span>
                          <strong>{rupeeFormatter.format(Number(item.subtotal) || 0)}</strong>
                        </div>
                      )) : <p>No item details returned for this order.</p>}
                      <div className="admin-delivery-details">
                        <h4>Delivery details</h4>
                        {displayedOrder.recipientName ? (
                          <>
                            <strong>{displayedOrder.recipientName} · {displayedOrder.phoneNumber}</strong>
                            <span>{formatDeliveryAddress(displayedOrder)}</span>
                          </>
                        ) : <p>Delivery details are unavailable for this order.</p>}
                      </div>
                      <div className="admin-order-controls">
                        <span className="admin-label">Payment</span>
                        {orderPayment ? (
                          <span className="admin-status admin-status-order">{formatStatus(orderPayment.status)}</span>
                        ) : <span className="admin-terminal-status">{paymentLookupError || 'No payment recorded'}</span>}
                        {orderPayment?.status === 'SUCCESS'
                          && ['PAID', 'PROCESSING', 'CANCELLED'].includes(order.status)
                          && (
                            <button
                              className="admin-danger-button"
                              type="button"
                              onClick={() => refundOrder(order)}
                              disabled={refundingOrderId !== null}
                            >
                              {refundingOrderId === order.orderId ? 'Refunding...' : 'Refund payment'}
                            </button>
                          )}
                        {refundingOrderId === order.orderId && <span className="admin-inline-progress">Refunding...</span>}
                      </div>
                    </div>
                  )}

                  <div className="admin-order-controls">
                    <span className="admin-label">Allowed next status</span>
                    {nextStatuses.length > 0 ? (
                      <select
                        aria-label={`Update order ${order.orderId} status`}
                        value=""
                        onChange={(event) => {
                          if (event.target.value) {
                            updateOrderStatus(order.orderId, event.target.value)
                          }
                        }}
                        disabled={updatingOrderId !== null}
                      >
                        <option value="">Select a transition</option>
                        {nextStatuses.map((status) => <option value={status} key={status}>{formatStatus(status)}</option>)}
                      </select>
                    ) : <span className="admin-terminal-status">No further transition</span>}
                    {updatingOrderId === order.orderId && <span className="admin-inline-progress">Updating...</span>}
                  </div>
                </article>
              )
            })}
          </div>
        )}
      </section>
    </main>
  )
}

export default AdminDashboard
