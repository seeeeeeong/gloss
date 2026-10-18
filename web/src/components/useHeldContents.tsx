'use client'

import { useCallback, useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { CONTENTS_CARD, ContentsPanel, type ShownContents } from '@/components/SetupContents'
import { PLAN_LINE } from '@/components/SetupPlan'
import { useShellPanel } from '@/components/ShellPanel'
import type { useSetupSession } from '@/components/useSetupSession'
import {
  type ChosenStyle,
  type HoldStep,
  liveContents,
  once,
  type ShownHold,
  seekingContents,
  shownHold,
  stepOn,
} from '@/lib/setup'

type Setup = ReturnType<typeof useSetupSession>

const NO_STEP: HoldStep = { seq: null, reviewing: false, starting: null, notice: null }

// Wide enough to keep the thread beside the panel, so new contents open in it unasked.
const BESIDE = '(min-width: 1024px)'

/** Where focus goes once the panel is gone: its card, the composer, or the first voice to start with. */
type Back = 'card' | 'composer' | 'voices'

const BACK_TO: Record<Back, string> = {
  card: `#${CONTENTS_CARD}`,
  composer: '.dock textarea',
  voices: '.voices .pick',
}

/**
 * The contents a session holds: a card in the thread that opens them whole in the side panel, which
 * hands focus back to where the reader goes next as it closes, and started with, which hands it to the
 * line saying how the plan comes along.
 */
export function useHeldContents(setup: Setup) {
  const { session } = setup
  const { slot, open, setOpen } = useShellPanel()
  // Tagged with the contents message it was taken on, so a new hold starts from nothing: no panel,
  // no voice a start is on its way with, no notice.
  const [step, setStep] = useState(NO_STEP)
  // The contents last held, kept on screen once started with: the session holds them no longer.
  const [kept, setKept] = useState<ShownHold | null>(null)
  const back = useRef<Back | null>(null)
  const opened = useRef<number | null>(null)
  const toPlan = useRef(false)
  const seeking = useRef(false)

  const live = session ? liveContents(session) : null
  const held = live === null ? null : (session?.held ?? null)
  const released = live === null && session?.state !== 'confirmed'
  useEffect(() => {
    if (live !== null && held) setKept({ seq: live, held })
  }, [live, held])
  // A new message lets the hold go: nothing is shown whole until the next pick.
  useEffect(() => {
    if (released) setKept(null)
  }, [released])
  const shown = shownHold(session, kept)
  const now = stepOn(step, live)
  const reviewing = now.reviewing && held !== null
  // Changes the step taken on [seq]; once another hold is live, the change is never seen.
  const change = useCallback((seq: number | null, patch: Partial<HoldStep>) => {
    setStep((prev) => ({ ...stepOn(prev, seq), ...patch }))
  }, [])

  useEffect(() => {
    setOpen(reviewing)
  }, [reviewing, setOpen])
  useEffect(() => () => setOpen(false), [setOpen])

  // New contents open in the panel once, where it fits beside the thread.
  useEffect(() => {
    if (live === null || opened.current === live) return
    opened.current = live
    if (matchMedia(BESIDE).matches) change(live, { reviewing: true, notice: null })
  }, [live, change])

  // Only once the panel is gone and the thread is wide again does the place to go back to stay put.
  useEffect(() => {
    if (open || !back.current) return
    const target = document.querySelector<HTMLElement>(BACK_TO[back.current])
    back.current = null
    target?.focus({ preventScroll: true })
    target?.scrollIntoView({ block: 'center' })
  }, [open])

  const confirmed = session?.state === 'confirmed'
  useEffect(() => {
    if (!confirmed || !toPlan.current) return
    toPlan.current = false
    document.getElementById(PLAN_LINE)?.focus({ preventScroll: true })
  }, [confirmed])

  const close = useCallback((to: Back) => {
    back.current = to
    setStep((prev) => ({ ...prev, reviewing: false }))
  }, [])

  // Refused, as when another tab moved on or no search is set up: said under the contents. A second
  // press while the first is on its way is not sent.
  async function seek() {
    const seq = live
    const result = await once(seeking, () => {
      change(seq, { notice: null })
      return setup.seek()
    })
    if (result && !result.ok) change(seq, { notice: result.message })
  }

  async function start(style: ChosenStyle) {
    const seq = live
    change(seq, { starting: style.voice, notice: null })
    toPlan.current = true
    const result = await setup.start(style)
    const failed = result !== null && !result.ok
    if (failed) toPlan.current = false
    change(seq, { starting: null, notice: failed ? result.message : null })
  }

  const contents: ShownContents | null = shown && {
    ...shown,
    live: shown.seq === live,
    reviewing,
    waiting: setup.pending !== null || session?.searching === true,
    starting: now.starting,
    searching: seekingContents(session),
    notice: now.notice,
    onReview: () => (reviewing ? close('card') : change(live, { reviewing: true, notice: null })),
    onSeek: seek,
    onStart: start,
  }

  const panel =
    reviewing && contents && slot
      ? createPortal(
          <ContentsPanel
            contents={contents}
            onClose={() => close('card')}
            onTalk={() => close('composer')}
            onChoose={() => close('voices')}
          />,
          slot,
        )
      : null

  // A new thread numbers its messages from 1 again, so its contents may take the seq last opened.
  const reset = useCallback(() => {
    setStep(NO_STEP)
    setKept(null)
    opened.current = null
    back.current = null
  }, [])

  return { contents, panel, reset }
}
