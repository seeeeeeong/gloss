'use client'

import { type ReactNode, useEffect, useRef } from 'react'

type Props = {
  labelledBy: string
  onClose: () => void
  /** False for a dialog that must be answered: Escape and the backdrop leave it open. */
  dismissible?: boolean
  alert?: boolean
  plain?: boolean
  children: ReactNode
}

/** A modal over the page while it is mounted; the browser keeps focus inside it. */
export function Dialog({ labelledBy, onClose, dismissible = true, alert, plain, children }: Props) {
  const ref = useRef<HTMLDialogElement>(null)

  useEffect(() => {
    const dialog = ref.current
    if (dialog && !dialog.open) dialog.showModal()
    return () => dialog?.close()
  }, [])

  return (
    // biome-ignore lint/a11y/useKeyWithClickEvents: Escape reaches it as the dialog's cancel event
    <dialog
      ref={ref}
      className={plain ? 'dlg plain' : 'dlg'}
      role={alert ? 'alertdialog' : undefined}
      aria-labelledby={labelledBy}
      onCancel={(event) => {
        event.preventDefault()
        if (dismissible) onClose()
      }}
      onClick={(event) => {
        // The backdrop belongs to the dialog element, so only a click outside its box is one on it.
        const box = event.currentTarget.getBoundingClientRect()
        const outside =
          event.clientX < box.left ||
          event.clientX > box.right ||
          event.clientY < box.top ||
          event.clientY > box.bottom
        if (event.target === event.currentTarget && outside && dismissible) onClose()
      }}
    >
      {children}
    </dialog>
  )
}
