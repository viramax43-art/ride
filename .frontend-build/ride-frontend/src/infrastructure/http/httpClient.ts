import { resolveApiBaseUrl } from '../../config/env'
import { clearAccessToken, ensurePassengerAccessToken } from '../auth/passengerAuthSession'

export type HttpMethod = 'GET' | 'POST' | 'PATCH' | 'PUT' | 'DELETE'
export type AuthMode = 'bearer' | 'cookie' | 'none'

export interface ApiRequestOptions {
  method?: HttpMethod
  body?: unknown
  authMode?: AuthMode
  withCredentials?: boolean
}

export class ApiError extends Error {
  status: number
  body: string
  constructor(status: number, message: string, body: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.body = body
  }
}

export function parseApiErrorCode(error: unknown): string | null {
  if (!(error instanceof ApiError)) return null
  try {
    const parsed = JSON.parse(error.body) as { detail?: { code?: string } | string }
    const detail = parsed.detail
    if (detail && typeof detail === 'object' && typeof detail.code === 'string') {
      return detail.code
    }
  } catch {
    // ignore
  }
  return null
}

function extractErrorMessage(rawBody: string, status: number): string {
  if (!rawBody) return `API request failed (${status})`
  try {
    const parsed = JSON.parse(rawBody) as unknown
    if (parsed && typeof parsed === 'object') {
      const detail = (parsed as { detail?: unknown }).detail
      if (typeof detail === 'string') return detail
      if (detail && typeof detail === 'object' && !Array.isArray(detail)) {
        const message = (detail as { message?: unknown }).message
        if (typeof message === 'string') return message
        const code = (detail as { code?: unknown }).code
        if (typeof code === 'string') return code
      }
      if (Array.isArray(detail) && detail.length > 0) {
        const first = detail[0]
        if (first && typeof first === 'object' && typeof (first as { msg?: unknown }).msg === 'string') {
          return (first as { msg: string }).msg
        }
      }
      const message = (parsed as { message?: unknown }).message
      if (typeof message === 'string') return message
    }
  } catch {
    // not JSON, fallthrough
  }
  return rawBody
}

async function performRequest(
  path: string,
  options: ApiRequestOptions,
  forceRefreshToken: boolean,
): Promise<Response> {
  const { method = 'GET', body, authMode = 'bearer', withCredentials = false } = options
  const headers: Record<string, string> = { 'Content-Type': 'application/json' }
  if (authMode === 'bearer') {
    const token = await ensurePassengerAccessToken(forceRefreshToken)
    console.debug('[httpClient] bearer request', {
      path,
      method,
      forceRefreshToken,
      hasToken: Boolean(token),
      tokenPrefix: token ? token.slice(0, 12) : null,
    })
    headers.Authorization = `Bearer ${token}`
  }
  return fetch(`${resolveApiBaseUrl()}${path}`, {
    method,
    credentials: authMode === 'cookie' || withCredentials ? 'include' : undefined,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  })
}

export async function apiRequest<T>(path: string, options: ApiRequestOptions = {}): Promise<T> {
  const authMode = options.authMode ?? 'bearer'
  console.debug('[httpClient] apiRequest start', {
    path,
    method: options.method ?? 'GET',
    authMode,
  })
  const response = await performRequest(path, options, false)

  if (response.status === 401 && authMode === 'bearer') {
    const rawBody = await response.text()
    clearAccessToken()
    if (!window.location.pathname.includes('/admin')) {
      window.location.replace('https://t.me/rideminiapp_bot?start=auth')
    }
    console.warn('[httpClient] apiRequest 401', { path, status: response.status, body: rawBody })
    throw new ApiError(response.status, extractErrorMessage(rawBody, response.status), rawBody)
  }

  if (!response.ok) {
    const rawBody = await response.text()
    console.warn('[httpClient] apiRequest failed', { path, status: response.status, body: rawBody })
    throw new ApiError(response.status, extractErrorMessage(rawBody, response.status), rawBody)
  }

  if (response.status === 204) {
    return undefined as T
  }
  return (await response.json()) as T
}

export async function uploadMultipart<T>(path: string, formData: FormData): Promise<T> {
  const response = await fetch(`${resolveApiBaseUrl()}${path}`, {
    method: 'POST',
    credentials: 'include',
    body: formData,
  })
  if (!response.ok) {
    const message = await response.text()
    throw new Error(message || `Upload failed (${response.status})`)
  }
  return (await response.json()) as T
}

export async function uploadMultipartBearer<T>(path: string, formData: FormData): Promise<T> {
  const token = await ensurePassengerAccessToken(false)
  const response = await fetch(`${resolveApiBaseUrl()}${path}`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
    body: formData,
  })
  if (!response.ok) {
    const message = await response.text()
    throw new Error(message || `Upload failed (${response.status})`)
  }
  return (await response.json()) as T
}
