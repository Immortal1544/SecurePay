import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { loginUser } from '../services/authService'

function Login() {
  const navigate = useNavigate()
  const [formData, setFormData] = useState({ email: '', password: '' })
  const [error, setError] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)

  function handleChange(event) {
    const { name, value } = event.target
    setFormData((currentData) => ({ ...currentData, [name]: value }))
  }

  async function handleSubmit(event) {
    event.preventDefault()
    setError('')

    if (!formData.email.trim() || !formData.password) {
      setError('Enter your email and password to continue.')
      return
    }

    setIsSubmitting(true)
    try {
      const response = await loginUser({
        email: formData.email.trim(),
        password: formData.password,
      })

      localStorage.setItem('securepay_token', response.token)
      localStorage.setItem('securepay_user', JSON.stringify({
        userId: response.userId,
        name: response.name,
        email: response.email,
        role: response.role,
      }))
      navigate('/')
    } catch (requestError) {
      setError(requestError.message || 'Login failed. Please try again.')
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <main className="auth-page">
      <section className="auth-card" aria-labelledby="login-heading">
        <p className="eyebrow">Welcome back</p>
        <h1 id="login-heading">Sign in to SecurePay</h1>
        <p className="auth-description">Access your payment and order workspace.</p>

        <form className="auth-form" onSubmit={handleSubmit}>
          <label htmlFor="login-email">Email</label>
          <input
            id="login-email"
            name="email"
            type="email"
            autoComplete="email"
            value={formData.email}
            onChange={handleChange}
            required
          />

          <label htmlFor="login-password">Password</label>
          <input
            id="login-password"
            name="password"
            type="password"
            autoComplete="current-password"
            value={formData.password}
            onChange={handleChange}
            required
          />

          {error && <p className="form-error" role="alert">{error}</p>}
          <button className="form-submit" type="submit" disabled={isSubmitting}>
            {isSubmitting ? 'Signing in...' : 'Login'}
          </button>
        </form>
      </section>
    </main>
  )
}

export default Login