import { BrowserRouter, Navigate, Route, Routes, useLocation } from 'react-router-dom'
import Navbar from './components/Navbar'
import { getCurrentUser, getToken } from './services/authService'
import AdminDashboard from './pages/AdminDashboard'
import Cart from './pages/Cart'
import Home from './pages/Home'
import Login from './pages/Login'
import Orders from './pages/Orders'
import Products from './pages/Products'
import Register from './pages/Register'
import './App.css'

function AdminRoute({ children }) {
  const location = useLocation()
  const user = getCurrentUser()

  if (!getToken() || !user) {
    return <Navigate to="/login" replace state={{ from: location }} />
  }

  if (user.role !== 'ADMIN') {
    return <Navigate to="/" replace />
  }

  return children
}

function App() {
  return (
    <BrowserRouter>
      <Navbar />
      <Routes>
        <Route path="/" element={<Home />} />
        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<Register />} />
        <Route path="/products" element={<Products />} />
        <Route path="/cart" element={<Cart />} />
        <Route path="/orders" element={<Orders />} />
        <Route path="/admin" element={<AdminRoute><AdminDashboard /></AdminRoute>} />
      </Routes>
    </BrowserRouter>
  )
}

export default App
