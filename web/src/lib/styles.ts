/**
 * How a book's posts are written: who for, and in what voice. The ids are the server's codes; the
 * samples are written ahead, one subject in every voice, so choosing calls no model.
 */
export type Level = 'intro' | 'course' | 'work'
export type Voice = 'textbook' | 'explain' | 'note'

export const LEVELS: { id: Level; label: string }[] = [
  { id: 'intro', label: '입문' },
  { id: 'course', label: '전공' },
  { id: 'work', label: '실무' },
]

export const VOICES: { id: Voice; label: string; sample: string }[] = [
  {
    id: 'textbook',
    label: '교과서체',
    sample:
      '프로세스는 실행 중인 프로그램이다. 운영체제는 각 프로세스에 독립된 주소 공간을 주고, 스레드는 그 주소 공간을 공유하며 실행 흐름만 따로 가진다. 따라서 스레드 사이의 전환은 프로세스 사이의 전환보다 가볍다.',
  },
  {
    id: 'explain',
    label: '설명체',
    sample:
      '프로그램을 실행하면 운영체제는 그 프로그램을 위한 작업 공간을 하나 만들어 줍니다. 이것이 프로세스입니다. 스레드는 같은 작업 공간 안에서 동시에 움직이는 일꾼이라고 보면 됩니다. 공간을 새로 만들 필요가 없으니 전환도 빠릅니다.',
  },
  {
    id: 'note',
    label: '노트체',
    sample:
      '프로세스: 실행 중인 프로그램, 독립된 주소 공간\n스레드: 프로세스 안의 실행 흐름, 주소 공간 공유\n전환 비용: 스레드 < 프로세스 (주소 공간 교체 없음)',
  },
]

/** The level picked until the reader picks another. */
export const DEFAULT_LEVEL: Level = 'course'

export const levelLabel = (id: string) => LEVELS.find((level) => level.id === id)?.label ?? null

export const voiceLabel = (id: string) => VOICES.find((voice) => voice.id === id)?.label ?? null
