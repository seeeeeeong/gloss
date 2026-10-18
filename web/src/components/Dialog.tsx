'use client'

import { type ReactNode, useEffect, useRef } from 'react'

type Props = {
  labelledBy: string
  onClose: () => void
  /** False for a dialog that must be answered: Escape and the backdrop leave it open. */
  dismissible?: boolean
  alert?: boolean
  /** No close button to leave room for, a confirmation's narrow, centred box, or a wide search. */
  look?: 'plain' | 'confirm' | 'search'
  children: ReactNode
}

/**
 * A modal over the page while it is mounted; the browser keeps focus inside it. Focus starts on
 * the element marked `data-autofocus`, else on the first that takes it.
 */
export function Dialog({ labelledBy, onClose, dismissible = true, alert, look, children }: Props) {
  const ref = useRef<HTMLDialogElement>(null)

  useEffect(() => {
    const dialog = ref.current
    // The browser hands focus back only to a dialog still on the page; this one is gone by then.
    const opener = document.activeElement instanceof HTMLElement ? document.activeElement : null
    if (dialog && !dialog.open) {
      dialog.showModal()
      dialog.querySelector<HTMLElement>('[data-autofocus]')?.focus()
    }
    return () => {
      dialog?.close()
      if (opener?.isConnected) opener.focus()
    }
  }, [])

  return (
    // biome-ignore lint/a11y/useKeyWithClickEvents: Escape reaches it as the dialog's cancel event
    <dialog
      ref={ref}
      className={look ? `dlg ${look}` : 'dlg'}
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
