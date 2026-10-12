import type { Metadata } from 'next'
import { BookAdd } from '@/components/BookAdd'

export const metadata: Metadata = { title: '책 추가' }

export default function NewBookPage() {
  return (
    <div className="page">
      <h1 className="page-title">책 추가</h1>
      <BookAdd />
    </div>
  )
}
