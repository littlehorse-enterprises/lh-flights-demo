import type { ReactNode } from 'react'

type IconName = 'activity' | 'alert' | 'arrow' | 'check' | 'clock' | 'dollar' | 'inbox' | 'ledger' | 'phone' | 'route' | 'spark' | 'truck' | 'user' | 'x'

const paths: Record<IconName, ReactNode> = {
  activity: <><path d="M3 12h4l2.5-7 5 14 2.5-7h4" /></>,
  alert: <><path d="M12 3 2.8 19h18.4L12 3Z" /><path d="M12 9v4M12 17h.01" /></>,
  arrow: <><path d="m9 18 6-6-6-6" /></>,
  check: <><path d="m5 12 4 4L19 6" /></>,
  clock: <><circle cx="12" cy="12" r="9" /><path d="M12 7v5l3 2" /></>,
  dollar: <><circle cx="12" cy="12" r="9" /><path d="M16 8.5c-.8-1-2-1.5-4-1.5-2.2 0-3.5 1.1-3.5 2.5 0 3.8 7 1.6 7 5 0 1.5-1.4 2.5-3.5 2.5-1.7 0-3.1-.5-4-1.5M12 5v14" /></>,
  inbox: <><path d="M4 5h16v14H4z" /><path d="M4 14h4l2 2h4l2-2h4" /></>,
  ledger: <><path d="M6 3h12v18H6zM9 7h6M9 11h6M9 15h4" /></>,
  phone: <><path d="M6.5 3h3l1.2 5-2 1.2a14 14 0 0 0 6.1 6.1l1.2-2 5 1.2v3c0 1.9-1.6 3.5-3.5 3.5A14.5 14.5 0 0 1 3 6.5C3 4.6 4.6 3 6.5 3Z" /></>,
  route: <><circle cx="6" cy="18" r="2" /><circle cx="18" cy="6" r="2" /><path d="M8 18h2a3 3 0 0 0 3-3V9a3 3 0 0 1 3-3" /></>,
  spark: <><path d="m12 3 1.3 4.2L17 9l-3.7 1.8L12 15l-1.3-4.2L7 9l3.7-1.8L12 3ZM5 15l.7 2.3L8 18l-2.3.7L5 21l-.7-2.3L2 18l2.3-.7L5 15ZM19 13l.6 1.4L21 15l-1.4.6L19 17l-.6-1.4L17 15l1.4-.6L19 13Z" /></>,
  truck: <><path d="M3 6h11v11H3zM14 10h4l3 3v4h-7z" /><circle cx="7" cy="18" r="2" /><circle cx="18" cy="18" r="2" /></>,
  user: <><circle cx="12" cy="8" r="4" /><path d="M4 21a8 8 0 0 1 16 0" /></>,
  x: <><path d="m6 6 12 12M18 6 6 18" /></>,
}

export function Icon({ name, size = 18 }: { name: IconName; size?: number }) {
  return <svg aria-hidden="true" className="icon" width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">{paths[name]}</svg>
}
