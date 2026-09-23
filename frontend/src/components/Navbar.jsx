import { Link } from 'react-router-dom'

function Navbar() {
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
        </div>
      </nav>
    </header>
  )
}

export default Navbar