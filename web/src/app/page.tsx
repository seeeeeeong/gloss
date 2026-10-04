import { ThemeToggle } from '@/components/ThemeToggle'

export default function Home() {
  return (
    <>
      <header className="topbar">
        <a className="brand" href="/">
          Gloss
        </a>
        <span className="sp" />
        <ThemeToggle />
      </header>
      <main className="placeholder">
        <h1>Gloss</h1>
        <p>책의 목차를 따라 학습 글을 만들고, 글 옆에서 튜터에게 묻는 곳입니다. 준비 중입니다.</p>
      </main>
    </>
  )
}
