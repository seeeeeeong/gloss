/** A failure's `retryAfter` is in seconds, when the server said how long to wait. */
export type ApiResult<T> =
  | { ok: true; data: T }
  | { ok: false; status: number; code: string; message: string; retryAfter?: number }

const CSRF_COOKIE = 'XSRF-TOKEN'
const CSRF_HEADER = 'X-XSRF-TOKEN'
const UNKNOWN = { code: 'common.unknown', message: '잠시 후 다시 시도해 주세요.' }
export const UNREACHED = {
  ok: false,
  status: 0,
  code: 'common.network',
  message: '서버에 연결하지 못했어요.',
} as const

/** The server sets this cookie; the page sends it back as a header on every change. */
export function csrfToken(cookie: string): string | undefined {
  const pair = cookie
    .split(';')
    .map((part) => part.trim())
    .find((part) => part.startsWith(`${CSRF_COOKIE}=`))
  return pair ? decodeURIComponent(pair.slice(CSRF_COOKIE.length + 1)) : undefined
}

export async function readResult<T>(response: Response): Promise<ApiResult<T>> {
  if (response.ok) {
    const text = await response.text()
    return { ok: true, data: (text ? JSON.parse(text) : undefined) as T }
  }
  const body = await response.json().catch(() => null)
  const known = typeof body?.code === 'string' && typeof body?.message === 'string'
  const retryAfter = Number.parseInt(response.headers.get('retry-after') ?? '', 10)
  return {
    ok: false,
    status: response.status,
    code: known ? body.code : UNKNOWN.code,
    message: known ? body.message : UNKNOWN.message,
    ...(retryAfter > 0 ? { retryAfter } : {}),
  }
}

/**
 * A change from the browser: same origin, so Next's rewrite or the proxy reaches the server.
 * The CSRF cookie lasts only as long as the browser, the session thirty days; a browser
 * reopened on a live session has to ask for the cookie again before it can change anything.
 */
export async function send<T>(method: string, path: string, body?: unknown): Promise<ApiResult<T>> {
  try {
    const response = await fetch(path, {
      method,
      headers: await changeHeaders(),
      body: body === undefined ? undefined : JSON.stringify(body),
    })
    return await readResult<T>(response)
  } catch {
    return UNREACHED
  }
}

/** The headers a change carries, asking for the CSRF cookie first when the browser has none. */
export async function changeHeaders(): Promise<Record<string, string>> {
  if (!csrfToken(document.cookie)) await fetch('/api/auth/csrf')
  const token = csrfToken(document.cookie)
  return { 'content-type': 'application/json', ...(token ? { [CSRF_HEADER]: token } : {}) }
}

/** A read the page makes after it has loaded, as a search does. */
export async function load<T>(path: string): Promise<ApiResult<T>> {
  try {
    return await readResult<T>(await fetch(path))
  } catch {
    return UNREACHED
  }
}
