import { useEffect, useRef, useState, type ButtonHTMLAttributes, type InputHTMLAttributes, type ReactNode } from 'react'
import { NavLink, useNavigate } from 'react-router-dom'
import { AnimatePresence, motion } from 'framer-motion'
import { Icon } from './Icon'
import { get, session } from '../lib/api'
import markUrl from '../assets/brand/mark.svg'
import './ui.css'

const cx = (...c: (string | false | null | undefined)[]) => c.filter(Boolean).join(' ')

/* ── Button ── */
type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: 'primary' | 'secondary' | 'ghost' | 'danger'
  size?: 'L' | 'S'
  block?: boolean
}
export function Button({ variant = 'primary', size = 'L', block = size === 'L', className, ...rest }: ButtonProps) {
  return (
    <button
      type="button"
      className={cx('btn', `btn-${variant}`, size === 'L' ? 'btn-l' : 'btn-s', block && 'btn-block', className)}
      {...rest}
    />
  )
}

/* ── Badge ── */
export type BadgeTone = '진행' | '확정' | '경고' | '완료' | '중립'
export function Badge({ tone = '진행', dot = true, children }: { tone?: BadgeTone; dot?: boolean; children: ReactNode }) {
  return (
    <span className={cx('badge', `badge-${tone}`)}>
      {dot && tone !== '중립' && <span className="badge-dot" />}
      {children}
    </span>
  )
}

/* ── Chip (복수 선택) ── */
export function Chip({ selected, onClick, children }: { selected?: boolean; onClick?: () => void; children: ReactNode }) {
  return (
    <button type="button" className="chip" aria-pressed={!!selected} onClick={onClick}>
      {children}
    </button>
  )
}

/* ── TextField ── */
type FieldProps = InputHTMLAttributes<HTMLInputElement> & { label?: string; action?: ReactNode; height?: number }
export function TextField({ label, action, height, className, ...rest }: FieldProps) {
  return (
    <label className={cx('field', className)}>
      {label && <span className="field-label">{label}</span>}
      <span className="field-box" style={height ? { height } : undefined}>
        <input {...rest} />
        {action}
      </span>
    </label>
  )
}

/* ── OTP 6칸 ── */
export function OtpInput({ value, onChange, cellWidth, height = 56 }: { value: string; onChange: (v: string) => void; cellWidth?: number; height?: number }) {
  const [focused, setFocused] = useState(false)
  return (
    <div className="otp" style={cellWidth ? { width: 'auto' } : undefined}>
      {Array.from({ length: 6 }).map((_, i) => (
        <div
          key={i}
          className={cx('otp-cell', focused && i === Math.min(value.length, 5) && 'is-active')}
          style={{ height, ...(cellWidth ? { flex: 'none', width: cellWidth } : null) }}
        >
          {value[i] ?? ''}
        </div>
      ))}
      <input
        inputMode="numeric"
        autoComplete="one-time-code"
        aria-label="인증번호 6자리"
        maxLength={6}
        value={value}
        onFocus={() => setFocused(true)}
        onBlur={() => setFocused(false)}
        onChange={(e) => onChange(e.target.value.replace(/\D/g, '').slice(0, 6))}
      />
    </div>
  )
}

/* ── PhoneInput ── */
export function PhoneInput({ value, onChange, onSubmit, autoFocus }: { value: string; onChange: (v: string) => void; onSubmit: () => void; autoFocus?: boolean }) {
  return (
    <form
      className="phone-input"
      onSubmit={(e) => {
        e.preventDefault()
        onSubmit()
      }}
    >
      <span className="prefix">+82</span>
      <span className="divider" />
      <input
        type="tel"
        inputMode="tel"
        placeholder="휴대폰 번호 입력"
        aria-label="휴대폰 번호"
        value={value}
        autoFocus={autoFocus}
        onChange={(e) => onChange(formatPhone(e.target.value))}
      />
      <button type="submit" className="submit" aria-label="다음">
        <Icon name="chevron-right" size={20} />
      </button>
    </form>
  )
}

export function formatPhone(v: string) {
  const d = v.replace(/\D/g, '').slice(0, 11)
  if (d.length < 4) return d
  if (d.length < 8) return `${d.slice(0, 3)}-${d.slice(3)}`
  return `${d.slice(0, 3)}-${d.slice(3, 7)}-${d.slice(7)}`
}

export function SocialButton({ provider, onClick }: { provider: 'kakao' | 'naver' | 'phone'; onClick?: () => void }) {
  const label = { kakao: '카카오로 시작하기', naver: '네이버로 시작하기', phone: '휴대폰 번호로 시작하기' }[provider]
  return (
    <button type="button" className={cx('social-btn', provider)} aria-label={label} onClick={onClick}>
      {provider === 'phone' ? <Icon name="phone" /> : <span className="social-logo-slot" />}
    </button>
  )
}

/* ── AppBar ── */
export function AppBar({ title, back = true, onBack, action }: { title?: ReactNode; back?: boolean; onBack?: () => void; action?: ReactNode }) {
  const navigate = useNavigate()
  return (
    <header className="app-bar">
      {back && (
        <button className="icon-btn" aria-label="뒤로" onClick={onBack ?? (() => navigate(-1))}>
          <Icon name="chevron-left" />
        </button>
      )}
      <h1 className="app-bar-title">{title}</h1>
      {action}
    </header>
  )
}

