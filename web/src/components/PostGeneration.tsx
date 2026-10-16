'use client'

import { useRouter } from 'next/navigation'
import { useEffect, useState } from 'react'
import { send } from '@/lib/api'
import { type Generation, generationLabel, generationPath, isUnderway } from '@/lib/posts'

// A post shows within three seconds of being kept, and the minute or so it takes to write is about
// twenty asks, each a few indexed reads: shorter adds load a reader would not notice the gain from.
const ASK_EVERY_MS = 3000

type Props = {
  bookId: string
  itemKey: string
  /** For a post the book's newer plan made stale: nothing is asked until the reader says so. */
  rewrite?: boolean
}

/**
 * Asks for the section's post as the page opens, and again until it is written. Work that failed,
 * and a post that is only stale, is asked for again only when the reader says so, since each try
 * is paid for on their key.
 */
export function PostGeneration({ bookId, itemKey, rewrite = false }: Props) {
  const router = useRouter()
  const [tries, setTries] = useState(rewrite ? 0 : 1)
  const [generation, setGeneration] = useState<Generation | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (tries === 0) return
    let live = true
    let timer: ReturnType<typeof setTimeout> | undefined
    async function ask(retry: boolean) {
      const result = await send<Generation>('POST', generationPath(bookId, itemKey, retry))
      if (!live) return
      if (!result.ok) {
        setError(result.message)
        return
      }
      setError(null)
      setGeneration(result.data)
      if (result.data.state === 'ready') router.refresh()
      else if (isUnderway(result.data.state))
        timer = setTimeout(() => void ask(false), ASK_EVERY_MS)
    }
    // Never rejects: a request that fails comes back as a result.
    void ask(rewrite || tries > 1)
    return () => {
      live = false
      clearTimeout(timer)
    }
  }, [bookId, itemKey, rewrite, tries, router])

  const again = () => setTries(tries + 1)
  if (tries === 0) {
    return (
      <p className="stale">
        <span className="gen">이전 목차로 쓴 글입니다.</span>
        <button type="button" className="tbtn" onClick={again}>
          다시 쓰기
        </button>
      </p>
    )
  }
  const failure = error ?? (generation?.state === 'failed' ? generation.message : null)
  if (failure) {
    return (
      <div className={rewrite ? 'gen-fail stale' : 'gen-fail'}>
        <p className="f-err" role="alert">
          {failure}
        </p>
        <button type="button" className="btn sec sm" onClick={again}>
          다시 시도
        </button>
      </div>
    )
  }
  return (
    <p className={rewrite ? 'gen pulse stale' : 'gen pulse'} role="status">
      {(generation && generationLabel(generation.state)) ?? '글 작성 중'}
    </p>
  )
}
