'use client'

import { Icon } from './Icon'
import { useDarkTheme } from './useDarkTheme'

/** 다크 모드 as a switch, its knob showing the sun or the moon. */
export function ThemeSwitch({ labelledBy }: { labelledBy: string }) {
  const { dark, toggle } = useDarkTheme()
  return (
    <button
      type="button"
      className="switch"
      role="switch"
      aria-checked={dark}
      aria-labelledby={labelledBy}
      onClick={toggle}
    >
      <span>
        <Icon name={dark ? 'moon' : 'sun'} size={14} />
      </span>
    </button>
  )
}
