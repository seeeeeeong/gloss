import { describe, expect, it } from 'vitest'
import type { ChatMessage } from './chat'
import {
  FOLLOW_LIMIT_MS,
  followWait,
  followWhileAnswering,
  holdOthers,
  MIN_READER,
  mergeBySeq,
  narrowAt,
  pillTop,
  pollUntil,
  repliedTo,
  shownOf,
  showsThread,
  type TutorAction,
  type TutorState,
  turnIn,
  tutorDocks,
  tutorOf,
  tutorReducer,
} from './tutor'

const message = (
  seq: number,
  role: 'reader' | 'gloss',
  text = `${role} ${seq}`,
  turnId: string | null = role === 'reader' ? `turn-${seq}` : null,
): ChatMessage => ({
  seq,
  role,
  turnId,
  replyTo: role === 'gloss' ? seq - 1 : null,
  itemKey: 'a',
  quote: null,
  text,
  status: role === 'gloss' ? 'done' : null,
  code: null,
  message: null,
  createdAt: '2026-10-17T00:00:00Z',
})

const apply = (state: TutorState, ...actions: Parameters<typeof tutorReducer>[1][]) =>
  actions.reduce(tutorReducer, state)

describe('tutorReducer', () => {
  it('drops what comes back for a book the reader has left', () => {
    const onB = apply(tutorOf('A'), { type: 'reset', bookId: 'B' })
    const late = apply(
      onB,
      { type: 'loaded', bookId: 'A', messages: [message(1, 'reader'), message(2, 'gloss')] },
      { type: 'asking', bookId: 'A', asked: message(-1, 'reader') },
    )
    expect(late).toEqual(tutorOf('B'))
    expect(apply(late, { type: 'loaded', bookId: 'B', messages: [] }).messages).toEqual([])
  })

  it('shows each message once whether the first read or the answer comes back first', () => {
    const asked = message(3, 'reader')
    const reply = message(4, 'gloss')
    const kept = [message(1, 'reader'), message(2, 'gloss'), asked, reply]
    const answer = { type: 'answered', bookId: 'A', asked, reply } as const
    const read = { type: 'loaded', bookId: 'A', messages: kept } as const

    const answerFirst = apply(tutorOf('A'), answer, read)
    const readFirst = apply(tutorOf('A'), read, answer)

    expect(answerFirst.messages?.map((m) => m.seq)).toEqual([1, 2, 3, 4])
    expect(readFirst.messages?.map((m) => m.seq)).toEqual([1, 2, 3, 4])
  })

  it('streams the answer onto the turn being asked', () => {
    const state = apply(
      tutorOf('A'),
      { type: 'asking', bookId: 'A', asked: message(-1, 'reader') },
      { type: 'asked', bookId: 'A', asked: message(1, 'reader') },
      { type: 'text', bookId: 'A', piece: '자원을 ' },
      { type: 'text', bookId: 'A', piece: '나눠요.' },
    )
    expect(state.live).toEqual({ asked: message(1, 'reader'), text: '자원을 나눠요.' })
  })
})

describe('mergeBySeq', () => {
  it('lays the newer copy over the older and leaves out a message not yet numbered', () => {
    const merged = mergeBySeq(
      [message(2, 'gloss', 'old'), message(-1, 'reader')],
      [message(1, 'reader'), message(2, 'gloss', 'new')],
    )
    expect(merged.map((m) => `${m.seq}:${m.text}`)).toEqual(['1:reader 1', '2:new'])
  })
})

describe('shownOf', () => {
  it('shows the turn being answered once, though a read already holds it, numbered or not', () => {
    const kept = [message(1, 'reader'), message(2, 'gloss'), message(3, 'reader', '질문', 'sent')]
    const numbered = message(3, 'reader', '질문', 'sent')
    const pending = message(-1, 'reader', '질문', 'sent')

    expect(shownOf(kept, { asked: numbered, text: '' }).map((m) => m.seq)).toEqual([1, 2, 3])
    expect(shownOf(kept, { asked: pending, text: '' }).map((m) => m.seq)).toEqual([1, 2, -1])
  })

  it('keeps an earlier question in the same words', () => {
    const kept = [message(1, 'reader', '질문', 'earlier'), message(2, 'gloss')]
    const pending = message(-1, 'reader', '질문', 'sent')

    expect(shownOf(kept, { asked: pending, text: '' }).map((m) => m.seq)).toEqual([1, 2, -1])
  })
})

