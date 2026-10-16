'use client'

import { useCallback, useEffect, useRef, useState } from 'react'
import { send } from '@/lib/api'
import { type Generation, generationPath, isUnderway } from '@/lib/posts'

// A post shows within three seconds of being kept, and the minute or so it takes to write is about
// twenty asks, each a few indexed reads: shorter adds load a reader would not notice the gain from.
const ASK_EVERY_MS = 3000

const ASKED: Generation = { state: 'writing', code: null, message: null }

/**
 * The posts this page has asked for, by item key, asked again until each is written. Work that
 * failed, and a post that is only stale, is asked for again only on `retry`, when the reader says
 * so, since each try is paid for on their key. An item whose work is ready waits there until its
 * post is read and [done] is called.
 */
export function useGenerations(bookId: string) {
  const [generations, setGenerations] = useState<ReadonlyMap<string, Generation>>(new Map())
  const timers = useRef(new Map<string, ReturnType<typeof setTimeout>>())
  const live = useRef(true)
  useEffect(() => {
    live.current = true
    const pending = timers.current
    return () => {
      live.current = false
      for (const timer of pending.values()) clearTimeout(timer)
      pending.clear()
    }
  }, [])

  const put = useCallback((itemKey: string, generation: Generation | null) => {
    setGenerations((before) => {
      const after = new Map(before)
      if (generation) after.set(itemKey, generation)
      else after.delete(itemKey)
      return after
    })
  }, [])

  const ask = useCallback(
    async (itemKey: string, retry: boolean) => {
      clearTimeout(timers.current.get(itemKey))
      timers.current.delete(itemKey)
      put(itemKey, ASKED)
      // Never rejects: a request that fails comes back as a result.
      const result = await send<Generation>('POST', generationPath(bookId, itemKey, retry))
      if (!live.current) return
      const generation: Generation = result.ok
        ? result.data
        : { state: 'failed', code: result.code, message: result.message }
      put(itemKey, generation)
      if (isUnderway(generation.state))
        timers.current.set(
          itemKey,
          setTimeout(() => void ask(itemKey, false), ASK_EVERY_MS),
        )
    },
    [bookId, put],
  )

  /** Forgets an item's finished work once its post is in hand; work asked for since stays. */
  const done = useCallback((itemKey: string) => {
    setGenerations((before) => {
      if (before.get(itemKey)?.state !== 'ready') return before
      const after = new Map(before)
      after.delete(itemKey)
      return after
    })
  }, [])

  /** Records why an item's post could not be read, so the reader can ask again. */
  const fail = useCallback(
    (itemKey: string, code: string, message: string) =>
      put(itemKey, { state: 'failed', code, message }),
    [put],
  )

  return { generations, ask, done, fail }
}
