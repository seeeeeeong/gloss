import { describe, expect, it } from 'vitest'
import type { ChatMessage, ChatSession } from '@/features/reader/api/chat'
import {
  FOLLOW_LIMIT_MS,
  followWait,
  followWhileAnswering,
  historyRowsOf,
  holdOthers,
  isQuiet,
  MIN_READER,
  mergeBySeq,
  narrowAt,
  pillTop,
  pollUntil,
  promptOf,
  relativeDay,
  repliedTo,
  sheetAfterDrag,
  shownOf,
  showsThread,
  startsDocked,
  type TutorAction,
  type TutorState,
  talkedItems,
  touchSession,
  turnIn,
  tutorDocks,
  tutorOf,
  tutorReducer,
} from '@/features/reader/utils/tutor'

const message = (
  seq: number,
  role: 'reader' | 'gloss',
  text = `${role} ${seq}`,
  turnId: string | null = role === 'reader' ? `turn-${seq}` : null,
): ChatMessage => ({
  sessionId: 's1',
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

describe('isQuiet', () => {
  it('holds only once read with nothing said, sent or refused', () => {
    const read = { messages: [], loadFailed: false, live: null, refused: null }
    expect(isQuiet(read)).toBe(true)
    expect(isQuiet({ ...read, messages: null })).toBe(false)
    expect(isQuiet({ ...read, messages: [message(1, 'reader')] })).toBe(false)
    expect(isQuiet({ ...read, live: { asked: message(-1, 'reader'), text: '' } })).toBe(false)
    expect(isQuiet({ ...read, loadFailed: true })).toBe(false)
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

describe('startsDocked', () => {
  it('opens a book with the tutor closed unless the reader docked it', () => {
    expect(startsDocked(null)).toBe(false)
    expect(startsDocked('false')).toBe(false)
    expect(startsDocked('true')).toBe(true)
  })
})

const session = (
  id: string,
  lastActivityAt: string,
  overrides: Partial<ChatSession> = {},
): ChatSession => ({
  id,
  firstQuestion: `${id} 질문`,
  itemKey: 'a',
  itemKeys: ['a'],
  startedAt: '2026-10-01T00:00:00Z',
  lastActivityAt,
  ...overrides,
})

describe('talkedItems', () => {
  it('names each item any conversation came from, once, and none before anything is read', () => {
    const elsewhere = { ...message(3, 'reader'), itemKey: 'b' }
    const general = { ...message(5, 'reader'), itemKey: null }
    const older = session('s0', '2026-10-01T00:00:00Z', { itemKeys: ['c', 'a'] })
    expect([
      ...talkedItems([older], [message(1, 'reader'), message(2, 'gloss'), elsewhere, general]),
    ]).toEqual(['c', 'a', 'b'])
    expect(talkedItems(null, null).size).toBe(0)
  })

  it('drops an item once the only conversation about it is deleted', () => {
    const kept = session('s1', '2026-10-02T00:00:00Z', { itemKeys: ['a'] })
    const deleted = session('s2', '2026-10-03T00:00:00Z', { itemKeys: ['b'] })
    const before = apply(tutorOf('A'), { type: 'listed', bookId: 'A', sessions: [kept, deleted] })
    const after = apply(before, { type: 'removed', bookId: 'A', sessionId: 's2' })
    expect([...talkedItems(before.sessions, null)]).toEqual(['a', 'b'])
    expect([...talkedItems(after.sessions, null)]).toEqual(['a'])
  })
})

describe('relativeDay', () => {
  const now = new Date(2026, 9, 18, 9, 30)
  it('says 오늘 and 어제 by the calendar, not by 24 hours', () => {
    expect(relativeDay(new Date(2026, 9, 18, 0, 5).toISOString(), now)).toBe('오늘')
    expect(relativeDay(new Date(2026, 9, 17, 23, 50).toISOString(), now)).toBe('어제')
    expect(relativeDay(new Date(2026, 9, 17, 0, 1).toISOString(), now)).toBe('어제')
  })

  it('names an older day by its month and day, and its year when not this one', () => {
    expect(relativeDay(new Date(2026, 9, 12, 15).toISOString(), now)).toBe('10월 12일')
    expect(relativeDay(new Date(2026, 0, 3).toISOString(), now)).toBe('1월 3일')
    expect(relativeDay(new Date(2025, 11, 31).toISOString(), now)).toBe('2025년 12월 31일')
  })
})

describe('historyRowsOf', () => {
  it('puts the newest activity first and marks only the conversation open', () => {
    const rows = historyRowsOf(
      [
        session('s1', '2026-10-12T00:00:00Z'),
        session('s3', '2026-10-18T00:00:00Z'),
        session('s2', '2026-10-17T00:00:00Z'),
      ],
      's2',
    )
    expect(rows.map((row) => row.session.id)).toEqual(['s3', 's2', 's1'])
    expect(rows.map((row) => row.open)).toEqual([false, true, false])
  })

  it('marks none while a new conversation is open', () => {
    const rows = historyRowsOf([session('s1', '2026-10-12T00:00:00Z')], null)
    expect(rows.map((row) => row.open)).toEqual([false])
  })
})

describe('touchSession', () => {
  const asked = (sessionId: string, itemKey: string | null) => ({
    ...message(1, 'reader', '새 질문'),
    sessionId,
    itemKey,
    createdAt: '2026-10-18T10:00:00Z',
  })

  it('adds a new conversation at the top by its first question', () => {
    const older = session('s1', '2026-10-12T00:00:00Z')
    const touched = touchSession([older], asked('s2', 'b'))
    expect(touched?.map((s) => s.id)).toEqual(['s2', 's1'])
    expect(touched?.[0]).toMatchObject({ firstQuestion: '새 질문', itemKey: 'b', itemKeys: ['b'] })
  })

  it('moves a conversation gone on with to the top, adding the item it came from', () => {
    const touched = touchSession(
      [session('s2', '2026-10-17T00:00:00Z'), session('s1', '2026-10-12T00:00:00Z')],
      asked('s1', 'b'),
    )
    expect(touched?.map((s) => s.id)).toEqual(['s1', 's2'])
    expect(touched?.[0]).toMatchObject({ firstQuestion: 's1 질문', itemKeys: ['a', 'b'] })
    expect(touchSession(null, asked('s1', 'a'))).toBeNull()
  })
})

describe('switching conversations', () => {
  it('opens a new one empty, and a read begun for the one left changes nothing', () => {
    const read = apply(tutorOf('A'), {
      type: 'loaded',
      bookId: 'A',
      messages: [message(1, 'reader')],
      sessionId: 's1',
      view: 0,
    })
    const fresh = apply(read, { type: 'opened', bookId: 'A', sessionId: null, view: 1 })
    const late = apply(fresh, {
      type: 'loaded',
      bookId: 'A',
      messages: [message(1, 'reader'), message(2, 'gloss')],
      sessionId: 's1',
      view: 0,
    })
    expect(read.sessionId).toBe('s1')
    expect(fresh).toMatchObject({ sessionId: null, messages: [], view: 1 })
    expect(late).toBe(fresh)
  })

  it('takes the conversation its first question went into', () => {
    const fresh = apply(tutorOf('A'), { type: 'opened', bookId: 'A', sessionId: null, view: 1 })
    const asking = apply(fresh, { type: 'asking', bookId: 'A', asked: message(-1, 'reader') })
    const asked = apply(asking, {
      type: 'asked',
      bookId: 'A',
      asked: { ...message(1, 'reader'), sessionId: 's9' },
    })
    expect(asked.sessionId).toBe('s9')
  })

  it('reads one from the history, dropping a read of another', () => {
    const opened = apply(tutorOf('A'), { type: 'opened', bookId: 'A', sessionId: 's2', view: 1 })
    const other = apply(opened, {
      type: 'loaded',
      bookId: 'A',
      messages: [message(1, 'reader')],
      sessionId: 's1',
      view: 1,
    })
    expect(opened.messages).toBeNull()
    expect(other).toBe(opened)
  })
})

describe('promptOf', () => {
  const quote = { text: '자원 할당자', itemKey: 'a' }

  it('asks about the post, then for more once Gloss has answered', () => {
    expect(promptOf(null, null)).toBe('이 글에 대해 물어보세요')
    expect(promptOf(null, [message(1, 'reader')])).toBe('이 글에 대해 물어보세요')
    expect(promptOf(null, [message(1, 'reader'), message(2, 'gloss')])).toBe('이어서 물어보세요')
  })

  it('asks about the phrase quoted, before anything else', () => {
    expect(promptOf(quote, [message(1, 'reader'), message(2, 'gloss')])).toBe(
      '이 부분에 대해 물어보세요',
    )
  })

  it('does not count a reply with no words as an answer', () => {
    expect(promptOf(null, [message(1, 'reader'), message(2, 'gloss', '')])).toBe(
      '이 글에 대해 물어보세요',
    )
  })
})

describe('sheetAfterDrag', () => {
  it('grows to full height when pulled up, and steps down or closes when pulled down', () => {
    expect(sheetAfterDrag('half', -80)).toBe('full')
    expect(sheetAfterDrag('full', 80)).toBe('half')
    expect(sheetAfterDrag('half', 80)).toBe('closed')
  })

  it('stays where it was after a short drag', () => {
    expect(sheetAfterDrag('half', -20)).toBe('half')
    expect(sheetAfterDrag('full', 30)).toBe('full')
  })
})
