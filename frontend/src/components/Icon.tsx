import type { CSSProperties } from 'react'

// Figma 아이콘(24 · stroke 1.75)은 잉크색이 박혀 있어 currentColor 로 바꿔 화면별 색을 입힌다.
const raw = import.meta.glob('../assets/icons/*.svg', { query: '?raw', import: 'default', eager: true }) as Record<string, string>

const icons: Record<string, string> = {}
for (const [path, svg] of Object.entries(raw)) {
  const name = path.split('/').pop()!.replace('.svg', '')
  icons[name] = svg
    .replace(/stroke="#4C4541"/gi, 'stroke="currentColor"')
    .replace(/fill="#4C4541"/gi, 'fill="currentColor"')
    .replace(/ style="display: block;"/, '')
    .replace(/ width="[\d.]+" height="[\d.]+"/, ' width="100%" height="100%"')
}

export type IconName =
  | 'home' | 'calendar' | 'chat' | 'user' | 'chevron-left' | 'chevron-right' | 'bell' | 'check'
  | 'map-pin' | 'clock' | 'users' | 'alert' | 'send' | 'grid' | 'shuffle' | 'utensils' | 'card'
  | 'flag' | 'star' | 'search' | 'plus' | 'refresh' | 'calendar-check' | 'megaphone' | 'sparkle'
  | 'download' | 'shield' | 'ticket' | 'trash' | 'sliders' | 'info' | 'phone' | 'copy' | 'chevron-down'

type Props = {
  name: IconName
  size?: number
  color?: string
  className?: string
  style?: CSSProperties
  label?: string
}

export function Icon({ name, size = 24, color, className, style, label }: Props) {
  return (
    <span
      className={className}
      role={label ? 'img' : undefined}
      aria-label={label}
      aria-hidden={label ? undefined : true}
      style={{ display: 'inline-flex', width: size, height: size, flexShrink: 0, color, ...style }}
      dangerouslySetInnerHTML={{ __html: icons[name] ?? '' }}
    />
  )
}
