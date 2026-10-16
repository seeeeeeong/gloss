import { type BookCandidate, creditOf, editionOf } from '@/lib/books'

type Props = {
  found: BookCandidate[] | null
  error: string | null
  searching: boolean
  /** The ref of the edition being added, if one is. */
  adding: string | null
  onAdd: (candidate: BookCandidate) => void
  /** Takes an edition the source has no contents for, to be typed in below. */
  onType: (candidate: BookCandidate) => void
}

/** Gloss's answer to a search: the editions found, each added or typed in from its own row. */
export function BookSearch({ found, error, searching, adding, onAdd, onType }: Props) {
  if (searching) return <p className="think pulse">검색 중</p>
  const alert = error && (
    <p className="f-err" role="alert">
      {error}
    </p>
  )
  if (!found) return alert || null
  if (found.length === 0) return <p>검색 결과가 없습니다.</p>

  const credit = found[0] ? creditOf(found[0].source) : null
  const busy = adding !== null
  return (
    <>
      <div>
        {found.map((candidate) => (
          <article className="bk" key={candidate.ref}>
            <div className="bk-in">
              <div className="bk-title">
                <p>
                  <a className="dl" href={candidate.url} target="_blank" rel="noopener noreferrer">
                    {candidate.title}
                  </a>
                </p>
                {candidate.hasContents ? (
                  <button
                    type="button"
                    className="pick"
                    disabled={busy}
                    aria-label={`${candidate.title}, 추가`}
                    aria-busy={adding === candidate.ref}
                    onClick={() => onAdd(candidate)}
                  >
                    {adding === candidate.ref ? '추가 중' : '추가'}
                  </button>
                ) : (
                  <button
                    type="button"
                    className="pick"
                    disabled={busy}
                    aria-label={`${candidate.title}, 직접 입력`}
                    onClick={() => onType(candidate)}
                  >
                    직접 입력
                  </button>
                )}
              </div>
              <p className="bk-meta">
                {[editionOf(candidate), candidate.hasContents ? '' : '목차 없음']
                  .filter(Boolean)
                  .join(' · ')}
              </p>
            </div>
          </article>
        ))}
      </div>
      {alert}
      {credit && <p className="credit">{credit}</p>}
    </>
  )
}
