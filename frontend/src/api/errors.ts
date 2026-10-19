import axios from 'axios'

export function errorMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    if (typeof error.response?.data === 'string') {
      return error.response.data
    }
  }
  return error instanceof Error ? error.message : 'Request failed'
}

export function handleFailure(
  error: unknown,
  onExpired: () => void,
): string {
  const message = errorMessage(error)
  if (message === 'Unauthorized') {
    onExpired()
  }
  return message
}
