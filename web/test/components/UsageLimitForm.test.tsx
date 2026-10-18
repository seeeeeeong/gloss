import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it, vi } from 'vitest'
import { UsageLimitForm } from '@/components/UsageLimitForm'

vi.mock('next/navigation', () => ({ useRouter: () => ({ replace() {}, refresh() {} }) }))

const usage = {
  billingMonth: '2026-10-01',
  limitMicros: 5_000_000,
  spentMicros: 1_240_000,
  inFlightMicros: 40_000,
  remainingMicros: 3_720_000,
}

describe('UsageLimitForm', () => {
  it('shows the bar and what is left once there is a limit', () => {
    const out = renderToStaticMarkup(<UsageLimitForm usage={usage} />)

    expect(out).toContain('2026년 10월')
    expect(out).toContain('class="ubar"')
    expect(out).toContain('남은 한도')
    expect(out).toContain('$5.00')
    expect(out).toContain('aria-label="월 한도 수정"')
  })

  it('shows neither without one', () => {
    const out = renderToStaticMarkup(
      <UsageLimitForm usage={{ ...usage, limitMicros: null, remainingMicros: null }} />,
    )

    expect(out).not.toContain('ubar')
    expect(out).not.toContain('남은 한도')
    expect(out).toContain('없음')
  })
})
