'use client'

import { useCallback, useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { EDIT_BUTTON, SetupContentsEditor, type ShownContents } from '@/components/SetupContents'
import { PLAN_LINE } from '@/components/SetupPlan'
import { useShellPanel } from '@/components/ShellPanel'
import type { useSetupSession } from '@/components/useSetupSession'
import {
  type ChosenStyle,
  type HoldStep,
  keepsEditing,
  liveContents,
  once,
  type ShownHold,
  seekingContents,
  shownHold,
  stepOn,
} from '@/lib/setup'

type Setup = ReturnType<typeof useSetupSession>

const NO_STEP: HoldStep = { seq: null, editing: false, starting: null, notice: null }

/**
 * The contents a session holds, shown whole in the thread: edited in the side panel, which hands
 * focus back to 수정 as it closes, and started with, which hands it to the line saying how the plan
 * comes along.
 */
export function useHeldContents(setup: Setup) {
  const { session } = setup
  const { slot, open, setOpen } = useShellPanel()
  // Tagged with the contents message it was taken on, so a new hold starts from nothing: no editor,
  // no voice a start is on its way with, no notice.
  const [step, setStep] = useState(NO_STEP)
  // The contents last held, kept on screen once started with: the session holds them no longer.
  const [kept, setKept] = useState<ShownHold | null>(null)
  const backToEdit = useRef(false)
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
  const editorOpen = now.editing && held !== null
  // Changes the step taken on [seq]; once another hold is live, the change is never seen.
  const change = useCallback((seq: number | null, patch: Partial<HoldStep>) => {
    setStep((prev) => ({ ...stepOn(prev, seq), ...patch }))
  }, [])

  useEffect(() => {
    setOpen(editorOpen)
  }, [editorOpen, setOpen])
  useEffect(() => () => setOpen(false), [setOpen])

  // Only once the panel is gone and the thread is wide again does 수정 sit where it will stay.
  useEffect(() => {
    if (open || !backToEdit.current) return
    backToEdit.current = false
    const button = document.getElementById(EDIT_BUTTON)
    button?.focus({ preventScroll: true })
    button?.scrollIntoView({ block: 'center' })
  }, [open])

  const confirmed = session?.state === 'confirmed'
  useEffect(() => {
    if (!confirmed || !toPlan.current) return
    toPlan.current = false
    document.getElementById(PLAN_LINE)?.focus({ preventScroll: true })
  }, [confirmed])

  const closeEditor = useCallback(() => {
    backToEdit.current = true
    setStep((prev) => ({ ...prev, editing: false }))
  }, [])

  async function save(toc: string) {
    const seq = live
    const result = await setup.edit(toc)
    // Refused because the contents moved on in another tab: shown as they are now, in the thread.
    if (result && !result.ok && !keepsEditing(result)) {
      backToEdit.current = true
      change(seq, { editing: false, notice: result.message })
    }
    return result
  }

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
    editing: editorOpen,
    waiting: setup.pending !== null || session?.searching === true,
    starting: now.starting,
    searching: seekingContents(session),
    notice: now.notice,
    onEdit: () => change(live, { editing: true, notice: null }),
    onSeek: seek,
    onStart: start,
  }

  const editor =
    editorOpen && held && slot
      ? createPortal(
          <SetupContentsEditor
            key={live}
            held={held}
            open={open}
            onSave={save}
            onClose={closeEditor}
          />,
          slot,
        )
      : null

  const reset = useCallback(() => {
    setStep(NO_STEP)
    setKept(null)
  }, [])

  return { contents, editor, reset }
}