export function Logo({ wordmark = true }: { wordmark?: boolean }) {
  return (
    <span className="logo">
      <img src={markUrl} width={34} height={34} alt="" />
      {wordmark && <span className="logo-word">우연한 식탁</span>}
    </span>
  )
}

export function BellButton({ unread }: { unread?: boolean }) {
  const navigate = useNavigate()
  // 읽지 않은 알림이 있을 때만 빨간 점 (시안 세션은 Figma 처럼 표시)
  const [hasUnread, setHasUnread] = useState(!session.token || session.token === 'demo')
  useEffect(() => {
    if (unread !== undefined || !session.token || session.token === 'demo') return
    let alive = true
    get<{ readAt: string | null }[]>('/api/me/notifications')
      .then((list) => alive && setHasUnread(list.some((n) => !n.readAt)))
      .catch(() => alive && setHasUnread(false))
    return () => {
      alive = false
    }
  }, [unread])
  const dot = unread ?? hasUnread
  return (
    <button className="bell-btn" aria-label={dot ? '알림, 읽지 않은 알림 있음' : '알림'} onClick={() => navigate('/notifications')}>
      <Icon name="bell" />
      {dot && <span className="bell-dot" />}
    </button>
  )
}

/* ── AppHeader: 로고 + 알림 ── */
export function AppHeader({ unread }: { unread?: boolean }) {
  return (
    <header className="app-header">
      <span style={{ flex: 1 }}>
        <Logo />
      </span>
      <BellButton unread={unread} />
    </header>
  )
}

/* ── TabBar ── */
export function TabBar() {
  const navigate = useNavigate()
  const tab = (to: string, label: string, icon: 'home' | 'calendar' | 'chat' | 'user') => (
    <NavLink to={to} end={to === '/'} className={({ isActive }) => cx('tab-item', isActive && 'is-active')}>
      <Icon name={icon} />
      {label}
    </NavLink>
  )
  return (
    <nav className="tab-bar" aria-label="하단 탭">
      {tab('/', '홈', 'home')}
      {tab('/calendar', '캘린더', 'calendar')}
      <div className="tab-apply">
        <button className="tab-apply-btn" aria-label="신청하기" onClick={() => navigate('/apply')}>
          <Icon name="plus" />
        </button>
      </div>
      {tab('/rooms', '단체방', 'chat')}
      {tab('/my', '마이', 'user')}
    </nav>
  )
}

/* ── Screen 레이아웃 ── */
export function Screen({ children, className, style }: { children: ReactNode; className?: string; style?: React.CSSProperties }) {
  return (
    <motion.div
      className={cx('screen', className)}
      style={style}
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      transition={{ duration: 0.18 }}
    >
      {children}
    </motion.div>
  )
}

export function Body({ children, style, className }: { children: ReactNode; style?: React.CSSProperties; className?: string }) {
  return (
    <div className={cx('screen-body', className)} style={style}>
      {children}
    </div>
  )
}

export function Footer({ children, style }: { children: ReactNode; style?: React.CSSProperties }) {
  return (
    <div className="screen-footer" style={style}>
      {children}
    </div>
  )
}

/* ── Bottom sheet / Dialog ── */
export function Sheet({ open, onClose, children, className }: { open: boolean; onClose: () => void; children: ReactNode; className?: string }) {
  return (
    <AnimatePresence>
      {open && (
        <motion.div className="overlay" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} onClick={onClose}>
          <motion.div
            className={cx('sheet', className)}
            role="dialog"
            initial={{ y: 40 }}
            animate={{ y: 0 }}
            exit={{ y: 40 }}
            transition={{ ease: 'easeOut', duration: 0.22 }}
            onClick={(e) => e.stopPropagation()}
          >
            <span className="sheet-grip" />
            {children}
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  )
}

export function Dialog({ open, onClose, children }: { open: boolean; onClose: () => void; children: ReactNode }) {
  return (
    <AnimatePresence>
      {open && (
        <motion.div className="overlay center" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} onClick={onClose}>
          <motion.div
            className="dialog"
            role="alertdialog"
            initial={{ scale: 0.96 }}
            animate={{ scale: 1 }}
            exit={{ scale: 0.96 }}
            onClick={(e) => e.stopPropagation()}
          >
            {children}
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  )
}

/* ── Toast ── */
export function useToast() {
  const [msg, setMsg] = useState<string | null>(null)
  const t = useRef<number | undefined>(undefined)
  useEffect(() => () => window.clearTimeout(t.current), [])
  const show = (m: string) => {
    setMsg(m)
    window.clearTimeout(t.current)
    t.current = window.setTimeout(() => setMsg(null), 2200)
  }
  const node = (
    <AnimatePresence>
      {msg && (
        <motion.div className="toast" role="status" initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0 }}>
          {msg}
        </motion.div>
      )}
    </AnimatePresence>
  )
  return { show, node }
}

export { cx }
