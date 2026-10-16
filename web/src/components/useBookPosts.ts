'use client'

import { useCallback, useEffect, useRef, useState } from 'react'
import { load } from '@/lib/api'
import { type Post, type PostSummary, postPath } from '@/lib/posts'
import { type ItemView, itemViewOf } from '@/lib/reader'
import { useGenerations } from './useGenerations'

/**
 * A book's posts as the reader turns to them: which are written, the text of those read so far,
 * and the work asked for the rest. A post is read only when its item is near the open spread.
 */
export function useBookPosts(bookId: string, posts: PostSummary[]) {
  const [summaries, setSummaries] = useState(
    () => new Map(posts.map((post) => [post.itemKey, post])),
  )
  const [texts, setTexts] = useState<ReadonlyMap<string, string>>(new Map())
  // The version each text on the page is of, so a question about it names the words the reader saw.
  const [versions, setVersions] = useState<ReadonlyMap<string, string>>(new Map())
  const { generations, ask, done, fail } = useGenerations(bookId)
  const reading = useRef(new Set<string>())
  const mounted = useRef(true)

  useEffect(() => {
    mounted.current = true
    return () => {
      mounted.current = false
    }
  }, [])

  const readPost = useCallback(
    async (key: string) => {
      if (reading.current.has(key)) return
      reading.current.add(key)
      const result = await load<Post>(postPath(bookId, key))
      reading.current.delete(key)
      if (!mounted.current) return
      if (!result.ok) {
        fail(key, result.code, result.message)
        return
      }
      const { contentMd, versionId, versionNo, stale, generatedAt } = result.data
      setTexts((before) => new Map(before).set(key, contentMd))
      setVersions((before) => new Map(before).set(key, versionId))
      setSummaries((before) =>
        new Map(before).set(key, { itemKey: key, versionNo, stale, generatedAt }),
      )
      done(key)
    },
    [bookId, done, fail],
  )

  // A post the server has kept is read once.
  useEffect(() => {
    for (const [key, generation] of generations) {
      if (generation.state === 'ready') void readPost(key)
    }
  }, [generations, readPost])

  const viewOf = useCallback(
    (key: string): ItemView => itemViewOf(summaries.get(key), generations.get(key)),
    [summaries, generations],
  )

  /** Asks for an item's post when nothing has been asked for it yet. */
  const arrive = useCallback(
    (key: string) => {
      if (viewOf(key).kind === 'todo') void ask(key, false)
    },
    [viewOf, ask],
  )

  /** Reads the posts among [keys] that are written but not yet in hand. */
  const prefetch = useCallback(
    (keys: string[]) => {
      for (const key of keys) {
        if (summaries.has(key) && !texts.has(key) && !generations.has(key)) void readPost(key)
      }
    },
    [summaries, texts, generations, readPost],
  )

  const retry = useCallback((key: string) => void ask(key, true), [ask])

  return { viewOf, texts, versions, arrive, prefetch, retry }
}
