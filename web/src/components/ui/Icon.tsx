import type { ReactNode } from 'react'

// The v4 set: a 20px grid at ChatGPT's 1.6 stroke.
const SHAPES = {
  sidebar: (
    <>
      <rect x="2.75" y="3.75" width="14.5" height="12.5" rx="3" />
      <path d="M7.75 3.75v12.5" />
    </>
  ),
  newbook: (
    <>
      <path d="M9.25 3.75H6.5a2.75 2.75 0 0 0-2.75 2.75v7A2.75 2.75 0 0 0 6.5 16.25h7a2.75 2.75 0 0 0 2.75-2.75v-2.75" />
      <path d="M14.4 3.1a1.55 1.55 0 0 1 2.2 2.2l-6.1 6.1-2.9.7.7-2.9z" />
    </>
  ),
  book: <path d="M4.25 15.5V5a2 2 0 0 1 2-2h9.5v11H6.25a2 2 0 0 0-2 2v0a2 2 0 0 0 2 2h9.5" />,
  spread: (
    <>
      <path d="M10 5.5C8.4 4.3 6.3 3.75 3.75 3.75v11c2.55 0 4.65.55 6.25 1.75 1.6-1.2 3.7-1.75 6.25-1.75v-11c-2.55 0-4.65.55-6.25 1.75z" />
      <path d="M10 5.5v11" />
    </>
  ),
  doc: (
    <>
      <path d="M5.75 2.75h5.5l3.5 3.5v10a1 1 0 0 1-1 1h-8a1 1 0 0 1-1-1V3.75a1 1 0 0 1 1-1z" />
      <path d="M11.25 2.75v3.5h3.5M7.5 10.25h5M7.5 13.25h3.5" />
    </>
  ),
  shield: <path d="M10 2.75 4.25 5v4.6c0 3.5 2.35 6.15 5.75 7.65 3.4-1.5 5.75-4.15 5.75-7.65V5z" />,
  account: (
    <>
      <circle cx="10" cy="10" r="7.25" />
      <circle cx="10" cy="8.25" r="2.5" />
      <path d="M5.6 15.1a5.2 5.2 0 0 1 8.8 0" />
    </>
  ),
  panelR: (
    <>
      <rect x="2.75" y="3.75" width="14.5" height="12.5" rx="3" />
      <path d="M12.25 3.75v12.5" />
    </>
  ),
  menu: <path d="M3.75 7.25h12.5M3.75 12.75h7.5" />,
  plus: <path d="M10 4.25v11.5M4.25 10h11.5" />,
  minus: <path d="M4.25 10h11.5" />,
  send: <path d="M10 15.5V4.75M5.25 9.5 10 4.75l4.75 4.75" strokeWidth="2" />,
  stop: <rect x="6.5" y="6.5" width="7" height="7" rx="1.25" fill="currentColor" stroke="none" />,
  chevR: <path d="M8 5.5 12.5 10 8 14.5" />,
  upRight: <path d="M6.5 13.5l7-7M8 6.5h5.5V12" />,
  arrowR: <path d="M4.5 10h11M11 5.5l4.5 4.5-4.5 4.5" />,
  arrowL: <path d="M15.5 10h-11M9 5.5 4.5 10 9 14.5" />,
  search: (
    <>
      <circle cx="9" cy="9" r="5.25" />
      <path d="m13 13 3.25 3.25" />
    </>
  ),
  globe: (
    <>
      <circle cx="10" cy="10" r="6.75" />
      <path d="M3.25 10h13.5M10 3.25c1.8 2 2.7 4.25 2.7 6.75s-.9 4.75-2.7 6.75c-1.8-2-2.7-4.25-2.7-6.75S8.2 5.25 10 3.25z" />
    </>
  ),
  chevL: <path d="M12 5.5 7.5 10 12 14.5" />,
  chevD: <path d="M5.5 8 10 12.5 14.5 8" />,
  x: <path d="m5.5 5.5 9 9M14.5 5.5l-9 9" />,
  copy: (
    <>
      <rect x="7.25" y="7.25" width="9" height="9" rx="2.25" />
      <path d="M12.75 7.25V5.75a2 2 0 0 0-2-2h-5a2 2 0 0 0-2 2v5a2 2 0 0 0 2 2h1.5" />
    </>
  ),
  check: <path d="m4.75 10.25 3.5 3.5 7-7.5" />,
  quote: (
    <path d="M8.5 6.25c-2 .6-3.5 2.2-3.5 4.6v2.9h3.5v-3.5H5.3M15.25 6.25c-2 .6-3.5 2.2-3.5 4.6v2.9h3.5v-3.5h-3.2" />
  ),
  chat: (
    <path d="M4.25 9.75c0-3 2.6-5.25 5.75-5.25s5.75 2.25 5.75 5.25S13.15 15 10 15c-.8 0-1.55-.13-2.25-.38L4.75 15.5l.8-2.6a4.9 4.9 0 0 1-1.3-3.15z" />
  ),
  more: (
    <>
      <circle cx="4.75" cy="10" r="1.15" fill="currentColor" stroke="none" />
      <circle cx="10" cy="10" r="1.15" fill="currentColor" stroke="none" />
      <circle cx="15.25" cy="10" r="1.15" fill="currentColor" stroke="none" />
    </>
  ),
  key: (
    <>
      <circle cx="7.25" cy="12.75" r="3.5" />
      <path d="m9.75 10.25 6-6M13.5 6.5l2 2M11.75 8.25l1.5 1.5" />
    </>
  ),
  ban: (
    <>
      <circle cx="10" cy="10" r="6.75" />
      <path d="m5.25 14.75 9.5-9.5" />
    </>
  ),
  restore: (
    <>
      <path d="M4.25 10a5.75 5.75 0 1 0 1.7-4.07L4.25 7.6" />
      <path d="M4.25 4.25V7.6H7.6" />
    </>
  ),
  alert: (
    <>
      <circle cx="10" cy="10" r="7.25" />
      <path d="M10 6.5v4.25M10 13.6h.01" />
    </>
  ),
  library: (
    <>
      <path d="M3.25 3.25v13.5M7.5 3.25v13.5M11.75 3.25l4.25 13.5" />
      <path d="M2.5 5.75h2.25M6.75 5.75h2.25M11.75 5.75l2.5-.75" />
    </>
  ),
  history: (
    <>
      <path d="M4.25 10a5.75 5.75 0 1 0 1.7-4.07L4.25 7.6" />
      <path d="M4.25 4.25V7.6H7.6" />
      <path d="M10 7.25V10l2 1.5" />
    </>
  ),
  trash: (
    <path d="M4.25 5.75h11.5M8.25 5.75V4.5a1 1 0 0 1 1-1h1.5a1 1 0 0 1 1 1v1.25M5.75 5.75l.7 9.6a1.5 1.5 0 0 0 1.5 1.4h4.1a1.5 1.5 0 0 0 1.5-1.4l.7-9.6" />
  ),
  bookmark: <path d="M5 3.25h10v14.25l-5-3.25-5 3.25z" />,
  text: <path d="m2.5 15.75 5-11.5 5 11.5M4.25 11.75h6.5m2.5-2.5h5m-2.5 0v6.5" />,
  moon: <path d="M16.75 11.75A6.85 6.85 0 0 1 8.25 3.25a7.1 7.1 0 1 0 8.5 8.5Z" />,
  clock: (
    <>
      <circle cx="10" cy="10" r="7.1" />
      <path d="M10 5.75V10l2.5 1.75" />
    </>
  ),
  sun: (
    <>
      <circle cx="10" cy="10" r="3.25" />
      <path d="M10 1.75v1.5m0 13.5v1.5M1.75 10h1.5m13.5 0h1.5M4.25 4.25l1.25 1.25m9 9 1.25 1.25M4.25 15.75l1.25-1.25m9-9 1.25-1.25" />
    </>
  ),
  grid: (
    <>
      <rect x="3.25" y="3.25" width="5" height="5" rx="1" />
      <rect x="11.75" y="3.25" width="5" height="5" rx="1" />
      <rect x="3.25" y="11.75" width="5" height="5" rx="1" />
      <rect x="11.75" y="11.75" width="5" height="5" rx="1" />
    </>
  ),
  list: (
    <>
      <path d="M7.5 4.25h9.25M7.5 10h9.25M7.5 15.75h9.25" />
      <path d="M3.25 4.25h.01M3.25 10h.01M3.25 15.75h.01" strokeWidth="2.5" />
    </>
  ),
} satisfies Record<string, ReactNode>

export type IconName = keyof typeof SHAPES

export function Icon({ name, size = 20 }: { name: IconName; size?: number }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 20 20"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.6"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
    >
      {SHAPES[name]}
    </svg>
  )
}
