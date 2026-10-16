'use client'

import { createContext, useContext } from 'react'

/**
 * The shell's side panel, Canvas-style: a screen portals its content into [slot] and opens it, and
 * the sidebar folds to its rail while it is open. On a phone it covers the screen.
 */
export type ShellPanel = { slot: HTMLElement | null; setOpen: (open: boolean) => void }

export const ShellPanelContext = createContext<ShellPanel>({ slot: null, setOpen: () => {} })

export const useShellPanel = () => useContext(ShellPanelContext)
