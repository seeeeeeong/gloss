import type { Metadata } from 'next'

export const metadata: Metadata = { title: '내 책장' }

export default function ShelfPage() {
  return (
    <div className="page">
      <h1 className="page-title">내 책장</h1>
      <p className="page-lead">책 제목으로 목차를 가져오고, 목차마다 학습 글을 만듭니다.</p>
      <div className="empty">아직 책이 없습니다. 책 추가는 곧 열립니다.</div>
    </div>
  )
}
