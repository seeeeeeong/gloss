import type { Metadata } from 'next'

export const metadata: Metadata = { title: '내 책장' }

export default function ShelfPage() {
  return (
    <div className="page">
      <h1 className="page-title">내 책장</h1>
      <div className="empty">아직 책이 없습니다. 책 추가는 곧 열립니다.</div>
    </div>
  )
}
