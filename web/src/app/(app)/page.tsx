import { redirect } from 'next/navigation'
import { HOME } from '@/lib/session'

export default function Home() {
  redirect(HOME)
}
