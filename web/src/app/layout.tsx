import type { Metadata, Viewport } from 'next'
import { cookies } from 'next/headers'
import { THEME_COOKIE, themeAttribute, themeColors, themeFrom } from '@/lib/theme'
import { d2coding, pretendard } from './fonts'
import './globals.css'

export const metadata: Metadata = {
  title: { default: 'Gloss', template: '%s · Gloss' },
  description: '책의 목차를 따라 만든 학습 글과, 글 옆에서 묻는 튜터',
}

export async function generateViewport(): Promise<Viewport> {
  const theme = themeFrom((await cookies()).get(THEME_COOKIE)?.value)
  return { themeColor: themeColors(theme), interactiveWidget: 'resizes-content' }
}

export default async function RootLayout({ children }: LayoutProps<'/'>) {
  const theme = themeFrom((await cookies()).get(THEME_COOKIE)?.value)
  return (
    <html
      lang="ko"
      className={`${pretendard.variable} ${d2coding.variable}`}
      data-theme={themeAttribute(theme)}
    >
      <body>{children}</body>
    </html>
  )
}
