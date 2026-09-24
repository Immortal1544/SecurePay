import { Link, useLocation } from 'react-router-dom'
import { getCurrentUser, getToken } from '../services/authService'

function Navbar() {
  const location = useLocation()
  const currentUser = getCurrentUser()
  const isAdmin = Boolean(getToken() && currentUser?.role === 'ADMIN')

  return (
    <header className="site-header">
      <nav className="navbar" aria-label="Primary navigation">
        <Link className="brand" to="/" aria-label="SecurePay home">
          <span className="brand-mark" aria-hidden="true">S</span>
          <span>SecurePay</span>
        </Link>

        <div className="nav-links">
          <Link className="nav-link active" to="/">Home</Link>
          <Link className="nav-link" to="/login">Login</Link>
          <Link className="nav-link nav-link-highlight" to="/register">Register</Link>
          <Link className="nav-link" to="/products">Products</Link>
          <Link className="nav-link" to="/cart">Cart</Link>
          <Link className="nav-link" to="/orders">Orders</Link>
          {isAdmin && <Link className={`nav-link${location.pathname.startsWith('/admin') ? ' active' : ''}`} to="/admin">Admin</Link>}
        </div>
      </nav>
    </header>
  )
}

export default Navbar
