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
  shield: <path d="M10 2.75 4.25 5v4.6c0 3.5 2.35 6.15 5.75 7.65 3.4-1.5 5.75-4.15 5.75-7.65V5z" />,
  account: (
    <>
      <circle cx="10" cy="10" r="7.25" />
      <circle cx="10" cy="8.25" r="2.5" />
      <path d="M5.6 15.1a5.2 5.2 0 0 1 8.8 0" />
    </>
  ),
  menu: <path d="M3.75 7.25h12.5M3.75 12.75h7.5" />,
  plus: <path d="M10 4.25v11.5M4.25 10h11.5" />,
  send: <path d="M10 15.5V4.75M5.25 9.5 10 4.75l4.75 4.75" strokeWidth="2" />,
  chevR: <path d="M8 5.5 12.5 10 8 14.5" />,
  upRight: <path d="M6.5 13.5l7-7M8 6.5h5.5V12" />,
  arrowR: <path d="M4.5 10h11M11 5.5l4.5 4.5-4.5 4.5" />,
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
