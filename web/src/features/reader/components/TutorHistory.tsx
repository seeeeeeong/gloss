'use client'

import { Dialog } from '@/components/ui/Dialog'
import { Icon } from '@/components/ui/Icon'
import type { ChatSession } from '@/features/reader/api/chat'
import { historyRowsOf, relativeDay } from '@/features/reader/utils/tutor'
import type { TocItem } from '@/lib/books'

type Props = {
  sessions: ChatSession[]
  items: TocItem[]
  /** The conversation open, marked in the list. */
  openId: string | null
  now: Date
  /** Why the last delete did not go through. */
  failed: string | null
  onOpen: (sessionId: string) => void
  onDelete: (sessionId: string) => void
}

/** The book's conversations, newest first: each by its first question, the item it started on and when. */
export function TutorHistory({ sessions, items, openId, now, failed, onOpen, onDelete }: Props) {
  const byKey = new Map(items.map((item) => [item.itemKey, item]))
  return (
    <>
      {failed && <p className="t-note h-failed">{failed}</p>}
      <ul className="t-hlist" aria-label="대화 기록">
        {historyRowsOf(sessions, openId).map(({ session, open }) => {
          const item = session.itemKey === null ? undefined : byKey.get(session.itemKey)
          const label = item ? item.title : session.itemKey?.split(' > ').pop()
          return (
            <li key={session.id} className="h-row" aria-current={open ? 'true' : undefined}>
              <button type="button" className="h-open" onClick={() => onOpen(session.id)}>
                <span className="h-q">{session.firstQuestion}</span>
                <span className="h-m">
                  {label && (
                    <span className="it">
                      {item?.number && <span className="no">{item.number}</span>}
                      {label}
                    </span>
                  )}
                  <span className="d">{relativeDay(session.lastActivityAt, now)}</span>
                </span>
              </button>
              <button
                type="button"
                className="ibtn h-del"
                aria-label="대화 삭제"
                onClick={() => onDelete(session.id)}
              >
                <Icon name="trash" size={18} />
              </button>
            </li>
          )
        })}
      </ul>
    </>
  )
}

/** Asks before a conversation is deleted, with 취소 focused. */
export function DeleteChatDialog({
  onDelete,
  onCancel,
}: {
  onDelete: () => void
  onCancel: () => void
}) {
  return (
    <Dialog labelledBy="delete-chat-title" onClose={onCancel} alert look="confirm">
      <h2 id="delete-chat-title">대화를 삭제할까요?</h2>
      <div className="dlg-stack">
        <button type="button" className="btn danger" onClick={onDelete}>
          삭제
        </button>
        <button type="button" className="btn sec" data-autofocus onClick={onCancel}>
          취소
        </button>
      </div>
    </Dialog>
  )
}
