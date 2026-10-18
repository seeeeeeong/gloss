'use client'

import { useCallback, useRef, useState } from 'react'
import { Icon } from '@/components/ui/Icon'
import { useReadingSize } from '@/features/reader/hooks/useReadingSize'
import { READING_SIZES, stepped } from '@/features/reader/utils/reading'
import { useDismiss } from '@/hooks/useDismiss'

const SMALLEST = READING_SIZES[0]
const LARGEST = READING_SIZES[READING_SIZES.length - 1]

type StepperProps = {
  labelledBy: string
  /** Shows 가 set at the size beside its number. */
  sample?: boolean
}

/** − 18 +: the book's text a size smaller or larger. */
export function SizeStepper({ labelledBy, sample = false }: StepperProps) {
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
      {sample ? (
        <span className="cur" aria-live="polite">
          <b aria-hidden="true" style={{ fontSize: size }}>
            가
          </b>
          {size}
        </span>
      ) : (
        <span aria-live="polite">{size}</span>
      )}
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

const SIZE_BUTTON = 'size-btn'

/** 가 in the reader's header, opening − 가 18 + under it. */
export function SizeMenu() {
  const [open, setOpen] = useState(false)
  const root = useRef<HTMLDivElement>(null)
  const trigger = useRef<HTMLButtonElement>(null)
  const close = useCallback(() => setOpen(false), [])
  useDismiss(open, root, trigger, close)
  return (
    <div ref={root} className="size-menu">
      <button
        ref={trigger}
        id={SIZE_BUTTON}
        type="button"
        className="ibtn ga"
        aria-expanded={open}
        aria-label="글자 크기"
        data-tip={open ? undefined : '글자 크기'}
        onClick={() => setOpen(!open)}
      >
        가
      </button>
      {open && (
        <div className="size-pop">
          <SizeStepper labelledBy={SIZE_BUTTON} sample />
        </div>
      )}
    </div>
  )
}
