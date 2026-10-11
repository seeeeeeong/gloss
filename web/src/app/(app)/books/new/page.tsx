import type { Metadata } from 'next'
import { BookCreateForm } from '@/components/BookCreateForm'

export const metadata: Metadata = { title: '책 추가' }

export default function NewBookPage() {
  return (
    <div className="page">
      <h1 className="page-title">책 추가</h1>
      <BookCreateForm />
    </div>
  )
}
