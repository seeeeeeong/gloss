import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it } from 'vitest'
import { SetupStyle } from '@/features/setup/components/SetupStyle'

const noop = () => {}

const render = (blocked = false) =>
  renderToStaticMarkup(<SetupStyle starting={null} blocked={blocked} onStart={noop} />)

describe('SetupStyle', () => {
  it('asks the level, 전공 picked until another is, then shows the three voices with their samples', () => {
    const out = render()

    expect(out).toContain('<p>글은 어떤 문체로 쓸까요?</p>')
    expect(out).toMatch(/<span class="lv-l" id="[^"]+">수준<\/span>/)
    expect(out.match(/type="radio"/g)).toHaveLength(3)
    expect(out).toMatch(/checked="" value="course"/)
    expect(out).toContain('<span>입문</span>')
    expect(out).toContain('<span>실무</span>')
    expect(out).toContain('<ul class="voices" aria-label="문체">')
    expect(out).toContain(
      '<span class="v-l">교과서체</span><span class="v-s">프로세스는 실행 중인 프로그램이다.',
    )
    expect(out).toContain('<span class="v-l">설명체</span>')
    expect(out).toContain('<span class="v-l">노트체</span>')
    expect(out).toContain('aria-label="설명체, 이 문체로 시작"')
  })

  it('holds every voice while the contents are being edited', () => {
    expect(render(true).match(/aria-disabled="true"/g)).toHaveLength(3)
    expect(render().match(/aria-disabled="false"/g)).toHaveLength(3)
  })
})
