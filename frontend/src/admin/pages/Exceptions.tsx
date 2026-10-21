import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { AdminLayout, DataTable, DemoNotice, useAdminApi, useServiceNow, cx } from '../AdminLayout'
import { Badge, useToast } from '../../components/ui'
import { adminSession, get, post } from '../../lib/api'
import type { ApplicantRow, ExceptionCase, TableRow } from '../types'
import { adminDemo, slotDayLabel, slotRegion, tableLabel } from '../demo'

/** 필터 칩 → 백엔드 예외 유형 */
const FILTERS: [string, string, string[]][] = [
  ['all', '전체', []],
  ['request', '참가자 요청', ['LATE_CANCEL']],
  ['conflict', '조건 충돌', ['UNRESOLVED_CONFLICT']],
  ['venue', '식당 부족', ['VENUE_SHORTAGE']],
  ['notify', '발송 실패', ['NOTIFICATION_FAILED']],
  ['data', '데이터 누락', ['DATA_MISSING']],
  ['noshow', '노쇼 후보', ['NO_SHOW_REVIEW']],
  ['report', '신고', ['SAFETY_REPORT']],
  ['payment', '결제 오류', ['PAYMENT_ERROR', 'REFUND_FAILED']],
]
const TYPE: Record<string, [string, boolean]> = {
  LATE_CANCEL: ['참가자 요청', false],
  UNRESOLVED_CONFLICT: ['조건 충돌', true],
  VENUE_SHORTAGE: ['식당 부족', true],
  NOTIFICATION_FAILED: ['발송 실패', false],
  DATA_MISSING: ['데이터 누락', false],
  NO_SHOW_REVIEW: ['노쇼 후보', false],
  SAFETY_REPORT: ['신고', true],
  PAYMENT_ERROR: ['결제 오류', true],
  REFUND_FAILED: ['환불 실패', true],
}
const typeBadge = (t: string, label?: string) => {
  const [l, warn] = TYPE[t] ?? [t, false]
  return (
    <Badge tone={warn ? '경고' : '확정'} dot={false}>
      {label ?? l}
    </Badge>
  )
}

type Row = { id: number; type: string; label?: string; target: string; detail: string; reason: string; tries: string; elapsed: string; createdAt: string; applicationId: number | null; tableId: number | null }
type Log = { text: string; at: string; done?: boolean }

// Figma 13:837 시안 데이터
const DEMO_ROWS: Row[] = [
  ['LATE_CANCEL', '식당 변경', '김서윤', '10.22, T-1022-EJ-3', '갑각류 알레르기로 식당 변경 요청', '—', '20분'],
  ['UNRESOLVED_CONFLICT', undefined, '한승우', '10.22, 강남/신논현', '연령 9살 차, 대체 회차 없음', '재배치 2', '8시간'],
  ['UNRESOLVED_CONFLICT', undefined, '서OO', '10.22, 홍대/합정', '차단 관계로 유일 후보 제외', '재배치 2', '8시간'],
  ['VENUE_SHORTAGE', undefined, 'T-1105-GN-1', '11.05, 강남/신논현', '6인석 파트너 식당 없음', '탐색 3', '1시간'],
  ['NOTIFICATION_FAILED', undefined, '노OO', '푸시, 10.15 회차', '푸시 토큰 만료', '재시도 3', '2시간'],
  ['DATA_MISSING', undefined, '강OO', '11.05', '출생연도 미입력', '—', '30분'],
  ['NO_SHOW_REVIEW', undefined, '3명', '10.15, 을지로/종로', '종료 시각까지 체크인 없음', '—', '3일'],
  ['SAFETY_REPORT', undefined, 'SR-031', '10.15, T-1015-HD-2', '불쾌한 언행 신고', '즉시 분리', '18시간'],
].map(([type, label, target, detail, reason, tries, elapsed], i) => ({
  id: [401, 412, 413, 420, 431, 435, 440, 450][i],
  type: type as string,
  label: label as string | undefined,
  target: target as string,
  detail: detail as string,
  reason: reason as string,
  tries: tries as string,
  elapsed: elapsed as string,
  createdAt: '',
  applicationId: 200 + i,
  tableId: 7,
}))
const DEMO_COUNTS: Record<string, number> = { all: 10, request: 1, conflict: 2, venue: 1, notify: 1, data: 1, noshow: 3, report: 1, payment: 0 }
const DEMO_LOG: Log[] = [
  { text: '자동 매칭, 최초 조합 실패 (3명)', at: '24:00:02' },
  { text: '재배치 1, 강남/신논현 GN-2 → 나이 차 9살', at: '24:01:10' },
  { text: '재배치 2, 11.05 강남/신논현 → 신청 범위 밖, 시도 안 함', at: '24:01:40' },
  { text: '대체 회차 탐색, 11.19 강남/신논현 연령 불충족', at: '24:02:15' },
  { text: 'EXCEPTION 전환, 운영자 확인 요청', at: '24:02:16', done: true },
]

