'use client'

import { useCallback, useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { EDIT_BUTTON, SetupContentsEditor, type ShownContents } from '@/components/SetupContents'
import { BOOK_READY } from '@/components/SetupTurns'
import { useShellPanel } from '@/components/ShellPanel'
import type { useSetupSession } from '@/components/useSetupSession'
import {
  type HoldStep,
  keepsEditing,
  liveContents,
  type ShownHold,
  shownHold,
  stepOn,
} from '@/lib/setup'

type Setup = ReturnType<typeof useSetupSession>

const NO_STEP: HoldStep = { seq: null, editing: false, starting: false, notice: null }

/**
 * The contents a session holds, shown whole in the thread: edited in the side panel, which hands
 * focus back to 수정 as it closes, and started with, which hands it to 책으로 이동.
 */
export function useHeldContents(setup: Setup) {
  const { session } = setup
  const { slot, open, setOpen } = useShellPanel()
  // Tagged with the contents message it was taken on, so a new hold starts from nothing.
  const [step, setStep] = useState(NO_STEP)
  // The contents last held, kept on screen once started with: the session holds them no longer.
  const [kept, setKept] = useState<ShownHold | null>(null)
  const backToEdit = useRef(false)
  const toBook = useRef(false)

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
    if (!confirmed || !toBook.current) return
    toBook.current = false
    document.getElementById(BOOK_READY)?.focus({ preventScroll: true })
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

  async function start() {
    const seq = live
    change(seq, { starting: true, notice: null })
    toBook.current = true
    const result = await setup.start()
    const failed = result !== null && !result.ok
    if (failed) toBook.current = false
    change(seq, { starting: false, notice: failed ? result.message : null })
  }

  const contents: ShownContents | null = shown && {
    ...shown,
    live: shown.seq === live,
    editing: editorOpen,
    starting: now.starting,
    notice: now.notice,
    onEdit: () => change(live, { editing: true, notice: null }),
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
