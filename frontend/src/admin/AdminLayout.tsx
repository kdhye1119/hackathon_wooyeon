import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react'
import { NavLink, useNavigate } from 'react-router-dom'
import { Icon, type IconName } from '../components/Icon'
import { adminSession, get } from '../lib/api'
import markPaper from '../assets/brand/mark-paper.svg'
import './admin.css'

const cx = (...c: (string | false | null | undefined)[]) => c.filter(Boolean).join(' ')

/** 운영자 API 조회. 운영자 로그인 전이거나 백엔드가 꺼져 있으면 Figma 시안 데이터로 그린다. */
export function useAdminApi<T>(fetcher: () => Promise<T>, fallback: T, deps: unknown[] = []) {
  const [data, setData] = useState<T>(fallback)
  const [demo, setDemo] = useState(false)
  const [loading, setLoading] = useState(true)
  const load = useCallback(async () => {
    if (!adminSession.token || adminSession.token === 'demo') {
      setData(fallback)
      setDemo(true)
      setLoading(false)
      return
    }
    setLoading(true)
    try {
      setData(await fetcher())
      setDemo(false)
    } catch {
      setData(fallback)
      setDemo(true)
    } finally {
      setLoading(false)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps)
  useEffect(() => {
    load()
  }, [load])
  return { data, setData, demo, loading, reload: load }
}

const NAV: [string, IconName, string][] = [
  ['/admin', 'grid', '운영 대시보드'],
  ['/admin/slots', 'calendar', '회차 관리'],
  ['/admin/regions', 'map-pin', '지역 관리'],
  ['/admin/venues', 'utensils', '식당 관리'],
  ['/admin/policy', 'sliders', '매칭 규칙'],
  ['/admin/applicants', 'users', '신청자 목록'],
  ['/admin/payments', 'card', '결제 정보'],
  ['/admin/matching', 'shuffle', '매칭 정보'],
  ['/admin/exceptions', 'alert', '예외 처리함'],
  ['/admin/messages', 'megaphone', '자동 메시지'],
  ['/admin/attendance', 'calendar-check', '참석/노쇼'],
  ['/admin/feedback', 'flag', '피드백/신고'],
]

function Sidebar() {
  const navigate = useNavigate()
  const ex = useAdminApi(() => get<unknown[]>('/api/admin/exceptions').then((l) => l.length), 10)
  // 권한별 접근 통제: 로그인한 운영자 권한 표시
  const me = useAdminApi(() => get<{ role: string }>('/api/admin/me'), { role: 'OWNER' })
  const ROLE: Record<string, string> = { OWNER: '권한: 총괄 (전체)', OPERATOR: '권한: 운영 매니저 (정책 변경 제외)', VIEWER: '권한: 조회 전용' }
  return (
    <aside className="adm-sidebar">
      <div className="adm-logo">
        <img src={markPaper} width={34} height={34} alt="" />
        <span>우연한 식탁</span>
      </div>
      <p className="adm-sub">운영자 어드민/와썹하우스</p>
      <nav className="adm-nav">
        {NAV.map(([to, icon, label]) => (
          <NavLink key={to} to={to} end={to === '/admin'} className={({ isActive }) => cx('adm-nav-item', isActive && 'on')}>
            <Icon name={icon} size={18} />
            <span className="label">{label}</span>
            {to === '/admin/exceptions' && ex.data > 0 && <em className="adm-count">{ex.data}</em>}
          </NavLink>
        ))}
      </nav>
      <div className="adm-me">
        {/* 시안 세션은 Figma 예시, 실제 로그인은 로그인한 운영자 이름 */}
        <span className="adm-me-avatar">{adminSession.token === 'demo' ? '운' : (adminSession.name ?? '운').slice(0, 1)}</span>
        <span className="stack">
          <b>{adminSession.token === 'demo' ? '운영자 김와썹' : adminSession.name ?? '운영자'}</b>
          <small>{adminSession.token === 'demo' ? '권한: 매칭/결제/안전' : ROLE[me.data.role] ?? me.data.role}</small>
        </span>
        <button
          className="adm-logout"
          title="로그아웃"
          onClick={() => {
            adminSession.clear()
            navigate('/admin/login')
          }}
        >
          로그아웃
        </button>
      </div>
    </aside>
  )
}

/**
 * 서비스 시각. 백엔드는 시연용으로 시각을 앞당길 수 있어(/api/admin/demo/clock) 실제 시각과 다를 수 있다.
 * 서버 시각과의 차이를 한 번 받아 두고 화면의 "지금"·경과 시간 계산에 쓴다.
 */
let clockOffset = 0
let clockLoaded: Promise<void> | null = null
export const serviceNow = () => new Date(Date.now() + clockOffset)
function loadClock() {
  if (!clockLoaded && adminSession.token && adminSession.token !== 'demo')
    clockLoaded = get<{ now: string }>('/api/admin/demo/clock')
      .then((r) => {
        clockOffset = new Date(r.now).getTime() - Date.now()
      })
      .catch(() => undefined)
  return clockLoaded ?? Promise.resolve()
}
export function useServiceNow() {
  const [now, setNow] = useState(serviceNow)
  useEffect(() => {
    loadClock().then(() => setNow(serviceNow()))
    const t = window.setInterval(() => setNow(serviceNow()), 30000)
    return () => window.clearInterval(t)
  }, [])
  return now
}

function Clock() {
  const now = useServiceNow()
  const D = ['일', '월', '화', '수', '목', '금', '토']
  const p = (n: number) => String(n).padStart(2, '0')
  return (
    <span className="adm-clock">
      {now.getFullYear()}.{p(now.getMonth() + 1)}.{p(now.getDate())} ({D[now.getDay()]}) {p(now.getHours())}:{p(now.getMinutes())}
    </span>
  )
}

/** 사이드바 + 상단 바(제목·검색·시각) */
export function AdminLayout({ title, children, onBack }: { title: ReactNode; children: ReactNode; onBack?: () => void }) {
  const navigate = useNavigate()
  const [q, setQ] = useState('')
  return (
    <div className="adm">
      <Sidebar />
      <main className="adm-main">
        <header className="adm-top">
          {onBack && (
            <button className="icon-btn" aria-label="뒤로" onClick={onBack}>
              <Icon name="chevron-left" />
            </button>
          )}
          <h1 className="t-h1" style={{ flex: 1 }}>
            {title}
          </h1>
          <form
            className="adm-search"
            onSubmit={(e) => {
              e.preventDefault()
              navigate(`/admin/applicants?q=${encodeURIComponent(q)}`)
            }}
          >
            <input value={q} onChange={(e) => setQ(e.target.value)} placeholder="이름, 연락처, 테이블 ID 검색" aria-label="검색" />
            <button type="submit" aria-label="검색">
              <Icon name="search" size={16} />
            </button>
          </form>
          <Clock />
        </header>
        {children}
      </main>
    </div>
  )
}

export function Panel({ title, action, children, className, style }: { title?: ReactNode; action?: ReactNode; children: ReactNode; className?: string; style?: React.CSSProperties }) {
  return (
    <section className={cx('adm-panel', className)} style={style}>
      {(title || action) && (
        <div className="adm-panel-head">
          {title && <h2 className="t-h2">{title}</h2>}
          {action}
        </div>
      )}
      {children}
    </section>
  )
}

export type Column<T> = {
  key: string
  label: string
  width?: number | string
  render: (row: T) => ReactNode
  /** 있으면 머리글에서 정렬·값 필터 메뉴를 연다 */
  sort?: (row: T) => string | number
  /** 필터 목록에 보일 값. 없으면 셀 글자(없으면 정렬 값) */
  filter?: (row: T) => string
}

/** 셀 ReactNode 에서 글자만 뽑는다 (필터 값 이름용) */
function nodeText(n: ReactNode): string {
  if (n === null || n === undefined || typeof n === 'boolean') return ''
  if (typeof n === 'string' || typeof n === 'number') return String(n)
  if (Array.isArray(n)) return n.map(nodeText).join('')
  if (typeof n === 'object' && 'props' in n) return nodeText((n as { props: { children?: ReactNode } }).props.children)
  return ''
}

/** Figma column-menu (130:1340): 낮은/높은 순 정렬 + 값 선택 + 초기화/적용. 표 스크롤에 잘리지 않게 화면 고정 위치 */
export function ColumnMenu({ at, values, selected, onSort, onApply, onClose }: { at: { x: number; y: number }; values: string[]; selected: string[] | null; onSort: (dir: 1 | -1) => void; onApply: (v: string[] | null) => void; onClose: () => void }) {
  const [pick, setPick] = useState<string[]>(selected ?? values)
  const ref = useRef<HTMLDivElement>(null)
  useEffect(() => {
    const down = (e: MouseEvent) => ref.current && !ref.current.contains(e.target as Node) && onClose()
    const scroll = (e: Event) => ref.current && !ref.current.contains(e.target as Node) && onClose()
    document.addEventListener('mousedown', down)
    window.addEventListener('scroll', scroll, true)
    return () => {
      document.removeEventListener('mousedown', down)
      window.removeEventListener('scroll', scroll, true)
    }
  }, [onClose])
  const left = Math.min(at.x, window.innerWidth - 216)
  return (
    <div className="adm-colmenu" ref={ref} style={{ position: 'fixed', top: at.y, left }}>
      <button className="opt" onClick={() => onSort(1)}>
        낮은 순 정렬
      </button>
      <button className="opt" onClick={() => onSort(-1)}>
        높은 순 정렬
      </button>
      <i className="sep" />
      <p className="cap">값 선택</p>
      <div className="vals">
        {values.map((v) => {
          const on = pick.includes(v)
          return (
            <button key={v} className="chk" onClick={() => setPick(on ? pick.filter((x) => x !== v) : [...pick, v])}>
              <span className={cx('box', on && 'on')}>{on && <Icon name="check" size={12} />}</span>
              <span className="v">{v || '(빈 값)'}</span>
            </button>
          )
        })}
      </div>
      <div className="foot">
        <button className="ghost" onClick={() => onApply(null)}>
          초기화
        </button>
        <button className="primary" onClick={() => onApply(pick.length === values.length ? null : pick)}>
          적용
        </button>
      </div>
    </div>
  )
}

/** 엑셀 필터처럼 머리글 메뉴에서 정렬하고 값을 골라 거르는 표 */
export function DataTable<T>({ columns, rows, rowKey, onRow, empty = '데이터가 없어요', group, light, className }: { columns: Column<T>[]; rows: T[]; rowKey: (r: T) => string | number; onRow?: (r: T) => void; empty?: string; group?: ReactNode; light?: boolean; className?: string }) {
  const [sort, setSort] = useState<{ key: string; dir: 1 | -1 } | null>(null)
  const [filters, setFilters] = useState<Record<string, string[]>>({})
  const [menu, setMenu] = useState<{ key: string; x: number; y: number } | null>(null)
  const labelOf = (c: Column<T>, r: T) => (c.filter ? c.filter(r) : nodeText(c.render(r)).trim() || (c.sort ? String(c.sort(r)) : ''))
  const filtered = rows.filter((r) => Object.entries(filters).every(([k, vs]) => {
    const c = columns.find((x) => x.key === k)
    return !c || vs.includes(labelOf(c, r))
  }))
  const col = columns.find((c) => c.key === sort?.key)
  const sorted = col?.sort ? [...filtered].sort((a, b) => (col.sort!(a) > col.sort!(b) ? 1 : col.sort!(a) < col.sort!(b) ? -1 : 0) * sort!.dir) : filtered
  const menuCol = columns.find((c) => c.key === menu?.key)
  // 값 목록은 다른 열 필터와 상관없이 이 열의 전체 값 (정렬 값 순)
  const menuValues = menuCol
    ? [...new Map(rows.map((r) => [labelOf(menuCol, r), menuCol.sort ? menuCol.sort(r) : labelOf(menuCol, r)])).entries()].sort((a, b) => (a[1] > b[1] ? 1 : a[1] < b[1] ? -1 : 0)).map(([l]) => l)
    : []
  return (
    <div className={cx('adm-table-wrap', light && 'light', className)}>
      <table className="adm-table">
        <thead>
          <tr>
            {columns.map((c) => (
              <th key={c.key} style={{ width: c.width }}>
                {c.sort ? (
                  <button
                    className={cx('adm-colbtn', (filters[c.key] || sort?.key === c.key) && 'on')}
                    onClick={(e) => {
                      const b = e.currentTarget.getBoundingClientRect()
                      setMenu(menu?.key === c.key ? null : { key: c.key, x: b.left - 6, y: b.bottom + 8 })
                    }}
                  >
                    {c.label}
                    <Icon name="chevron-down" size={12} style={{ transform: sort?.key === c.key && sort.dir === -1 ? 'rotate(180deg)' : undefined }} />
                  </button>
                ) : (
                  c.label
                )}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {group && (
            <tr className="adm-group">
              <td colSpan={columns.length}>{group}</td>
            </tr>
          )}
          {sorted.length === 0 && (
            <tr>
              <td colSpan={columns.length} className="adm-empty">
                {rows.length > 0 ? '조건에 맞는 항목이 없어요' : empty}
              </td>
            </tr>
          )}
          {sorted.map((r) => (
            <tr key={rowKey(r)} className={onRow ? 'clickable' : undefined} onClick={onRow ? () => onRow(r) : undefined}>
              {columns.map((c) => (
                <td key={c.key}>{c.render(r)}</td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
      {menu && menuCol && (
        <ColumnMenu
          key={menu.key}
          at={menu}
          values={menuValues}
          selected={filters[menu.key] ?? null}
          onSort={(dir) => {
            setSort({ key: menu.key, dir })
            setMenu(null)
          }}
          onApply={(v) => {
            const next = { ...filters }
            if (v) next[menu.key] = v
            else delete next[menu.key]
            setFilters(next)
            if (!v && sort?.key === menu.key) setSort(null)
            setMenu(null)
          }}
          onClose={() => setMenu(null)}
        />
      )}
    </div>
  )
}

/** 일별/주간/월별 같은 세그먼트 */
export function Seg<T extends string>({ value, options, onChange, dark = true, className }: { value: T; options: [T, string][]; onChange: (v: T) => void; dark?: boolean; className?: string }) {
  return (
    <div className={cx('adm-seg', !dark && 'light', className)} role="tablist">
      {options.map(([v, l]) => (
        <button key={v} role="tab" aria-selected={value === v} onClick={() => onChange(v)}>
          {l}
        </button>
      ))}
    </div>
  )
}

/** 회차 선택 (Figma session-picker: ‹ | 10.22 (목) 회차 ⌄ | ›) */
export function SessionPicker({ value, options, onChange, onPrev, onNext }: { value: string; options: [string, string][]; onChange: (v: string) => void; onPrev?: () => void; onNext?: () => void }) {
  return (
    <div className="adm-session">
      <button aria-label="이전 회차" disabled={!onPrev} onClick={onPrev}>
        <Icon name="chevron-left" size={18} />
      </button>
      <label className="mid">
        <span className="label">{options.find(([v]) => v === value)?.[1] ?? '회차 없음'}</span>
        <Icon name="chevron-down" size={16} />
        <select value={value} onChange={(e) => onChange(e.target.value)} aria-label="회차 선택">
          {options.map(([v, l]) => (
            <option key={v} value={v}>
              {l}
            </option>
          ))}
        </select>
      </label>
      <button aria-label="다음 회차" disabled={!onNext} onClick={onNext}>
        <Icon name="chevron-right" size={18} />
      </button>
    </div>
  )
}

/** 세로 막대 차트 (Figma chart: 높이 148, 막대 영역 120, 격자 4줄) */
export function BarChart({ items, area = 120 }: { items: { label: string; value: number; text?: string; hot?: boolean }[]; area?: number }) {
  const max = Math.max(...items.map((i) => i.value), 1)
  return (
    <div className="adm-score-chart" style={{ height: area + 28 }}>
      {[0, 1, 2, 3].map((k) => (
        <i key={k} className="grid" style={{ top: Math.round((area * k) / 3) }} />
      ))}
      <div className="cols">
        {items.map((it, i) => (
          <div key={i} className="col" style={{ height: area }}>
            <span className="cnt">{it.text ?? it.value}</span>
            <span className={it.hot ? 'bar low' : 'bar'} style={{ height: (it.value / max) * area }} />
            <span className="lbl" style={{ top: area + 8 }}>
              {it.label}
            </span>
          </div>
        ))}
      </div>
    </div>
  )
}

export function Field({ label, children, style }: { label: string; children: ReactNode; style?: React.CSSProperties }) {
  return (
    <label className="adm-field" style={style}>
      <span>{label}</span>
      {children}
    </label>
  )
}

export function Toggle({ on, onChange, label }: { on: boolean; onChange: (v: boolean) => void; label?: string }) {
  return <button type="button" role="switch" aria-checked={on} aria-label={label} className={cx('adm-toggle', on && 'on')} onClick={() => onChange(!on)} />
}

export function DemoNotice({ show }: { show: boolean }) {
  if (!show) return null
  return <p className="adm-demo">시안 데이터로 보여주는 중이에요. 운영자로 로그인하면 실제 데이터가 보여요.</p>
}

export { cx }
