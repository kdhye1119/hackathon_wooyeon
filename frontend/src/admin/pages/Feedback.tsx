import { useEffect, useMemo, useRef, useState } from 'react'
import { AdminLayout, BarChart, DataTable, DemoNotice, Seg, useAdminApi, cx, useServiceNow } from '../AdminLayout'
import { Badge, useToast } from '../../components/ui'
import { Icon } from '../../components/Icon'
import { get, post } from '../../lib/api'
import type { FeedbackSummary, ReportDto, TableRow } from '../types'
import { adminDemo, tableLabel } from '../demo'

type Period = 'day' | 'week' | 'month'
const DAY = 86400000
const p2 = (n: number) => String(n).padStart(2, '0')
const md = (d: Date) => `${p2(d.getMonth() + 1)}.${p2(d.getDate())}`
function range(period: Period, a: Date) {
  const d = new Date(a.getFullYear(), a.getMonth(), a.getDate())
  if (period === 'day') return { start: d, end: new Date(d.getTime() + DAY), label: `${md(d)} (${'일월화수목금토'[d.getDay()]})` }
  if (period === 'week') {
    // 기준일까지 최근 7일
    const start = new Date(d.getTime() - 6 * DAY)
    const end = new Date(d.getTime() + DAY)
    return { start, end, label: `${d.getMonth() + 1}월 ${Math.ceil(d.getDate() / 7)}주, ${md(start)} ~ ${md(d)}` }
  }
  return { start: new Date(d.getFullYear(), d.getMonth(), 1), end: new Date(d.getFullYear(), d.getMonth() + 1, 1), label: `${d.getFullYear()}년 ${d.getMonth() + 1}월` }
}
const shift = (p: Period, d: Date, n: number) => (p === 'day' ? new Date(d.getTime() + n * DAY) : p === 'week' ? new Date(d.getTime() + 7 * n * DAY) : new Date(d.getFullYear(), d.getMonth() + n, 1))

type FRow = { id: number; name: string; table: string; mood: number; talk: number; venue: number; rejoin: number; rec: number; open: boolean; comment: string }
type RRow = { id: number; code: string; reporter: string; reported: string; reason: string; split: boolean; status: string; action: string | null; handledAt: string | null }
type XRow = { key: string; a: string; b: string; type: '신고' | '차단' | '비선호'; at: string }

// Figma 15:993 시안 데이터
const DEMO = {
  kpi: { mood: 4.7, talk: 4.5, venue: 4.3, rejoin: 82, pairs: 37 },
  dist: { mood: [2, 3, 8, 24, 27], talk: [1, 5, 10, 22, 26], venue: [3, 4, 12, 23, 22] },
  reports: [
    { id: 31, code: 'SR-031', reporter: '이OO', reported: '박OO', reason: '불쾌한 언행', split: true, status: 'OPEN', action: null, handledAt: null },
    { id: 30, code: 'SR-030', reporter: '정OO', reported: '최OO', reason: '연락처 요구', split: true, status: 'HANDLED', action: 'WARNING', handledAt: '2026-10-21T10:00:00' },
  ] as RRow[],
  exclusions: [
    { key: '1', a: '이OO', b: '박OO', type: '신고', at: '10.15' },
    { key: '2', a: '정OO', b: '최OO', type: '차단', at: '10.08' },
    { key: '3', a: '김서윤', b: '이OO', type: '비선호', at: '10.08' },
  ] as XRow[],
  feedback: [
    [1, '김서윤', 'T-1015-EJ-1', 5, 4, 4, 5, 10, true, '대화 주제가 정말 좋았어요. 다음에도 을지로면 좋겠어요'],
    [2, '박도현', 'T-1015-EJ-1', 4, 4, 5, 5, 8, false, ''],
    [3, '이OO', 'T-1015-GN-1', 3, 3, 4, 3, 6, false, '나이대가 조금 더 비슷했으면 해요'],
    [4, '정OO', 'T-1015-HD-3', 4, 5, 3, 4, 7, false, '식당이 시끄러워서 대화가 힘들었어요'],
    [5, '최OO', 'T-1015-HD-3', 5, 5, 4, 1, 5, false, ''],
  ].map(([id, name, table, mood, talk, venue, rejoin, rec, open, comment]) => ({ id, name, table, mood, talk, venue, rejoin, rec, open, comment }) as FRow),
  hidden: [3],
}

