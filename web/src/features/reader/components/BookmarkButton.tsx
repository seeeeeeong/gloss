'use client'

import { createPortal } from 'react-dom'
import { useShellTitle } from '@/components/layouts/ShellTitle'
import { Icon } from '@/components/ui/Icon'
import { type Bookmark, itemMarkOf } from '@/lib/bookmarks'

type MarkProps = {
  current: string | null
  marks: Bookmark[]
  onSave: (itemKey: string) => void
  onRemove: (mark: Bookmark) => void
}

/** The item open kept whole as a bookmark, or let go, from the header; nothing to keep on the front pages. */
export function BookmarkButton({ current, marks, onSave, onRemove }: MarkProps) {
  const shell = useShellTitle()
  if (!shell.acts) return null
  const own = itemMarkOf(marks, current)
  const label = own ? '책갈피 지우기' : '이 글 책갈피에 저장'
  return createPortal(
    <button
      type="button"
      className={own ? 'ibtn saved' : 'ibtn'}
      aria-pressed={own !== undefined}
      aria-label={label}
      data-tip={label}
      disabled={current === null}
      onClick={() => {
        if (own) onRemove(own)
        else if (current) onSave(current)
      }}
    >
      <Icon name="bookmark" />
    </button>,
    shell.acts,
  )
}
