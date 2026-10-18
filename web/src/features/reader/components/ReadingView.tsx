'use client'

import './controls.css'
import { createPortal } from 'react-dom'
import { useShellTitle } from '@/components/layouts/ShellTitle'
import { Icon, type IconName } from '@/components/ui/Icon'
import { useReadingView } from '@/features/reader/hooks/useReadingView'
import type { ReadingView } from '@/features/reader/utils/reading'
import { SizeMenu } from './ReadingSize'

const VIEWS: { view: ReadingView; label: string; icon: IconName }[] = [
  { view: 'book', label: '책으로 보기', icon: 'spread' },
  { view: 'post', label: '글로 보기', icon: 'doc' },
]

type ToggleProps = {
  view: ReadingView
  onChoose: (view: ReadingView) => void
}

/** The book a spread at a time, or one item a screen: the one in use pressed. */
export function ViewToggle({ view, onChoose }: ToggleProps) {
  return (
    <fieldset className="views" aria-label="보기 방식">
      {VIEWS.map((option) => (
        <button
          key={option.view}
          type="button"
          className="ibtn"
          aria-pressed={view === option.view}
          aria-label={option.label}
          data-tip={option.label}
          onClick={() => onChoose(option.view)}
        >
          <Icon name={option.icon} />
        </button>
      ))}
    </fieldset>
  )
}

/** The view and the text size, at the start of the reader's header buttons. */
export function ReadingControls() {
  const shell = useShellTitle()
  const [view, setView] = useReadingView()
  if (!shell.acts || view === null) return null
  return createPortal(
    <>
      <ViewToggle view={view} onChoose={setView} />
      <SizeMenu />
    </>,
    shell.acts,
  )
}
