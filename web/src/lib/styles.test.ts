import { describe, expect, it } from 'vitest'
import { LEVELS, levelLabel, VOICES, voiceLabel } from './styles'

describe('styles', () => {
  it('offers the server codes, each with its label', () => {
    expect(LEVELS.map((level) => level.id)).toEqual(['intro', 'course', 'work'])
    expect(VOICES.map((voice) => `${voice.id}:${voice.label}`)).toEqual([
      'textbook:교과서체',
      'explain:설명체',
      'note:노트체',
    ])
  })

  it('names a code by its label, and a code it does not know by nothing', () => {
    expect(levelLabel('work')).toBe('실무')
    expect(voiceLabel('note')).toBe('노트체')
    expect(voiceLabel('poem')).toBeNull()
  })

  it('shows one subject in every voice, so only the voice differs', () => {
    for (const voice of VOICES) expect(voice.sample).toContain('스레드')
  })
})
