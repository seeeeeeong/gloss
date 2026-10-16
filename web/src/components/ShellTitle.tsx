'use client'

import { createContext, useContext } from 'react'

/**
 * Where a screen may put its own title in the shell's header, through a portal: [pill] beside the
 * menu button on a phone, [bar] at the header's left elsewhere. The shell's own title steps aside.
 */
export type ShellTitle = { pill: HTMLElement | null; bar: HTMLElement | null }

export const ShellTitleContext = createContext<ShellTitle>({ pill: null, bar: null })

export const useShellTitle = () => useContext(ShellTitleContext)
