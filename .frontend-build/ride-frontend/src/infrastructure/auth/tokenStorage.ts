const TOKEN_KEY = 'ride_access_token'
const TOKEN_KEYS = [TOKEN_KEY, 'access_token', 'accessToken', 'token']
const INIT_DATA_KEY = 'ride_init_data'
const TELEGRAM_HANDOFF_KEY = 'ride_telegram_handoff_started'

function readQueryParam(name: string): string {
  const params = new URLSearchParams(window.location.search)
  const fromSearch = params.get(name)
  if (fromSearch) return fromSearch

  const hash = window.location.hash.startsWith('#') ? window.location.hash.slice(1) : window.location.hash
  if (!hash) return ''
  const hashQuery = hash.includes('?') ? hash.slice(hash.indexOf('?') + 1) : hash
  return new URLSearchParams(hashQuery).get(name) ?? ''
}

function safeGetFromStorage(storage: Storage | undefined, key: string): string {
  if (!storage) return ''
  try {
    return storage.getItem(key) ?? ''
  } catch {
    return ''
  }
}

function safeSet(storage: Storage | undefined, key: string, value: string): void {
  if (!storage) return
  try {
    storage.setItem(key, value)
  } catch {
    // noop
  }
}

function safeRemove(storage: Storage | undefined, key: string): void {
  if (!storage) return
  try {
    storage.removeItem(key)
  } catch {
    // noop
  }
}

function safeWindowToken(): string {
  try {
    const globalToken = (window as Window & { __RIDE_ACCESS_TOKEN__?: unknown }).__RIDE_ACCESS_TOKEN__
    return typeof globalToken === 'string' ? globalToken : ''
  } catch {
    return ''
  }
}

function readStoredToken(): string {
  const globalToken = safeWindowToken()
  if (globalToken) return globalToken

  for (const key of TOKEN_KEYS) {
    const localToken = safeGetFromStorage(window.localStorage, key)
    if (localToken) return localToken
    const sessionToken = safeGetFromStorage(window.sessionStorage, key)
    if (sessionToken) return sessionToken
  }
  return ''
}

export function getAccessToken(): string | null {
  const token = readStoredToken()
  return token || null
}

export function saveAccessToken(token: string): void {
  if (!token) return
  for (const key of TOKEN_KEYS) {
    safeSet(window.localStorage, key, token)
    safeSet(window.sessionStorage, key, token)
  }
  clearTelegramHandoffAttempted()
  try {
    ;(window as Window & { __RIDE_ACCESS_TOKEN__?: string }).__RIDE_ACCESS_TOKEN__ = token
  } catch {
    // noop
  }
}

export function clearAccessToken(): void {
  for (const key of TOKEN_KEYS) {
    safeRemove(window.localStorage, key)
    safeRemove(window.sessionStorage, key)
  }
  clearTelegramHandoffAttempted()
  try {
    delete (window as Window & { __RIDE_ACCESS_TOKEN__?: string }).__RIDE_ACCESS_TOKEN__
  } catch {
    // noop
  }
}

export function readCachedInitData(): string {
  return safeGetFromStorage(window.localStorage, INIT_DATA_KEY)
}

export function cacheInitData(initData: string): void {
  if (!initData) return
  safeSet(window.localStorage, INIT_DATA_KEY, initData)
}

export function readAccessTokenFromUrl(): string {
  return readQueryParam('accessToken') || readQueryParam('ride_access_token') || readQueryParam('token')
}

export function hasTelegramHandoffAttempted(): boolean {
  return safeGetFromStorage(window.sessionStorage, TELEGRAM_HANDOFF_KEY) === '1' ||
    safeGetFromStorage(window.localStorage, TELEGRAM_HANDOFF_KEY) === '1'
}

export function markTelegramHandoffAttempted(): void {
  safeSet(window.sessionStorage, TELEGRAM_HANDOFF_KEY, '1')
  safeSet(window.localStorage, TELEGRAM_HANDOFF_KEY, '1')
}

export function clearTelegramHandoffAttempted(): void {
  safeRemove(window.sessionStorage, TELEGRAM_HANDOFF_KEY)
  safeRemove(window.localStorage, TELEGRAM_HANDOFF_KEY)
}
