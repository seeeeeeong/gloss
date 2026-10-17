'use client'

import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { useEffect, useState } from 'react'
import { load, send } from '@/lib/api'
import {
  failureLink,
  type PlanStatus,
  planPath,
  planProgress,
  planRetryPath,
  planRunning,
  planWaits,
} from '@/lib/setup'
import { Icon } from './Icon'

// A plan takes from seconds to minutes; looking every two is quick enough and light.
const LOOK_AGAIN_MS = 2000
const FILL_MS = 500

/** The line saying how the plan comes along, which takes focus once the reader has started. */
export const PLAN_LINE = 'plan-line'

type Props = {
  bookId: string
  /** How many items the plan is written for, when the start counted them. */
  items: number | null | undefined
  /** When the book was made, from which the line fills. */
  since: string
}

/**
 * Gloss's answer to the start: while the book's plan is written, a line and a thin progress line
 * under it; then that it is ready, or that it failed or was cancelled, with a retry, and the way to
 * the book. When the plan cannot be looked at, it says why and stops looking until asked again.
 */
export function SetupPlan({ bookId, items, since }: Props) {
  const [status, setStatus] = useState<PlanStatus | null>(null)
  const [now, setNow] = useState(() => Date.now())
  // Where the line fills from: the start, or the latest retry.
  const [from, setFrom] = useState(() => Date.parse(since))
  const [lookFailed, setLookFailed] = useState<string | null>(null)
  const [retrying, setRetrying] = useState(false)
  const [retryFailed, setRetryFailed] = useState<string | null>(null)
  const waiting = planWaits(status, lookFailed)

  useEffect(() => {
    if (!waiting) return
    let live = true
    async function look() {
      const result = await load<PlanStatus>(planPath(bookId))
      if (!live) return
      if (result.ok) setStatus(result.data)
      else setLookFailed(result.message)
    }
    void look()
    const looking = setInterval(look, LOOK_AGAIN_MS)
    const filling = setInterval(() => setNow(Date.now()), FILL_MS)
    return () => {
      live = false
      clearInterval(looking)
      clearInterval(filling)
    }
  }, [waiting, bookId])

  async function retry() {
    if (retrying) return
    setRetrying(true)
    setRetryFailed(null)
    const result = await send<PlanStatus>('POST', planRetryPath(bookId))
    setRetrying(false)
    if (!result.ok) return setRetryFailed(result.message)
    setFrom(Date.now())
    setNow(Date.now())
    setStatus(result.data)
  }

  return (
    <PlanLine
      bookId={bookId}
      items={items}
      status={status}
      filled={planProgress(now - from, items)}
      lookFailed={lookFailed}
      onLook={() => setLookFailed(null)}
      retry={{ running: retrying, failed: retryFailed, onRetry: retry }}
    />
  )
}

type LineProps = {
  bookId: string
  items: number | null | undefined
  status: PlanStatus | null
  /** The progress line's fill, from 0 to 100. */
  filled: number
  /** Why the plan could not be looked at; looking stops until [onLook]. */
  lookFailed: string | null
  onLook: () => void
  /** Asking again for a plan that ended without one: under way, why it was refused, and the ask. */
  retry: { running: boolean; failed: string | null; onRetry: () => void }
}

export function PlanLine({ bookId, items, status, filled, lookFailed, onLook, retry }: LineProps) {
  const state = status?.state ?? 'planning'
  const link = status?.code ? failureLink(status.code) : null
  if (lookFailed !== null && state === 'planning')
    return (
      <>
        <p id={PLAN_LINE} tabIndex={-1} role="status">
          {lookFailed}{' '}
          <button type="button" className="tbtn" onClick={onLook}>
            다시 시도
          </button>
        </p>
        <BookReady bookId={bookId} />
      </>
    )
  const said =
    state === 'ready'
      ? '준비됐어요. 항목을 열면 그 글을 바로 써 드릴게요.'
      : state === 'failed'
        ? '글 순서를 정리하지 못했어요.'
        : planRunning(items)
  return (
    <>
      <p id={PLAN_LINE} tabIndex={-1} role="status">
        {said}
        {state === 'failed' && link && (
          <>
            {' '}
            <Link className="dl" href={link.href}>
              {link.label}
            </Link>
          </>
        )}
        {state === 'failed' && (
          <>
            {' '}
            {/* Not disabled while it asks: a disabled button would drop focus to the page. */}
            <button
              type="button"
              className="tbtn"
              aria-disabled={retry.running}
              onClick={retry.onRetry}
            >
              다시 시도
            </button>
          </>
        )}
      </p>
      {state === 'failed' && retry.failed && <p role="alert">{retry.failed}</p>}
      {state === 'planning' && (
        <div className="progress" role="progressbar" aria-label={said}>
          <i style={{ width: `${filled}%` }} />
        </div>
      )}
      {state !== 'planning' && <BookReady bookId={bookId} />}
    </>
  )
}

/** To the book made, refreshing the sidebar on the way so it lists the book. */
function BookReady({ bookId }: { bookId: string }) {
  const router = useRouter()
  const href = `/books/${bookId}`
  return (
    <a
      className="submit"
      href={href}
      onClick={(event) => {
        if (event.metaKey || event.ctrlKey || event.shiftKey || event.button !== 0) return
        event.preventDefault()
        router.push(href)
        router.refresh()
      }}
    >
      책으로 이동
      <Icon name="arrowR" size={16} />
    </a>
  )
}