describe('holdOthers', () => {
  it('sets aside all but the modal, then brings back only what it set aside and returns focus', () => {
    const sheet = { inert: false }
    const page = { inert: false }
    const already = { inert: true }
    let focused = false

    const release = holdOthers(
      [page, sheet, already],
      (el) => el === sheet,
      () => {
        focused = true
      },
    )
    expect([page.inert, sheet.inert, already.inert]).toEqual([true, false, true])

    release()
    expect([page.inert, sheet.inert, already.inert]).toEqual([false, false, true])
    expect(focused).toBe(true)
  })
})

describe('tutorDocks', () => {
  it('docks beside a book that keeps its width, and is a sheet on a narrow tablet with the sidebar open', () => {
    // 1440 wide, rail 52: the stage is 1388 before docking and 1008 after.
    expect(tutorDocks(1388, false)).toBe(true)
    expect(tutorDocks(1008, true)).toBe(true)
    // 768 wide with the sidebar open at 260: 508 without the tutor, 128 with it.
    expect(tutorDocks(508, false)).toBe(false)
    expect(tutorDocks(128, true)).toBe(false)
  })

  it('gives the same answer docked or not at a given window, so the panel does not flip', () => {
    for (const undocked of [700, 859, 860, 861, 1200]) {
      expect(tutorDocks(undocked, false)).toBe(tutorDocks(undocked - 380, true))
    }
    expect(tutorDocks(MIN_READER + 380, false)).toBe(true)
  })
})

describe('pollUntil', () => {
  it('reads again until the reply is kept, and stops at the last try', async () => {
    const reads = [
      [message(1, 'reader')],
      [message(1, 'reader')],
      [message(1, 'reader'), message(2, 'gloss')],
    ]
    let waited = 0
    let at = 0
    const read = async () => reads[Math.min(at++, reads.length - 1)] ?? []
    const wait = async () => {
      waited++
    }

    const settled = await pollUntil(read, (m) => repliedTo(m, 1), { tries: 8, wait })
    expect(settled.map((m) => m.seq)).toEqual([1, 2])
    expect(waited).toBe(2)

    at = 0
    const given = await pollUntil(
      async () => [message(1, 'reader')],
      (m) => repliedTo(m, 1),
      { tries: 3, wait },
    )
    expect(given).toHaveLength(1)
    expect(waited).toBe(4)
  })
})

describe('turnIn', () => {
  it('finds the turn sent with no word back by its id, not its words', () => {
    // Saved before the page's first read came back: the same words, another turn.
    const earlier = [message(1, 'reader', '질문', 'earlier'), message(2, 'gloss')]
    const sent = [...earlier, message(3, 'reader', '질문', 'sent')]

    expect(turnIn(earlier, 'sent')).toBe('absent')
    expect(turnIn(sent, 'sent')).toBe('kept')
    expect(turnIn([...sent, message(4, 'gloss')], 'sent')).toBe('answered')
  })
})

describe('showsThread', () => {
  it('shows a turn sent before the first read comes back', () => {
    const waiting = { messages: null, loadFailed: false, live: null, refused: null }
    expect(showsThread(waiting)).toBe(false)
    expect(showsThread({ ...waiting, live: { asked: message(-1, 'reader'), text: '' } })).toBe(true)
    expect(
      showsThread({ ...waiting, refused: { code: 'c', message: 'm', text: 't', quote: null } }),
    ).toBe(true)
  })
})

