import axios from 'axios'
import type { HealthResponse, LoginResponse } from './types'

const api = axios.create({ baseURL: '/' })

export async function health(): Promise<HealthResponse> {
  const { data } = await api.get<HealthResponse>('/api/health')
  return data
}

export async function register(
  username: string,
  password: string,
  passwordAgain: string,
): Promise<void> {
  const { data } = await api.post<string>('/api', null, {
    params: { q: `register: ${username};${password};${passwordAgain}` },
  })
  if (data !== 'OK') {
    throw new Error('Registration failed')
  }
}

export async function login(
  username: string,
  password: string,
): Promise<LoginResponse> {
  const { data } = await api.post<LoginResponse | string>('/api', null, {
    params: { q: `login: ${username};${password}` },
  })
  if (typeof data === 'string' || !data?.token) {
    throw new Error('Login failed')
  }
  return data
}

export async function logout(token: string): Promise<void> {
  const { data } = await api.post<string>('/api', null, {
    params: { q: `logout: ${token}` },
  })
  if (data !== 'OK') {
    throw new Error('Logout failed')
  }
}
