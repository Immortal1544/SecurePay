const configuredApiBaseUrl = import.meta.env.VITE_API_BASE_URL

if (import.meta.env.PROD && !configuredApiBaseUrl) {
  throw new Error('VITE_API_BASE_URL must be set for production builds.')
}

const apiBaseUrl = configuredApiBaseUrl || 'http://localhost:8080'

export const API_BASE_URL = apiBaseUrl.replace(/\/+$/, '')
