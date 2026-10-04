import localFont from 'next/font/local'

/** Body text. Served from this app, never a third-party CDN. */
export const pretendard = localFont({
  src: '../../node_modules/pretendard/dist/web/variable/woff2/PretendardVariable.woff2',
  variable: '--font-pretendard',
  weight: '45 920',
  display: 'swap',
})

/**
 * Code and ASCII diagrams. Its Hangul is exactly twice the width of its Latin,
 * which is what keeps a diagram with Korean labels aligned. The full set, not the
 * KS X 1001 subset: a post may use any syllable.
 */
export const d2coding = localFont({
  src: [
    { path: '../../node_modules/d2coding/fonts/d2coding-full.woff2', weight: '400' },
    { path: '../../node_modules/d2coding/fonts/d2coding-bold-full.woff2', weight: '700' },
  ],
  variable: '--font-d2coding',
  display: 'swap',
})
