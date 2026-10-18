import type { Metadata, Viewport } from 'next'
import { cookies } from 'next/headers'
import { ThemeProvider } from '@/app/_components/ThemeProvider'
import { THEME_COOKIE, themeAttribute, themeColors, themeFrom } from '@/lib/theme'
import 'pretendard/dist/web/variable/pretendardvariable-dynamic-subset.css'
import '@fontsource/gowun-batang/400.css'
import '@fontsource/gowun-batang/700.css'
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
    <html lang="ko" data-theme={themeAttribute(theme)}>
      <body>
        <ThemeProvider theme={theme}>{children}</ThemeProvider>
      </body>
    </html>
  )
}
