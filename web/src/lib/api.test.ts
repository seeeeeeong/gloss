import { afterEach, describe, expect, it, vi } from 'vitest'
import { csrfToken, readResult, send } from './api'

describe('csrfToken', () => {
  it('reads the token the server set as a cookie', () => {
    expect(csrfToken('gloss.theme=dark; XSRF-TOKEN=abc-123')).toBe('abc-123')
  })

  it('decodes it, since a cookie value may be percent-encoded', () => {
    expect(csrfToken('XSRF-TOKEN=a%2Bb')).toBe('a+b')
  })

  it('is missing before the server set one', () => {
    expect(csrfToken('gloss.theme=dark')).toBeUndefined()
    expect(csrfToken('')).toBeUndefined()
  })
})

describe('readResult', () => {
  const json = (status: number, body: unknown) =>
    new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json' } })

  it('hands back the body of a success', async () => {
    expect(await readResult(json(200, { loginId: 'reader' }))).toEqual({
      ok: true,
      data: { loginId: 'reader' },
    })
  })

  it('hands back nothing for 204', async () => {
    expect(await readResult(new Response(null, { status: 204 }))).toEqual({
      ok: true,
      data: undefined,
    })
  })

  it('shows the message the server wrote for an error, and keeps its code', async () => {
    const result = await readResult(
      json(401, { code: 'account.login_failed', message: '아이디 또는 비밀번호가 맞지 않습니다' }),
    )

    expect(result).toEqual({
      ok: false,
      status: 401,
      code: 'account.login_failed',
      message: '아이디 또는 비밀번호가 맞지 않습니다',
    })
  })

  it('falls back to a plain message when an error carries no body we know', async () => {
    const result = await readResult(new Response('<html>Bad Gateway</html>', { status: 502 }))

    expect(result).toMatchObject({ ok: false, status: 502, code: 'common.unknown' })
    expect(result.ok || result.message).toBeTruthy()
  })
})

describe('send', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('asks for a CSRF cookie first when the browser lost it, then sends it back', async () => {
    const page = { cookie: 'gloss.theme=dark' }
    const calls: { path: string; headers?: Record<string, string> }[] = []
    vi.stubGlobal('document', page)
    vi.stubGlobal(
      'fetch',
      vi.fn(async (path: string, init?: { headers?: Record<string, string> }) => {
        calls.push({ path, headers: init?.headers })
        if (path === '/api/auth/csrf') page.cookie = 'gloss.theme=dark; XSRF-TOKEN=fresh'
        return new Response(null, { status: 204 })
      }),
    )

    await send('PUT', '/api/me', { displayName: 'Reader' })

    expect(calls.map((call) => call.path)).toEqual(['/api/auth/csrf', '/api/me'])
    expect(calls[1]?.headers?.['X-XSRF-TOKEN']).toBe('fresh')
  })

  it('reports a failure to reach the server, the CSRF request included, instead of throwing', async () => {
    vi.stubGlobal('document', { cookie: '' })
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => {
        throw new TypeError('Failed to fetch')
      }),
    )

    expect(await send('POST', '/api/auth/login', {})).toMatchObject({
      ok: false,
      code: 'common.network',
    })
  })
})
