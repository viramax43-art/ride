import { resolveApiBaseUrl } from '../../config/env'
import { getRequiredTelegramInitData } from './telegramInitDataProvider'
import { clearAccessToken, getAccessToken, readAccessTokenFromUrl, saveAccessToken } from './tokenStorage'

interface TokenResponse {
  access_token: string
}

let tokenRefreshPromise: Promise<string> | null = null

async function loginWithTelegramInitData(initData: string): Promise<string> {
  const response = await fetch(`${resolveApiBaseUrl()}/api/auth`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ initData }),
  })
  if (!response.ok) {
    const body = await response.text()
    throw new Error(body || `AUTH_FAILED (${response.status})`)
  }
  const body = (await response.json()) as TokenResponse
  saveAccessToken(body.access_token)
  return body.access_token
}

async function refreshAccessToken(): Promise<string> {
  if (tokenRefreshPromise) return tokenRefreshPromise
  tokenRefreshPromise = (async () => {
    const urlToken = readAccessTokenFromUrl()
    if (urlToken) {
      saveAccessToken(urlToken)
      return urlToken
    }
    const cachedToken = getAccessToken()
    if (cachedToken) return cachedToken
    const initData = await getRequiredTelegramInitData()
    return loginWithTelegramInitData(initData)
  })()
  try {
    return await tokenRefreshPromise
  } finally {
    tokenRefreshPromise = null
  }
}

export async function ensurePassengerAccessToken(forceRefresh = false): Promise<string> {
  const cachedToken = getAccessToken()
  if (cachedToken && !forceRefresh) return cachedToken
  return refreshAccessToken()
}

export async function loginPassengerWithTelegramInitData(initData: string): Promise<string> {
  return loginWithTelegramInitData(initData)
}

export { clearAccessToken, getAccessToken }
