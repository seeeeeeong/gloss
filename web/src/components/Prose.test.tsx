import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it } from 'vitest'
import { Prose } from './Prose'

const html = (markdown: string) => renderToStaticMarkup(<Prose markdown={markdown} />)

describe('Prose', () => {
  it('renders headings, emphasis, code and tables', () => {
    const out = html('## 버퍼 풀\n\n**페이지**는 `16KiB`다.\n\n| 변수 | 값 |\n|---|---|\n| a | 1 |')
    expect(out).toContain('<h2>버퍼 풀</h2>')
    expect(out).toContain('<strong>페이지</strong>')
    expect(out).toContain('<code>16KiB</code>')
    expect(out).toContain('<div class="table-wrap"><table>')
  })

  it('keeps raw HTML as text, never as markup', () => {
    const out = html('앞 <script>alert(1)</script> <img src=x onerror=alert(1)> 뒤')
    expect(out).toContain('&lt;script&gt;')
    expect(out).not.toContain('<script')
    expect(out).not.toContain('<img')
  })

  it('drops a link whose scheme could run code', () => {
    const out = html('[눌러 보세요](javascript:alert(1))')
    expect(out).not.toContain('javascript:')
  })

  it('opens a link in a new tab without handing over the opener', () => {
    const out = html('[문서](https://example.com/a)')
    expect(out).toContain('href="https://example.com/a"')
    expect(out).toContain('rel="noopener noreferrer"')
  })

  it("shows no image, so no host of the model's choosing is called", () => {
    expect(html('![그림](https://example.com/a.png)')).not.toContain('<img')
  })

  it('keeps the key terms in one block, so a page turn does not split them', () => {
    const out = html('## 요약\n\n- 하나\n\n## 핵심 용어\n\n- **커널**: 중심 프로그램')
    expect(out).toContain('<h2>요약</h2>')
    expect(out).toMatch(/<section class="keep"><h2>핵심 용어<\/h2>\s*<ul>/)
    expect(html('## 요약\n\n- 하나')).not.toContain('keep')
  })

  it('takes a fenced "## 핵심 용어" for code, never the start of the key terms', () => {
    const md =
      '## 1. 절\n\n```md\n## 핵심 용어\n- 예시\n```\n\n본문\n\n## 핵심 용어\n\n- **커널**: 중심'
    const out = html(md)
    expect(out).toContain('<pre><code class="language-md">## 핵심 용어\n- 예시\n</code></pre>')
    expect(out).toMatch(/<p>본문<\/p>\s*<section class="keep"><h2>핵심 용어<\/h2>/)
    expect(out.match(/<section/g)).toHaveLength(1)
  })
})
