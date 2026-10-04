import localFont from 'next/font/local'

export const pretendard = localFont({
  src: '../../node_modules/pretendard/dist/web/variable/woff2/PretendardVariable.woff2',
  variable: '--font-pretendard',
  weight: '45 920',
  display: 'swap',
})

// Hangul exactly twice Latin width keeps diagrams aligned. Full set: a post may use any syllable.
export const d2coding = localFont({
  src: [
    { path: '../../node_modules/d2coding/fonts/d2coding-full.woff2', weight: '400' },
    { path: '../../node_modules/d2coding/fonts/d2coding-bold-full.woff2', weight: '700' },
  ],
  variable: '--font-d2coding',
  display: 'swap',
})
