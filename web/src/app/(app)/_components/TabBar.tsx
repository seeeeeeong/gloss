import './tabbar.css'
import Link from 'next/link'
import { Icon, type IconName } from '@/components/ui/Icon'

type Tab = { href: string; label: string; icon: IconName; at: (path: string) => boolean }

const TABS: Tab[] = [
  { href: '/', label: '내 서재', icon: 'library', at: (path) => path === '/' },
  {
    href: '/books/new',
    label: '새 책',
    icon: 'plus',
    at: (path) => path === '/books/new' || path.startsWith('/books/new/'),
  },
  { href: '/bookmarks', label: '책갈피', icon: 'bookmark', at: (path) => path === '/bookmarks' },
]

/** A phone's way between the shelf, a new book and the bookmarks, at the bottom as the prototype has it. */
export function TabBar({ path }: { path: string }) {
  return (
    <nav className="tabbar" aria-label="탭 메뉴">
      {TABS.map((tab) => (
        <Link key={tab.href} href={tab.href} aria-current={tab.at(path) ? 'page' : undefined}>
          <Icon name={tab.icon} />
          {tab.label}
        </Link>
      ))}
    </nav>
  )
}
