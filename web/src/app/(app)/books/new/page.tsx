import type { Metadata } from 'next'
import { BookAdd } from '@/components/BookAdd'

export const metadata: Metadata = { title: '새 책' }

export default function NewBookPage() {
  return <BookAdd />
}
