'use client'

import { lineOf, READING_SIZES, sizeLabel, stepped } from '@/lib/reading'
import { Dialog } from './Dialog'
import { Icon } from './Icon'
import { useReadingSize } from './useReadingSize'

const SMALLEST = READING_SIZES[0]
const LARGEST = READING_SIZES[READING_SIZES.length - 1]

/** − 18 +: the book's text a size smaller or larger. */
export function SizeStepper({ labelledBy }: { labelledBy: string }) {
  const [size, setSize] = useReadingSize()
  return (
    <fieldset className="stepper" aria-labelledby={labelledBy}>
      <button
        type="button"
        aria-label="글자 작게"
        disabled={size === SMALLEST}
        onClick={() => setSize(stepped(size, -1))}
      >
        <Icon name="minus" size={14} />
      </button>
      <span aria-live="polite">{size}</span>
      <button
        type="button"
        aria-label="글자 크게"
        disabled={size === LARGEST}
        onClick={() => setSize(stepped(size, 1))}
      >
        <Icon name="plus" size={14} />
      </button>
    </fieldset>
  )
}

/** 글자 크기 from the reader's header: the three sizes, each with 가 set in it, and a line of the book in the one chosen. */
export function ReadingSizeDialog({ onClose }: { onClose: () => void }) {
  const [size, setSize] = useReadingSize()
  return (
    <Dialog labelledBy="size-title" onClose={onClose}>
      <h2 id="size-title">글자 크기</h2>
      <button type="button" className="ibtn x" aria-label="닫기" onClick={onClose}>
        <Icon name="x" />
      </button>
      <div className="sizes">
        {READING_SIZES.map((option) => (
          <button
            key={option}
            type="button"
            aria-pressed={size === option}
            data-autofocus={size === option || undefined}
            onClick={() => setSize(option)}
          >
            <span style={{ fontSize: option }} aria-hidden="true">
              가
            </span>
            {sizeLabel(option)}
          </button>
        ))}
      </div>
      <p className="size-sample" style={{ fontSize: size, lineHeight: `${lineOf(size)}px` }}>
        프로세스는 실행 중인 프로그램과 그 실행 상태를 말한다.
      </p>
    </Dialog>
  )
}