const VIS_KEY = 'wt.admin.feedbackHidden'
const loadHidden = (d: number[]): number[] => {
  try {
    return JSON.parse(localStorage.getItem(VIS_KEY) ?? 'null') ?? d
  } catch {
    return d
  }
}
const ACTION: Record<string, string> = { WARNING: '경고', RESTRICTED: '참가 제한', BANNED: '영구 제한', NONE: '기각' }

/** Figma column-menu (130:1340): 정렬 + 값 선택 */
function ColumnMenu({ values, selected, onApply, onSort, onClose }: { values: number[]; selected: number[] | null; onApply: (v: number[] | null) => void; onSort: (dir: 1 | -1) => void; onClose: () => void }) {
  const [pick, setPick] = useState<number[]>(selected ?? values)
  const ref = useRef<HTMLDivElement>(null)
  useEffect(() => {
    const h = (e: MouseEvent) => ref.current && !ref.current.contains(e.target as Node) && onClose()
    document.addEventListener('mousedown', h)
    return () => document.removeEventListener('mousedown', h)
  }, [onClose])
  return (
    <div className="adm-colmenu" ref={ref}>
      <button className="opt" onClick={() => onSort(1)}>
        낮은 순 정렬
      </button>
      <button className="opt" onClick={() => onSort(-1)}>
        높은 순 정렬
      </button>
      <i className="sep" />
      <p className="cap">값 선택</p>
      {values.map((v) => {
        const on = pick.includes(v)
        return (
          <button key={v} className="chk" onClick={() => setPick(on ? pick.filter((x) => x !== v) : [...pick, v])}>
            <span className={cx('box', on && 'on')}>{on && <Icon name="check" size={12} />}</span>
            {v}점
          </button>
        )
      })}
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

/** A10 피드백/신고 */
export default function Feedback() {
  const toast = useToast()
  const fb = useAdminApi(() => get<FeedbackSummary>('/api/admin/feedback'), adminDemo.feedback)
  const reports = useAdminApi(() => get<ReportDto[]>('/api/admin/reports'), adminDemo.reports)
  const tables = useAdminApi(() => get<TableRow[]>('/api/admin/tables'), adminDemo.tables)
  const demo = fb.demo
  const [period, setPeriod] = useState<Period>('week')
  // 기준일 기본값 = 서비스 시각 (시연 시각 이동 반영)
  const now = useServiceNow()
  const [picked, setAnchor] = useState<Date | null>(null)
  const anchor = picked ?? (demo ? new Date(2026, 9, 20) : now)
  const r = range(period, anchor)
  const [hiddenSaved, setHidden] = useState(() => loadHidden([]))
  // 시안 데이터에서만 Figma 처럼 한 건을 숨김으로 보여준다
  // 실데이터는 서버의 숨김 상태, 시안은 이 기기 기록 + Figma 예시
  const hidden = demo ? [...new Set([...hiddenSaved, ...DEMO.hidden])] : fb.data.recent.filter((x) => x.hidden).map((x) => x.id)
  const [menu, setMenu] = useState<keyof FRow | null>(null)
  const [filters, setFilters] = useState<Partial<Record<keyof FRow, number[]>>>({})
  const [sort, setSort] = useState<{ key: keyof FRow; dir: 1 | -1 } | null>(null)

  const tableCode = useMemo(() => new Map(tables.data.map((t) => [t.tableId, tableLabel(t.tableCode, t.slotLabel)])), [tables.data])
  const rows: FRow[] = demo
    ? DEMO.feedback
    : fb.data.recent.map((x) => ({ id: x.id, name: x.userName, table: tableCode.get(x.tableId) ?? `#${x.tableId}`, mood: x.tableRating, talk: x.conversationRating, venue: x.venueRating, rejoin: x.rejoinIntent, rec: x.recommendScore, open: !!x.publicReview, comment: x.comment ?? '' }))
  const dist = (k: 'mood' | 'talk' | 'venue') => (demo ? DEMO.dist[k] : [1, 2, 3, 4, 5].map((v) => rows.filter((x) => x[k] === v).length))
  const kpi = demo
    ? DEMO.kpi
    : {
        mood: fb.data.avgTable,
        talk: fb.data.avgConversation,
        venue: fb.data.avgVenue,
        rejoin: rows.length ? Math.round((rows.filter((x) => x.rejoin >= 4).length / rows.length) * 100) : 0,
        pairs: fb.data.avoidCount + reports.data.length,
      }

  const rrows: RRow[] = demo
    ? DEMO.reports
    : reports.data.map((x) => ({ id: x.id, code: `SR-${String(x.id).padStart(3, '0')}`, reporter: x.reporterName, reported: x.reportedName, reason: x.category, split: true, status: x.status, action: x.action, handledAt: x.handledAt }))
  const xrows: XRow[] = demo ? DEMO.exclusions : reports.data.map((x) => ({ key: String(x.id), a: x.reporterName, b: x.reportedName, type: '신고', at: md(new Date(x.createdAt)) }))

  const act = async (row: RRow, action: 'WARNING' | 'RESTRICTED' | 'BANNED') => {
    try {
      if (!demo) {
        await post(`/api/admin/reports/${row.id}/action`, { action, note: ACTION[action], restrictDays: action === 'RESTRICTED' ? 30 : undefined })
        reports.reload()
      } else {
        const i = DEMO.reports.indexOf(row)
        DEMO.reports[i] = { ...row, status: 'HANDLED', action, handledAt: new Date().toISOString() }
        reports.setData([...reports.data])
      }
      toast.show(`${row.reported}: ${ACTION[action]} 조치했어요`)
    } catch (e) {
      toast.show(e instanceof Error ? e.message : '조치하지 못했어요')
    }
  }
  const toggleHidden = async (id: number) => {
    if (!demo) {
      try {
        await post(`/api/admin/feedback/${id}/visibility`, { hidden: !hidden.includes(id) })
        toast.show(hidden.includes(id) ? '후기를 다시 공개했어요' : '후기를 숨겼어요')
        fb.reload()
      } catch (e) {
        toast.show(e instanceof Error ? e.message : '바꾸지 못했어요')
      }
      return
    }
    const next = hidden.includes(id) ? hiddenSaved.filter((x) => x !== id) : [...hiddenSaved, id]
    setHidden(next)
    try {
      localStorage.setItem(VIS_KEY, JSON.stringify(next))
    } catch {
      /* noop */
    }
  }

  let frows = rows.filter((x) => (Object.entries(filters) as [keyof FRow, number[]][]).every(([k, vs]) => vs.includes(x[k] as number)))
  if (sort) frows = [...frows].sort((a, b) => ((a[sort.key] as number) - (b[sort.key] as number)) * sort.dir)
  const scoreHead = (k: keyof FRow, label: string) => (
    <span className="adm-colhead">
      <button className={cx('adm-colbtn', (filters[k] || sort?.key === k) && 'on')} onClick={() => setMenu(menu === k ? null : k)}>
        {label}
        <Icon name="chevron-down" size={12} />
      </button>
      {menu === k && (
        <ColumnMenu
          values={[5, 4, 3, 2, 1]}
          selected={filters[k] ?? null}
          onSort={(dir) => {
            setSort({ key: k, dir })
            setMenu(null)
          }}
          onApply={(v) => {
            const next = { ...filters }
            if (v) next[k] = v
            else delete next[k]
            setFilters(next)
            if (!v && sort?.key === k) setSort(null)
            setMenu(null)
          }}
          onClose={() => setMenu(null)}
        />
      )}
    </span>
  )

  return (
    <AdminLayout title="피드백/신고">
      <DemoNotice show={demo} />
      <div className="row" style={{ gap: 12 }}>
        <Seg
          value={period}
          onChange={setPeriod}
          options={[
            ['day', '일별'],
            ['week', '주간'],
            ['month', '월별'],
          ]}
        />
        <div className="adm-stepper">
          <button aria-label="이전" onClick={() => setAnchor(shift(period, anchor, -1))}>
            <Icon name="chevron-left" size={16} />
          </button>
          <span className="label">{r.label}</span>
          <button aria-label="다음" onClick={() => setAnchor(shift(period, anchor, 1))}>
            <Icon name="chevron-right" size={16} />
          </button>
        </div>
      </div>

      <div className="adm-att-stats">
        {[
          ['분위기', kpi.mood.toFixed(1)],
          ['대화', kpi.talk.toFixed(1)],
          ['식당', kpi.venue.toFixed(1)],
          ['재참여 의향', `${kpi.rejoin}%`],
          ['매칭 제외 관계', `${kpi.pairs}쌍`],
        ].map(([l, v]) => (
          <div key={l}>
            <span>{l}</span>
            <b>{v}</b>
          </div>
        ))}
      </div>

      <div className="adm-mt-stats">
        {(
          [
            ['mood', '분위기 점수 분포'],
            ['talk', '대화 점수 분포'],
            ['venue', '식당 점수 분포'],
          ] as const
        ).map(([k, title]) => (
          <section key={k} className="adm-panel">
            <p className="t-label-m">{title}</p>
            <BarChart area={70} items={dist(k).map((n, i) => ({ label: `${i + 1}점`, value: n, hot: i < 2 }))} />
          </section>
        ))}
      </div>

      <div className="adm-mt-section">
        <h2 className="t-h2">신고</h2>
        <DataTable
          className="adm-att-table"
          rows={rrows}
          rowKey={(x) => x.id}
          empty="신고가 없어요"
          columns={[
            { key: 'id', label: 'ID', width: 80, render: (x) => <span className={cx(x.status === 'OPEN' && 'adm-att-hl')}>{x.code}</span>, sort: (x) => x.id },
            { key: 'from', label: '신고자', width: 74, render: (x) => x.reporter, sort: (x) => x.reporter },
            { key: 'to', label: '피신고자', width: 74, render: (x) => x.reported, sort: (x) => x.reported },
            { key: 'reason', label: '사유', width: 200, render: (x) => x.reason, sort: (x) => x.reason },
            {
              key: 'split',
              label: '매칭 분리',
              width: 100,
              render: () => (
                <Badge tone="확정" dot={false}>
                  분리됨
                </Badge>
              ),
            },
            {
              key: 'st',
              label: '상태',
              width: 100,
              render: (x) =>
                x.status === 'OPEN' ? (
                  <Badge tone="경고" dot={false}>
                    미처리
                  </Badge>
                ) : (
                  <Badge tone="확정" dot={false}>
                    {ACTION[x.action ?? 'NONE'] ?? '처리됨'}
                  </Badge>
                ),
              sort: (x) => x.status,
            },
            {
              key: 'act',
              label: '조치',
              render: (x) =>
                x.status === 'OPEN' ? (
                  <div className="row" style={{ gap: 6 }}>
                    <button className="adm-outline card" onClick={() => act(x, 'WARNING')}>
                      경고
                    </button>
                    <button className="adm-outline card" onClick={() => act(x, 'RESTRICTED')}>
                      참가 제한
                    </button>
                    <button className="adm-outline" onClick={() => act(x, 'BANNED')}>
                      영구 제한
                    </button>
                  </div>
                ) : (
                  <span className="t-body-s">{x.handledAt ? `${md(new Date(x.handledAt))} 처리` : '처리됨'}</span>
                ),
            },
          ]}
        />
      </div>

      <div className="adm-mt-section">
        <div className="row" style={{ gap: 8 }}>
          <h2 className="t-h2" style={{ flex: 1 }}>
            매칭 제외 관계
          </h2>
          <span className="t-body-s muted">
            {kpi.pairs}쌍, 다음 매칭에 자동 반영{!demo && kpi.pairs > xrows.length ? ` (비선호·차단 ${kpi.pairs - xrows.length}쌍은 당사자 보호로 목록에 보이지 않아요)` : ''}
          </span>
        </div>
        <DataTable
          light
          className="compact"
          rows={xrows}
          rowKey={(x) => x.key}
          empty="제외 관계가 없어요"
          columns={[
            { key: 'a', label: '참가자', width: 110, render: (x) => <span className="strong">{x.a}</span>, sort: (x) => x.a },
            { key: 'b', label: '상대', width: 110, render: (x) => x.b, sort: (x) => x.b },
            {
              key: 't',
              label: '유형',
              width: 110,
              render: (x) => (
                <Badge tone={x.type === '비선호' ? '확정' : '경고'} dot={false}>
                  {x.type}
                </Badge>
              ),
              sort: (x) => x.type,
            },
            { key: 'at', label: '발생 회차', width: 110, render: (x) => x.at, sort: (x) => x.at },
            {
              key: 's',
              label: '상태',
              render: () => (
                <Badge tone="확정" dot={false}>
                  분리 중
                </Badge>
              ),
            },
          ]}
        />
      </div>

      <div className="adm-mt-section">
        <h2 className="t-h2">피드백</h2>
        <div className="adm-table-wrap light adm-fb">
          <table className="adm-table">
            <thead>
              <tr>
                <th style={{ width: 90 }}>작성자</th>
                <th style={{ width: 130 }}>테이블</th>
                <th style={{ width: 60 }}>{scoreHead('mood', '분위기')}</th>
                <th style={{ width: 60 }}>{scoreHead('talk', '대화')}</th>
                <th style={{ width: 60 }}>{scoreHead('venue', '식당')}</th>
                <th style={{ width: 80 }}>재참여</th>
                <th style={{ width: 64 }}>추천</th>
                <th style={{ width: 76 }}>공개</th>
                <th>한마디</th>
                <th style={{ width: 96 }}>노출 관리</th>
              </tr>
            </thead>
            <tbody>
              {frows.length === 0 && (
                <tr>
                  <td colSpan={10} className="adm-empty">
                    조건에 맞는 피드백이 없어요
                  </td>
                </tr>
              )}
              {frows.map((x) => {
                const isHidden = hidden.includes(x.id)
                return (
                  <tr key={x.id}>
                    <td>
                      <span className="strong">{x.name}</span>
                    </td>
                    <td>{x.table}</td>
                    <td>{x.mood}</td>
                    <td>{x.talk}</td>
                    <td>{x.venue}</td>
                    <td>
                      {x.rejoin >= 4 ? (
                        <Badge tone="완료" dot={false}>
                          있음
                        </Badge>
                      ) : x.rejoin === 3 ? (
                        <Badge tone="확정" dot={false}>
                          보통
                        </Badge>
                      ) : (
                        <Badge tone="중립" dot={false}>
                          없음
                        </Badge>
                      )}
                    </td>
                    <td>
                      <span className="strong">{x.rec}/10</span>
                    </td>
                    <td>
                      {isHidden ? (
                        <Badge tone="경고" dot={false}>
                          숨김
                        </Badge>
                      ) : x.open && x.comment ? (
                        <Badge tone="완료" dot={false}>
                          공개
                        </Badge>
                      ) : (
                        <Badge tone="중립" dot={false}>
                          비공개
                        </Badge>
                      )}
                    </td>
                    <td className={cx('t-body-s', !x.comment && 'muted')}>{x.comment || '—'}</td>
                    <td>
                      {isHidden ? (
                        <button className="adm-outline card sage" onClick={() => toggleHidden(x.id)}>
                          다시 공개
                        </button>
                      ) : x.open && x.comment ? (
                        <button className="adm-outline card sage" onClick={() => toggleHidden(x.id)}>
                          숨김
                        </button>
                      ) : (
                        <span className="t-label-s muted">—</span>
                      )}
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      </div>
      {toast.node}
    </AdminLayout>
  )
}
