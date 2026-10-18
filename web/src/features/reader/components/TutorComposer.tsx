'use client'

import {
  type FormEvent,
  type KeyboardEvent,
  type MouseEvent,
  type RefObject,
  useLayoutEffect,
} from 'react'
import { Icon } from '@/components/ui/Icon'
import { MAX_QUESTION, type Quote } from '@/features/reader/api/chat'
import { sendsOnEnter } from '@/utils/composer'

// Five lines of 22 and the box's padding.
const MAX_HEIGHT = 124

type Props = {
  value: string
  onChange: (value: string) => void
  quote: Quote | null
  onUnquote: () => void
  placeholder: string
  /** While Gloss answers, send becomes stop. */
  answering: boolean
  onSend: () => void
  onStop: () => void
  input: RefObject<HTMLTextAreaElement | null>
}

/** One line that grows to five, send inside it, and the picked phrase on a card above it with its x. */
export function TutorComposer(props: Props) {
  const { value, onChange, quote, onUnquote, placeholder, answering, onSend, onStop, input } = props
  const empty = value.trim() === ''

  // One line that grows with its text, up to the cap; an emptied box shrinks back.
  // biome-ignore lint/correctness/useExhaustiveDependencies: the height follows the text
  useLayoutEffect(() => {
    const box = input.current
    if (!box) return
    box.style.height = 'auto'
    box.style.height = `${Math.min(box.scrollHeight, MAX_HEIGHT)}px`
  }, [value, input])

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!empty && !answering) onSend()
  }

  function keyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key === 'Backspace' && value === '' && quote) {
      onUnquote()
      return
    }
    const key = {
      key: event.key,
      shiftKey: event.shiftKey,
      isComposing: event.nativeEvent.isComposing,
      keyCode: event.keyCode,
    }
    if (!sendsOnEnter(key, matchMedia('(pointer: coarse)').matches)) return
    event.preventDefault()
    event.currentTarget.form?.requestSubmit()
  }

  function focusBox(event: MouseEvent<HTMLFormElement>) {
    if (event.target === event.currentTarget) input.current?.focus()
  }

  return (
    <>
      {quote && (
        <div className="t-quote">
          <p>
            <mark>{quote.text}</mark>
          </p>
          <button type="button" className="ibtn" aria-label="인용 지우기" onClick={onUnquote}>
            <Icon name="x" size={16} />
          </button>
        </div>
      )}
      {/* biome-ignore lint/a11y/useKeyWithClickEvents: a click on the box's padding only moves focus to its text box */}
      <form
        className={quote ? 'composer quoted' : 'composer'}
        autoComplete="off"
        onSubmit={submit}
        onClick={focusBox}
      >
        <textarea
          ref={input}
          rows={1}
          aria-label="메시지"
          placeholder={placeholder}
          maxLength={MAX_QUESTION}
          value={value}
          onChange={(event) => onChange(event.target.value)}
          onKeyDown={keyDown}
        />
        {answering ? (
          <button type="button" className="cbtn c-send stop" aria-label="중지" onClick={onStop}>
            <Icon name="stop" />
          </button>
        ) : (
          <button type="submit" className="cbtn c-send" aria-label="보내기" disabled={empty}>
            <Icon name="send" />
          </button>
        )}
      </form>
    </>
  )
}
