'use client'

import { type FormEvent, type KeyboardEvent, useRef, useState } from 'react'
import { COMPOSER_MAX_HEIGHT, sendsOnEnter } from '../lib/composer'
import { Icon } from './Icon'

type Props = {
  /** The text box's accessible name. */
  label: string
  placeholder?: string
  onSend: (text: string) => void
  /** Holds the next send back while the last one is still being answered. */
  busy?: boolean
  maxLength?: number
  autoFocus?: boolean
  /** The round button on the left, when the screen has something to put there. */
  plus?: { label: string; expanded?: boolean; onClick: () => void }
}

export function Composer({ label, placeholder, onSend, busy, maxLength, autoFocus, plus }: Props) {
  const [value, setValue] = useState('')
  const form = useRef<HTMLFormElement>(null)
  const input = useRef<HTMLTextAreaElement>(null)
  const empty = value.trim() === ''

  // One line that grows with its text, up to the cap; a sent message shrinks it back.
  function grow(box: HTMLTextAreaElement | null) {
    if (!box) return
    box.style.height = 'auto'
    box.style.height = `${Math.min(box.scrollHeight, COMPOSER_MAX_HEIGHT)}px`
  }

  function send(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (empty || busy) return
    onSend(value.trim())
    setValue('')
    if (input.current) input.current.style.height = 'auto'
  }

  function keyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    const key = {
      key: event.key,
      shiftKey: event.shiftKey,
      isComposing: event.nativeEvent.isComposing,
      keyCode: event.keyCode,
    }
    if (!sendsOnEnter(key, matchMedia('(pointer: coarse)').matches)) return
    event.preventDefault()
    form.current?.requestSubmit()
  }

  return (
    <form
      ref={form}
      className={plus ? 'composer' : 'composer no-plus'}
      autoComplete="off"
      onSubmit={send}
    >
      {plus && (
        <button
          type="button"
          className="cbtn c-plus"
          aria-label={plus.label}
          aria-expanded={plus.expanded}
          data-tip={plus.label}
          onClick={plus.onClick}
        >
          <Icon name="plus" />
        </button>
      )}
      <textarea
        ref={input}
        rows={1}
        aria-label={label}
        placeholder={placeholder}
        maxLength={maxLength}
        // biome-ignore lint/a11y/noAutofocus: the composer moves when a screen fills, and the reader is mid-sentence
        autoFocus={autoFocus}
        value={value}
        onChange={(event) => {
          setValue(event.target.value)
          grow(event.target)
        }}
        onKeyDown={keyDown}
      />
      <button type="submit" className="cbtn c-send" aria-label="보내기" disabled={empty || busy}>
        <Icon name="send" />
      </button>
    </form>
  )
}
