'use client'

import { createContext, useContext } from 'react'

/**
 * The shell's side panel, Canvas-style: a screen portals its content into [slot] and opens it, and
 * the sidebar folds to its rail while it is open. On a phone it covers the screen. [open] turns true
 * only once the panel is shown, so its content can take focus then.
 */
export type ShellPanel = {
  slot: HTMLElement | null
  open: boolean
  setOpen: (open: boolean) => void
}

export const ShellPanelContext = createContext<ShellPanel>({
  slot: null,
  open: false,
  setOpen: () => {},
})

export const useShellPanel = () => useContext(ShellPanelContext)
