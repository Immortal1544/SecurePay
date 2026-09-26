import { Link, useLocation, useNavigate } from 'react-router-dom'
import { getCurrentUser, getToken, logoutUser } from '../services/authService'

function Navbar() {
  const location = useLocation()
  const navigate = useNavigate()
  const currentUser = getCurrentUser()
  const isAuthenticated = Boolean(getToken() && currentUser)
  const isAdmin = Boolean(isAuthenticated && currentUser?.role === 'ADMIN')

  function handleLogout() {
    logoutUser()
    navigate('/login')
  }

  return (
    <header className="site-header">
      <nav className="navbar" aria-label="Primary navigation">
        <Link className="brand" to="/" aria-label="SecurePay home">
          <span className="brand-mark" aria-hidden="true">S</span>
          <span>SecurePay</span>
        </Link>

        <div className="nav-links">
          <Link className="nav-link" to="/">Home</Link>

          {!isAuthenticated && (
            <>
              <Link className="nav-link" to="/login">Login</Link>
              <Link className="nav-link nav-link-highlight" to="/register">
                Register
              </Link>
            </>
          )}

          <Link className="nav-link" to="/products">Products</Link>

          {isAuthenticated && (
            <>
              <Link className="nav-link" to="/cart">Cart</Link>
              <Link className="nav-link" to="/orders">Orders</Link>

              {isAdmin && (
                <Link
                  className={`nav-link${location.pathname.startsWith('/admin') ? ' active' : ''}`}
                  to="/admin"
                >
                  Admin
                </Link>
              )}

              <button
                type="button"
                className="nav-link nav-button"
                onClick={handleLogout}
              >
                Logout
              </button>
            </>
          )}
        </div>
      </nav>
    </header>
  )
}

export default Navbar