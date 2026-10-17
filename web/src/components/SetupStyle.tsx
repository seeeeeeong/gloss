'use client'

import { useId, useState } from 'react'
import type { ChosenStyle } from '@/lib/setup'
import { DEFAULT_LEVEL, LEVELS, type Level, VOICES, type Voice } from '@/lib/styles'

type Props = {
  /** Only the contents a book would be made from now are started with; the rest only show the voices. */
  live: boolean
  /** The voice a start is on its way with. */
  starting: Voice | null
  /** Nothing starts while the contents are being edited, or may be about to change. */
  blocked: boolean
  onStart: (style: ChosenStyle) => void
}

/**
 * Gloss's question after the contents: the level, then the three voices side by side, each with the
 * same paragraph written ahead in it. Picking a voice starts with it and the level picked.
 */
export function SetupStyle({ live, starting, blocked, onStart }: Props) {
  const [level, setLevel] = useState<Level>(DEFAULT_LEVEL)
  const id = useId()
  const busy = starting !== null || blocked
  return (
    <>
      <p>글은 어떤 문체로 쓸까요?</p>
      {live && (
        <div className="lv" role="radiogroup" aria-labelledby={`${id}-level`}>
          <span className="lv-l" id={`${id}-level`}>
            수준
          </span>
          <div className="seg seg-3">
            {LEVELS.map((option) => (
              <label key={option.id}>
                <input
                  type="radio"
                  name={`${id}-level`}
                  value={option.id}
                  checked={level === option.id}
                  onChange={() => setLevel(option.id)}
                />
                <span>{option.label}</span>
              </label>
            ))}
          </div>
        </div>
      )}
      <ul className="voices" aria-label="문체">
        {VOICES.map((voice) => (
          <li className="vb" key={voice.id}>
            <span className="v-l">{voice.label}</span>
            <span className="v-s">{voice.sample}</span>
            {live && (
              // Not disabled while busy: a disabled button would drop focus to the page.
              <button
                type="button"
                className="pick"
                aria-label={`${voice.label}, 이 문체로 시작`}
                aria-disabled={busy}
                aria-busy={starting === voice.id}
                onClick={() => {
                  if (!busy) onStart({ level, voice: voice.id })
                }}
              >
                {starting === voice.id ? '시작하는 중' : '이 문체로 시작'}
              </button>
            )}
          </li>
        ))}
      </ul>
    </>
  )
}
