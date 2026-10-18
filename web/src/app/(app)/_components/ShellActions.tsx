'use client'

import { useState } from 'react'
import { Dialog } from '@/components/ui/Dialog'
import { Icon } from '@/components/ui/Icon'
import { SizeStepper } from '@/features/reader/components/ReadingSize'
import { useDarkTheme } from '@/hooks/useDarkTheme'
import { initialOf } from './shell'
import { ThemeSwitch } from './ThemeSwitch'

/** The header's right end: the theme in one press, and the reader's letter opening the settings. */
export function ShellActions({ name }: { name: string }) {
  const { dark, toggle } = useDarkTheme()
  const [settings, setSettings] = useState(false)
  const themeLabel = dark ? '라이트 모드로 변경' : '다크 모드로 변경'
  return (
    <div className="top-me">
      <button
        type="button"
        className="ibtn sm tert"
        aria-label={themeLabel}
        data-tip={themeLabel}
        onClick={toggle}
      >
        <Icon name={dark ? 'sun' : 'moon'} size={18} />
      </button>
      <button
        type="button"
        className="avatar"
        aria-label="설정 열기"
        data-tip="설정"
        onClick={() => setSettings(true)}
      >
        {initialOf(name)}
      </button>
      {settings && (
        <Dialog labelledBy="settings-title" onClose={() => setSettings(false)}>
          <h2 id="settings-title">설정</h2>
          <button
            type="button"
            className="ibtn x"
            aria-label="닫기"
            onClick={() => setSettings(false)}
          >
            <Icon name="x" />
          </button>
          <div className="set-me">
            <span className="avatar lg" aria-hidden="true">
              {initialOf(name)}
            </span>
            <span>{name}</span>
          </div>
          <div className="set-row">
            <span id="set-dark">다크 모드</span>
            <ThemeSwitch labelledBy="set-dark" />
          </div>
          <div className="set-row">
            <span id="set-size">읽기 글자 크기</span>
            <SizeStepper labelledBy="set-size" />
          </div>
        </Dialog>
      )}
    </div>
  )
}
