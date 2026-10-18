import { spawnSync } from 'node:child_process'
import { mkdirSync, mkdtempSync, readdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { dirname, join } from 'node:path'
import { afterAll, describe, expect, it } from 'vitest'

const WEB = join(import.meta.dirname, '..')
const BIOME = join(WEB, 'node_modules', '.bin', 'biome')
const config = JSON.parse(readFileSync(join(WEB, 'biome.json'), 'utf8'))

// A scratch project with the real rules, outside git so Biome lints whatever is written there.
const project = mkdtempSync(join(tmpdir(), 'gloss-imports-'))
writeFileSync(
  join(project, 'biome.json'),
  JSON.stringify({ ...config, $schema: undefined, vcs: { enabled: false } }),
)
afterAll(() => rmSync(project, { recursive: true, force: true }))

type Diagnostic = { category: string }

function restricts(path: string, specifier: string): boolean {
  mkdirSync(join(project, dirname(path)), { recursive: true })
  writeFileSync(join(project, path), `import { x } from '${specifier}'\nexport const y = x\n`)
  const run = spawnSync(BIOME, ['lint', '--reporter=json', path], {
    cwd: project,
    encoding: 'utf8',
  })
  const report: { diagnostics: Diagnostic[] } = JSON.parse(run.stdout)
  return report.diagnostics.some((d) => d.category === 'lint/style/noRestrictedImports')
}

describe('imports across folders', () => {
  it('keeps a feature out of another feature and out of the routes', () => {
    expect(restricts('src/features/reader/a.ts', '@/features/books/api/server-books')).toBe(true)
    expect(restricts('src/features/reader/b.ts', '@/app/(app)/_components/shell')).toBe(true)
  })

  it('lets a feature import itself and shared code', () => {
    expect(restricts('src/features/reader/c.ts', '@/features/reader/utils/reader')).toBe(false)
    expect(restricts('src/features/reader/d.ts', '@/lib/books')).toBe(false)
  })

  it.each(['src/components/ui/e.tsx', 'src/hooks/e.ts', 'src/lib/e.ts', 'src/utils/e.ts'])(
    'keeps shared code in %s off the features',
    (path) => {
      expect(restricts(path, '@/features/reader/utils/reader')).toBe(true)
    },
  )

  it('lets a route compose features', () => {
    expect(restricts('src/app/(app)/f.tsx', '@/features/reader/views/Reader')).toBe(false)
  })

  it('sends an import from another folder through @/', () => {
    expect(restricts('src/app/g.ts', '../lib/books')).toBe(true)
    expect(restricts('src/features/reader/views/h.ts', '../utils/reader')).toBe(true)
    expect(restricts('test/i.test.ts', '../src/lib/books')).toBe(true)
    expect(restricts('src/lib/j.ts', './../features/reader/utils/reader')).toBe(true)
    expect(restricts('src/lib/k.ts', './a/../../features/reader/utils/reader')).toBe(true)
  })

  it('lets a file import its own folder', () => {
    expect(restricts('src/features/reader/views/l.ts', './Reader')).toBe(false)
  })

  it('has a rule for every feature folder', () => {
    const guarded = config.overrides.flatMap((o: { includes: string[] }) => o.includes)
    const features = readdirSync(join(WEB, 'src', 'features'))
    for (const feature of features) expect(guarded).toContain(`src/features/${feature}/**`)
  })
})
