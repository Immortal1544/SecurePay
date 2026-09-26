import { useEffect, useState } from 'react'
import { getToken } from '../services/authService'
import { API_BASE_URL } from '../services/apiConfig'

const PRODUCTS_URL = `${API_BASE_URL}/api/products`
const CART_ITEMS_URL = `${API_BASE_URL}/api/cart/items`
const rupeeFormatter = new Intl.NumberFormat('en-IN', {
  style: 'currency',
  currency: 'INR',
  maximumFractionDigits: 2,
})

function Products() {
  const [products, setProducts] = useState([])
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState('')
  const [addingProductId, setAddingProductId] = useState(null)
  const [cartFeedback, setCartFeedback] = useState({ productId: null, type: '', message: '' })

  useEffect(() => {
    async function fetchProducts() {
      const token = getToken()

      if (!token) {
        setError('Please log in to view the product catalog.')
        setIsLoading(false)
        return
      }

      try {
        const response = await fetch(PRODUCTS_URL, {
          headers: {
            Authorization: `Bearer ${token}`,
            'Content-Type': 'application/json',
          },
        })

        let responseData = null
        try {
          responseData = await response.json()
        } catch {
          responseData = null
        }

        if (!response.ok) {
          throw new Error(responseData?.message || responseData?.error || 'Unable to load products.')
        }

        setProducts(Array.isArray(responseData) ? responseData : [])
      } catch (requestError) {
        setError(requestError.message || 'Unable to load products. Please try again.')
      } finally {
        setIsLoading(false)
      }
    }

    fetchProducts()
  }, [])

  async function addToCart(product) {
    if (!product.active || Number(product.stockQuantity) <= 0 || addingProductId !== null) {
      return
    }

    const token = getToken()
    if (!token) {
      setCartFeedback({
        productId: product.id,
        type: 'error',
        message: 'Please log in to add products to your cart.',
      })
      return
    }

    setCartFeedback({ productId: null, type: '', message: '' })
    setAddingProductId(product.id)
    try {
      const response = await fetch(CART_ITEMS_URL, {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${token}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          productId: product.id,
          quantity: 1,
        }),
      })

      if (!response.ok) {
        throw new Error('Unable to add product to cart.')
      }

      setCartFeedback({ productId: product.id, type: 'success', message: 'Added to cart' })
    } catch {
      setCartFeedback({
        productId: product.id,
        type: 'error',
        message: 'Unable to add this product. Please try again.',
      })
    } finally {
      setAddingProductId(null)
    }
  }

  return (
    <main className="products-page">
      <section className="products-header" aria-labelledby="products-heading">
        <p className="eyebrow">SecurePay catalog</p>
        <h1 id="products-heading">Products</h1>
        <p className="products-description">Browse the products currently available in your workspace.</p>
      </section>

      {isLoading && <p className="products-message">Loading products...</p>}
      {!isLoading && error && <p className="products-message products-error" role="alert">{error}</p>}
      {!isLoading && !error && products.length === 0 && (
        <p className="products-message">There are no products to display yet.</p>
      )}

      {!isLoading && !error && products.length > 0 && (
        <section className="product-grid" aria-label="Product catalog">
          {products.map((product) => (
            <article className={`product-card${product.active ? '' : ' product-card-inactive'}`} key={product.id}>
              <div className="product-card-header">
                <h2>{product.name}</h2>
                <span className={`product-status${product.active ? '' : ' product-status-inactive'}`}>
                  {product.active ? 'Active' : 'Inactive'}
                </span>
              </div>
              <p className="product-description">{product.description || 'No description available.'}</p>
              <div className="product-details">
                <div>
                  <span className="product-detail-label">Price</span>
                  <strong>{rupeeFormatter.format(Number(product.price) || 0)}</strong>
                </div>
                <div>
                  <span className="product-detail-label">Stock</span>
                  <strong>{product.stockQuantity ?? 0} units</strong>
                </div>
              </div>
              {product.active && Number(product.stockQuantity) > 0 && (
                <button
                  className="add-to-cart-button"
                  type="button"
                  onClick={() => addToCart(product)}
                  disabled={addingProductId !== null}
                >
                  {addingProductId === product.id ? 'Adding...' : 'Add to Cart'}
                </button>
              )}
              {product.active && Number(product.stockQuantity) <= 0 && (
                <p className="product-unavailable">Out of stock</p>
              )}
              {!product.active && <p className="product-unavailable">Currently unavailable</p>}
              {cartFeedback.productId === product.id && (
                <p className={`cart-feedback cart-feedback-${cartFeedback.type}`} role="status">
                  {cartFeedback.message}
                </p>
              )}
            </article>
          ))}
        </section>
      )}
    </main>
  )
}

export default Products
