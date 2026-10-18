import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it, vi } from 'vitest'
import { PlanLine } from '@/components/SetupPlan'
import type { PlanStatus } from '@/lib/setup'

vi.mock('next/navigation', () => ({ useRouter: () => ({ push: () => {}, refresh: () => {} }) }))

const noop = () => {}

type Trouble = { lookFailed?: string | null; retryFailed?: string | null }

const render = (status: PlanStatus | null, items: number | null = 206, trouble: Trouble = {}) =>
  renderToStaticMarkup(
    <PlanLine
      bookId="b1"
      items={items}
      status={status}
      filled={40}
      lookFailed={trouble.lookFailed ?? null}
      onLook={noop}
      retry={{ running: false, failed: trouble.retryFailed ?? null, onRetry: noop }}
    />,
  )

describe('PlanLine', () => {
  it('says the plan is being written, over a thin line filled as far as it has run', () => {
    const out = render({ state: 'planning', code: null })

    expect(out).toContain(
      '<p id="plan-line" tabindex="-1" role="status">206개 항목의 글 순서를 정리하고 있어요.</p>',
    )
    expect(out).toContain('<div class="progress" role="progressbar"')
    expect(out).toContain('<i style="width:40%"></i>')
    expect(out).not.toContain('책으로 이동')
    expect(out).not.toContain('다시 시도')
  })

  it('says so before the first answer, as the plan is asked for with the start', () => {
    const out = render(null, null)

    expect(out).toContain('>글 순서를 정리하고 있어요.</p>')
    expect(out).toContain('progressbar')
  })

  it('says it is ready, then leads to the book', () => {
    const out = render({ state: 'ready', code: null })

    expect(out).toContain('>준비됐어요. 항목을 열면 그 글을 바로 써 드릴게요.</p>')
    expect(out).toContain('<a class="submit" href="/books/b1">책으로 이동')
    expect(out).not.toContain('progressbar')
    expect(out).not.toContain('다시 시도')
  })

  it('says it failed, with the way to fix a key or a limit, and still leads to the book', () => {
    const out = render({ state: 'failed', code: 'budget.limit_reached' })

    expect(out).toContain(
      '글 순서를 정리하지 못했어요. <a class="dl" href="/me#usage">사용 한도</a> <button type="button" class="tbtn" aria-disabled="false">다시 시도</button></p>',
    )
    expect(out).toContain('href="/books/b1"')
    expect(render({ state: 'failed', code: 'plan.not_json' })).toContain(
      '글 순서를 정리하지 못했어요. <button',
    )
  })

  it('says a plan cancelled before it was written failed, with a retry and the way to the book', () => {
    const out = render({ state: 'failed', code: 'plan.cancelled' })

    expect(out).toContain('>글 순서를 정리하지 못했어요. <button type="button" class="tbtn"')
    expect(out).toContain('>다시 시도</button></p>')
    expect(out).not.toContain('progressbar')
    expect(out).toContain('href="/books/b1"')
  })

  it('says why the plan could not be looked at, with a quiet retry, instead of a line that never ends', () => {
    const out = render(null, 206, { lookFailed: '서버에 연결할 수 없어요.' })

    expect(out).toContain(
      '<p id="plan-line" tabindex="-1" role="status">서버에 연결할 수 없어요. <button type="button" class="tbtn">다시 시도</button></p>',
    )
    expect(out).not.toContain('progressbar')
    expect(out).toContain('href="/books/b1"')
  })

  it('says why a retry was refused, under the failure it retried', () => {
    const out = render({ state: 'failed', code: 'plan.not_started' }, 206, {
      retryFailed: '지금은 요청이 많아요.',
    })

    expect(out).toContain('다시 시도</button></p><p role="alert">지금은 요청이 많아요.</p>')
    expect(render({ state: 'failed', code: 'plan.not_started' })).not.toContain('role="alert"')
  })
})