const elapsed = (iso: string, now: Date) => {
  const m = Math.max(0, Math.round((now.getTime() - new Date(iso).getTime()) / 60000))
  if (m < 60) return `${m}분`
  if (m < 60 * 24) return `${Math.round(m / 60)}시간`
  return `${Math.round(m / 1440)}일`
}
const hms = (iso: string) => {
  const d = new Date(iso)
  const p = (n: number) => String(n).padStart(2, '0')
  return d.getHours() === 0 ? `24:${p(d.getMinutes())}:${p(d.getSeconds())}` : `${p(d.getMonth() + 1)}.${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}
const MEMO_KEY = 'wt.admin.exceptionMemo'
const loadMemo = (): Record<string, string> => {
  try {
    return JSON.parse(localStorage.getItem(MEMO_KEY) ?? '{}')
  } catch {
    return {}
  }
}

/** A07 예외 처리함 */
export default function Exceptions() {
  const navigate = useNavigate()
  const toast = useToast()
  const list = useAdminApi(() => get<ExceptionCase[]>('/api/admin/exceptions'), adminDemo.exceptions)
  const apps = useAdminApi(() => get<ApplicantRow[]>('/api/admin/applications'), adminDemo.applicants)
  const tables = useAdminApi(() => get<TableRow[]>('/api/admin/tables'), adminDemo.tables)
  const demo = list.demo
  const now = useServiceNow()
  const [filter, setFilter] = useState('all')
  const [selId, setSelId] = useState<number | null>(null)
  const [log, setLog] = useState<Log[]>([])
  const [memoOpen, setMemoOpen] = useState(false)
  const [memos, setMemos] = useState(loadMemo)
  const [memo, setMemo] = useState('')

  const rows: Row[] = useMemo(() => {
    if (demo) return DEMO_ROWS
    const appOf = new Map(apps.data.map((a) => [a.applicationId, a]))
    const userOf = new Map(apps.data.map((a) => [a.userId, a]))
    const tableOf = new Map(tables.data.map((t) => [t.tableId, t]))
    return list.data.map((e) => {
      const a = e.applicationId ? appOf.get(e.applicationId) : e.userId ? userOf.get(e.userId) : undefined
      const t = e.tableId ? tableOf.get(e.tableId) : undefined
      const label = a?.slotLabels[0] ?? t?.slotLabel
      const date = label ? slotDayLabel(label).split(' ')[0] : ''
      return {
        id: e.id,
        type: e.type,
        target: a?.name ?? (t ? tableLabel(t.tableCode, t.slotLabel) : e.userId ? `회원 ${e.userId}` : '—'),
        // 대상이 테이블이면 상세에는 지역만, 사람이면 테이블 코드(없으면 지역)
        detail: [date, a && t ? tableLabel(t.tableCode, t.slotLabel) : label ? slotRegion(label) : ''].filter(Boolean).join(', ') || '—',
        reason: e.reason,
        tries: a?.reallocationCount ? `재배치 ${a.reallocationCount}` : '—',
        elapsed: elapsed(e.createdAt, now),
        createdAt: e.createdAt,
        applicationId: e.applicationId,
        tableId: e.tableId,
      }
    })
  }, [demo, list.data, apps.data, tables.data, now])

  const counts = useMemo(() => {
    if (demo) return DEMO_COUNTS
    return Object.fromEntries(FILTERS.map(([k, , types]) => [k, k === 'all' ? rows.length : rows.filter((r) => types.includes(r.type)).length]))
  }, [demo, rows])
  const shown = filter === 'all' ? rows : rows.filter((r) => FILTERS.find(([k]) => k === filter)![2].includes(r.type))
  const sel = rows.find((r) => r.id === selId) ?? rows.find((r) => r.type === 'UNRESOLVED_CONFLICT') ?? rows[0]

  // 자동 처리 이력 = 신청 상태 변경 기록
  useEffect(() => {
    setMemoOpen(false)
    if (!sel) return setLog([])
    if (demo) return setLog(sel.type === 'UNRESOLVED_CONFLICT' ? DEMO_LOG : [{ text: sel.reason, at: sel.elapsed + ' 전', done: true }])
    if (!sel.applicationId || !adminSession.token) return setLog([{ text: `${sel.reason}, 운영자 확인 요청`, at: hms(sel.createdAt), done: true }])
    get<{ field: string; fromValue: string | null; toValue: string; reason: string | null; actor: string; changedAt: string }[]>(`/api/admin/applications/${sel.applicationId}/history`)
      .then((h) => setLog([...h.map((x) => ({ text: `${x.field} ${x.fromValue ?? '—'} → ${x.toValue}${x.reason ? `, ${x.reason}` : ''}`, at: hms(x.changedAt) })), { text: 'EXCEPTION 전환, 운영자 확인 요청', at: hms(sel.createdAt), done: true }]))
      .catch(() => setLog([{ text: sel.reason, at: hms(sel.createdAt), done: true }]))
  }, [sel?.id, demo]) // eslint-disable-line react-hooks/exhaustive-deps

  const close = async (status: 'RESOLVED' | 'DISMISSED', note: string) => {
    if (!sel) return
    try {
      if (!demo) await post(`/api/admin/exceptions/${sel.id}/close`, { status, note })
      list.setData(demo ? list.data : list.data.filter((e) => e.id !== sel.id))
      if (demo) DEMO_ROWS.splice(DEMO_ROWS.indexOf(sel), 1)
      setSelId(null)
      toast.show(`EX-${String(sel.id).padStart(4, '0')} ${note}`)
    } catch (e) {
      toast.show(e instanceof Error ? e.message : '처리하지 못했어요')
    }
  }
  const saveMemo = () => {
    if (!sel || !memo.trim()) return
    const next = { ...memos, [sel.id]: memo.trim() }
    setMemos(next)
    try {
      localStorage.setItem(MEMO_KEY, JSON.stringify(next))
    } catch {
      /* noop */
    }
    setMemo('')
    setMemoOpen(false)
    toast.show('메모를 남기고 보류했어요')
  }

  /** 유형별 운영자 조치 */
  const actions: [string, () => void][] = !sel
    ? []
    : sel.type === 'UNRESOLVED_CONFLICT'
      ? [
          ['대체 일정, 보관, 환불 선택지 보내기', () => close('RESOLVED', '선택지를 보냈어요')],
          ['다른 회차 대기 명단에 등록', () => close('RESOLVED', '대기 명단에 등록했어요')],
        ]
      : sel.type === 'VENUE_SHORTAGE'
        ? [['테이블에 식당 직접 배정', () => navigate(`/admin/tables/${sel.tableId ?? ''}`)], ['처리 완료', () => close('RESOLVED', '처리 완료')]]
        : sel.type === 'NOTIFICATION_FAILED'
          ? [['자동 메시지에서 다시 보내기', () => navigate('/admin/messages')], ['처리 완료', () => close('RESOLVED', '처리 완료')]]
          : sel.type === 'NO_SHOW_REVIEW'
            ? [['참석/노쇼에서 확인', () => navigate('/admin/attendance')], ['처리 완료', () => close('RESOLVED', '처리 완료')]]
            : sel.type === 'SAFETY_REPORT'
              ? [['신고 내용 보고 조치', () => navigate('/admin/feedback')], ['처리 완료', () => close('RESOLVED', '처리 완료')]]
              : sel.type === 'PAYMENT_ERROR' || sel.type === 'REFUND_FAILED'
                ? [['결제 정보에서 확인', () => navigate('/admin/payments')], ['처리 완료', () => close('RESOLVED', '처리 완료')]]
                : [['요청 반영하고 완료', () => close('RESOLVED', '요청을 반영했어요')], ['요청 반려', () => close('DISMISSED', '요청을 반려했어요')]]

  return (
    <AdminLayout title="예외 처리함">
      <DemoNotice show={demo} />
      <div className="adm-ex-chips" role="tablist">
        {FILTERS.map(([k, l]) => (
          <button key={k} role="tab" aria-selected={filter === k} className={cx(filter === k && 'on')} onClick={() => setFilter(k)}>
            <span>{l}</span>
            <span className="n">{counts[k] ?? 0}</span>
          </button>
        ))}
      </div>

      <div className="adm-td-cols">
        <div style={{ flex: '1 0 0', minWidth: 0 }}>
          <DataTable
            className="adm-ex-table"
            rows={shown}
            rowKey={(r) => r.id}
            onRow={(r) => setSelId(r.id)}
            empty="처리할 예외가 없어요"
            columns={[
              { key: 'type', label: '유형', width: 84, render: (r) => <span className={cx(r.id === sel?.id && 'adm-ex-sel')}>{typeBadge(r.type, r.label)}</span>, sort: (r) => r.type },
              { key: 'target', label: '대상', width: 64, render: (r) => <span className="strong ellipsis" style={{ maxWidth: 64, display: 'block' }}>{r.target}</span>, sort: (r) => r.target },
              { key: 'detail', label: '상세', width: 118, render: (r) => <span className="ellipsis" style={{ maxWidth: 118, display: 'block' }}>{r.detail}</span>, sort: (r) => r.detail },
              { key: 'reason', label: '사유', render: (r) => <span className="ellipsis" style={{ maxWidth: 260, display: 'block' }}>{memos[r.id] ? `📝 ${r.reason}` : r.reason}</span>, sort: (r) => r.reason },
              { key: 'tries', label: '자동 시도', width: 60, render: (r) => <span className="nowrap">{r.tries}</span>, sort: (r) => r.tries },
              { key: 'el', label: '경과', width: 50, render: (r) => <span className="nowrap">{r.elapsed}</span>, sort: (r) => r.createdAt || r.elapsed },
            ]}
          />
        </div>

        {sel && (
          <section className="adm-card16 adm-ex-detail">
            <div className="row" style={{ gap: 8 }}>
              {typeBadge(sel.type)}
              <span className="t-label-s" style={{ flex: 1 }}>
                EX-{String(sel.id).padStart(4, '0')}
              </span>
              <span className="t-caption" style={{ color: 'var(--terracotta)' }}>
                예외
              </span>
            </div>
            <h2 className="t-h2">
              {sel.target}, {sel.detail}
            </h2>
            <div className="stack" style={{ gap: 10 }}>
              <p className="t-label-m">자동 처리 이력</p>
              {log.map((l, i) => (
                <div key={i} className="adm-ex-log">
                  <i className={l.done ? 'done' : undefined} />
                  <div>
                    <span>{l.text}</span>
                    <small>{l.at}</small>
                  </div>
                </div>
              ))}
              {memos[sel.id] && (
                <div className="adm-ex-log">
                  <i className="done" />
                  <div>
                    <span>운영자 메모: {memos[sel.id]}</span>
                    <small>보류 중</small>
                  </div>
                </div>
              )}
            </div>
            <div className="stack" style={{ gap: 8 }}>
              <p className="t-label-m">운영자 조치</p>
              {actions.map(([l, fn], i) => (
                <button key={l} className={cx('adm-btn-l', i === 0 ? 'primary' : 'card')} onClick={fn}>
                  {l}
                </button>
              ))}
              {memoOpen ? (
                <div className="stack" style={{ gap: 8 }}>
                  <textarea className="adm-ex-memo" value={memo} onChange={(e) => setMemo(e.target.value)} placeholder="보류 사유를 남겨주세요" autoFocus />
                  <button className="adm-btn-l" disabled={!memo.trim()} onClick={saveMemo}>
                    메모 저장하고 보류
                  </button>
                </div>
              ) : (
                <button className="adm-btn-l" onClick={() => setMemoOpen(true)}>
                  메모 남기고 보류
                </button>
              )}
            </div>
            <p className="adm-ex-warn">필수 조건은 수동으로도 완화 불가</p>
          </section>
        )}
      </div>
      {toast.node}
    </AdminLayout>
  )
}
