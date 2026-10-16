'use client'

import { useCallback, useEffect, useRef, useState } from 'react'
import { moveInMenu } from '@/lib/menu'
import {
  THEME_LABELS,
  THEMES,
  type Theme,
  themeAttribute,
  themeColors,
  themeCookie,
} from '@/lib/theme'
import { Icon } from './Icon'
import { useDismiss } from './useDismiss'

function apply(theme: Theme) {
  const attribute = themeAttribute(theme)
  if (attribute) document.documentElement.dataset.theme = attribute
  else delete document.documentElement.dataset.theme
  // biome-ignore lint/suspicious/noDocumentCookie: the Cookie Store API is not in Safari
  document.cookie = themeCookie(theme)
  for (const { media, color } of themeColors(theme)) {
    document
      .querySelector(`meta[name="theme-color"][media="${media}"]`)
      ?.setAttribute('content', color)
  }
}

/** The theme row's dropdown: system, light or dark, kept in a cookie. */
export function ThemeSelect({ initial }: { initial: Theme }) {
  const [theme, setTheme] = useState(initial)
  const [open, setOpen] = useState(false)
  const root = useRef<HTMLDivElement>(null)
  const button = useRef<HTMLButtonElement>(null)

  const close = useCallback(() => setOpen(false), [])
  useDismiss(open, root, button, close)

  // An open menu takes focus on the theme in use, and the page scrolls to show all of it.
  useEffect(() => {
    if (!open) return
    root.current
      ?.querySelector<HTMLElement>('[aria-checked="true"]')
      ?.focus({ preventScroll: true })
    root.current?.querySelector('[role="menu"]')?.scrollIntoView({ block: 'nearest' })
  }, [open])

  function choose(next: Theme) {
    apply(next)
    setTheme(next)
    setOpen(false)
    button.current?.focus()
  }

  return (
    <div className="dd" ref={root}>
      <button
        ref={button}
        type="button"
        className="dd-btn"
        aria-haspopup="menu"
        aria-expanded={open}
        aria-labelledby="theme-label theme-value"
        onClick={() => setOpen(!open)}
      >
        <span id="theme-value">{THEME_LABELS[theme]}</span>
        <Icon name="chevD" size={16} />
      </button>
      {open && (
        <div className="menu" role="menu" aria-labelledby="theme-label" onKeyDown={moveInMenu}>
          {THEMES.map((option) => (
            <button
              key={option}
              type="button"
              role="menuitemradio"
              className="mi check"
              aria-checked={option === theme}
              onClick={() => choose(option)}
            >
              {THEME_LABELS[option]}
              <Icon name="check" size={18} />
            </button>
          ))}
        </div>
      )}
    </div>
  )
}