describe('pillTop', () => {
  const screen = 800

  it('goes under a phrase on touch, and over it when the phrase is at the bottom of the screen', () => {
    expect(pillTop({ top: 300, bottom: 320 }, screen, true)).toBe(328)
    expect(pillTop({ top: 740, bottom: 760 }, screen, true)).toBe(740 - 36 - 8)
  })

  it('goes over a phrase with a mouse, and under it when the header leaves no room', () => {
    expect(pillTop({ top: 300, bottom: 320 }, screen, false)).toBe(256)
    expect(pillTop({ top: 70, bottom: 90 }, screen, false)).toBe(98)
  })

  it('stays on screen however tall the phrase', () => {
    const top = pillTop({ top: 20, bottom: 790 }, screen, true)
    expect(top).toBeGreaterThanOrEqual(60)
    expect(top + 36).toBeLessThanOrEqual(screen)
  })
})

describe('reading along with another tab', () => {
  it('marks the book answered elsewhere until the reads find its reply, for this book only', () => {
    const on = apply(tutorOf('A'), { type: 'elsewhere', bookId: 'A', on: true })
    expect(on.elsewhere).toBe(true)
    expect(apply(on, { type: 'elsewhere', bookId: 'B', on: false }).elsewhere).toBe(true)
    expect(apply(on, { type: 'elsewhere', bookId: 'A', on: false }).elsewhere).toBe(false)
  })

  it("marks another tab's turn as under way while the server says so, and not this page's own", () => {
    const read = (answering: boolean): TutorAction => ({
      type: 'loaded',
      bookId: 'A',
      messages: [],
      answering,
    })
    expect(apply(tutorOf('A'), read(true)).elsewhere).toBe(true)
    expect(apply(tutorOf('A'), read(true), read(false)).elsewhere).toBe(false)
    const asking = { type: 'asking', bookId: 'A', asked: message(-1, 'reader') } as const
    expect(apply(tutorOf('A'), asking, read(true)).elsewhere).toBe(false)
  })

  it('follows a turn as long as a call may run, reading less often as it goes on', async () => {
    const waits: number[] = []
    const sleep = async (ms: number) => {
      waits.push(ms)
    }
    // Under way for five minutes of waiting, then ended.
    let left = 5 * 60_000
    const ended = await followWhileAnswering(async () => {
      if (left <= 0) return false
      left -= waits.at(-1) ?? 0
      return true
    }, sleep)
    expect(ended).toBe(true)
    expect(waits[0]).toBe(3000)
    expect(Math.max(...waits)).toBe(10_000)
    expect(waits.reduce((a, b) => a + b, 0)).toBeGreaterThan(2 * 60_000)

    waits.length = 0
    const gaveUp = await followWhileAnswering(async () => true, sleep)
    expect(gaveUp).toBe(false)
    expect(waits.reduce((a, b) => a + b, 0)).toBeLessThanOrEqual(FOLLOW_LIMIT_MS)
    expect(waits.reduce((a, b) => a + b, 0)).toBeGreaterThan(20 * 60_000)
    expect(followWait(1)).toBe(3000)
    expect(followWait(50)).toBe(10_000)
  })
})

describe('narrowAt', () => {
  it('judges a new width against the docking it was measured under, and not a docking change alone', () => {
    // Docked beside a book of 500, then closed: the 500 is still the last width measured.
    expect(narrowAt(0, 500, true)).toBe(false)
    expect(narrowAt(500, 500, false)).toBeNull()
    // The closed panel's width comes in: 880 without the tutor still docks it.
    expect(narrowAt(500, 880, false)).toBe(false)
    expect(narrowAt(880, 508, false)).toBe(true)
    expect(narrowAt(880, 0, false)).toBeNull()
  })
})

describe('repliedTo', () => {
  it('takes only a reply to that message as its answer, not a later turn answered after a restart', () => {
    // 1 was cut off by a restart; 2 was asked after and answered by 3.
    const thread = [
      message(1, 'reader'),
      message(2, 'reader'),
      { ...message(3, 'gloss'), replyTo: 2 },
    ]
    expect(repliedTo(thread, 1)).toBe(false)
    expect(repliedTo(thread, 2)).toBe(true)
    expect(turnIn(thread, 'turn-1')).toBe('kept')
  })
})
