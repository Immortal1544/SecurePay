const API_BASE_URL = 'http://localhost:8080'
const TOKEN_KEY = 'securepay_token'
const USER_KEY = 'securepay_user'

async function requestAuth(endpoint, data) {
  const response = await fetch(`${API_BASE_URL}/api/auth/${endpoint}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(data),
  })

  let responseData = null
  try {
    responseData = await response.json()
  } catch {
    responseData = null
  }

  if (!response.ok) {
    const message = responseData?.message || responseData?.error || 'Unable to complete the request.'
    throw new Error(message)
  }

  return responseData
}

export function registerUser(data) {
  return requestAuth('register', data)
}

export function loginUser(data) {
  return requestAuth('login', data)
}

export function logoutUser() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}

export function getToken() {
  return localStorage.getItem(TOKEN_KEY)
}

export function getCurrentUser() {
  const storedUser = localStorage.getItem(USER_KEY)

  if (!storedUser) {
    return null
  }

  try {
    return JSON.parse(storedUser)
  } catch {
    return null
  }
}