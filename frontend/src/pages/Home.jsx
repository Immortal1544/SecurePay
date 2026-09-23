import { Link } from 'react-router-dom'

function Home() {
  return (
    <main className="home-page" id="home">
      <section className="welcome-section" aria-labelledby="welcome-heading">
        <div className="welcome-copy">
          <p className="eyebrow">Payment infrastructure, made clear</p>
          <h1 id="welcome-heading">Welcome to SecurePay</h1>
          <p className="welcome-description">
            SecurePay is a Payment &amp; Order Management System designed to keep
            every transaction, order, and customer touchpoint in one dependable place.
          </p>
          <Link className="primary-action" to="/register">Create an account <span aria-hidden="true">-&gt;</span></Link>
        </div>

        <div className="welcome-panel" aria-label="SecurePay overview">
          <div className="panel-topline">
            <span className="status-dot" aria-hidden="true"></span>
            <span>Workspace ready</span>
          </div>
          <div className="panel-line panel-line-main">
            <span>Secure checkout</span>
            <strong>Protected</strong>
          </div>
          <div className="panel-line">
            <span>Order visibility</span>
            <strong>Connected</strong>
          </div>
          <div className="panel-line">
            <span>Payment control</span>
            <strong>In progress</strong>
          </div>
        </div>
      </section>

      <section className="coming-section" aria-labelledby="coming-heading">
        <div>
          <p className="eyebrow">What comes next</p>
          <h2 id="coming-heading">One place for the whole payment journey.</h2>
        </div>
        <p className="coming-description">
          Products, cart, orders, and payments will be added here as the platform grows.
        </p>
        <div className="feature-list" aria-label="Planned modules">
          <span>Products</span>
          <span>Cart</span>
          <span>Orders</span>
          <span>Payments</span>
        </div>
      </section>
    </main>
  )
}

export default Home