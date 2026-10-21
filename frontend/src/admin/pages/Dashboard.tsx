import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { AdminLayout, DataTable, DemoNotice, Panel, Seg, useAdminApi, useServiceNow } from '../AdminLayout'
import { Badge, Button } from '../../components/ui'
import { Icon } from '../../components/Icon'
import { get } from '../../lib/api'
import type { ApplicantRow, Dashboard as DashboardDto, SlotSummary } from '../types'
import { adminDemo, slotDayLabel } from '../demo'

type Period = 'day' | 'week' | 'month'
const DAY = 86400000

/** 기준일이 속한 기간 [start, end) 와 라벨 */
function range(period: Period, anchor: Date) {
  const a = new Date(anchor.getFullYear(), anchor.getMonth(), anchor.getDate())
  if (period === 'day') return { start: a, end: new Date(a.getTime() + DAY), label: `${a.getMonth() + 1}.${a.getDate()} (${'일월화수목금토'[a.getDay()]})` }
  if (period === 'week') {
    const start = new Date(a.getTime() - ((a.getDay() + 6) % 7) * DAY) // 월요일 시작
    const end = new Date(start.getTime() + 7 * DAY)
    const nth = Math.ceil(start.getDate() / 7)
    const md = (d: Date) => `${d.getMonth() + 1}.${String(d.getDate()).padStart(2, '0')}`
    return { start, end, label: `${start.getMonth() + 1}월 ${nth}주, ${md(start)} ~ ${md(new Date(end.getTime() - DAY))}` }
  }
  const start = new Date(a.getFullYear(), a.getMonth(), 1)
  return { start, end: new Date(a.getFullYear(), a.getMonth() + 1, 1), label: `${a.getFullYear()}년 ${a.getMonth() + 1}월` }
}

function shift(period: Period, d: Date, n: number) {
  if (period === 'day') return new Date(d.getTime() + n * DAY)
  if (period === 'week') return new Date(d.getTime() + n * 7 * DAY)
  return new Date(d.getFullYear(), d.getMonth() + n, 1)
}

function Sparkline({ values, color = 'var(--mustard)' }: { values: number[]; color?: string }) {
  const max = Math.max(...values, 1)
  const min = Math.min(...values, 0)
  const pts = values.map((v, i) => `${(i / Math.max(values.length - 1, 1)) * 116 + 2},${30 - ((v - min) / (max - min || 1)) * 26}`).join(' ')
  return (
    <svg width={120} height={32} viewBox="0 0 120 32" aria-hidden>
      <polyline points={pts} fill="none" stroke={color} strokeWidth={1.5} strokeLinejoin="round" strokeLinecap="round" />
    </svg>
  )
}

function Kpi({ label, value, sub, spark, warn }: { label: string; value: string; sub: string; spark: number[]; warn?: boolean }) {
  return (
    <div className={warn ? 'adm-kpi warn' : 'adm-kpi'}>
      <span className="label">{label}</span>
      <span className="value">{value}</span>
      <span className="sub">{sub}</span>
      {spark.length > 1 && <Sparkline values={spark} color={warn ? 'var(--terracotta)' : undefined} />}
    </div>
  )
}

function BarChart({ data }: { data: { label: string; value: number }[] }) {
  const max = Math.max(...data.map((d) => d.value), 1)
  return (
    <div className="adm-bars">
      {[0, 1, 2, 3].map((i) => (
        <span key={i} className="grid" style={{ top: i * 50 }} />
      ))}
      {data.map((d, i) => (
        <div key={d.label + i} className="bar-col">
          <span className="bar-val">{d.value}</span>
          <span className="bar" style={{ height: Math.max(2, (d.value / max) * 150), background: i === data.length - 1 ? 'var(--terracotta)' : undefined }} />
          <span className="bar-label">{d.label}</span>
        </div>
      ))}
    </div>
  )
}

/** A01 운영 대시보드 (일별/주간/월별) */
export default function Dashboard() {
  const navigate = useNavigate()
  const [period, setPeriod] = useState<Period>('week')
  // 기준일 기본값 = 서비스 시각 (시연 시각 이동 반영)
  const now = useServiceNow()
  const [picked, setAnchor] = useState<Date | null>(null)
  const anchor = picked ?? now
  const dash = useAdminApi(() => get<DashboardDto>('/api/admin/dashboard'), adminDemo.dashboard)
  const apps = useAdminApi(() => get<ApplicantRow[]>('/api/admin/applications'), [] as ApplicantRow[])
  const r = range(period, anchor)
  const { kpi, pending } = dash.data

  // 기간별 신청 수 (실데이터: 신청 제출 시각 기준)
  const series = useMemo(() => {
    if (dash.demo) return adminDemo.trend[period]
    const out: { label: string; value: number }[] = []
    const count = period === 'month' ? 6 : 8
    for (let i = count - 1; i >= 0; i--) {
      const rr = range(period, shift(period, anchor, -i))
      const v = apps.data.filter((a) => {
        const t = new Date(a.submittedAt).getTime()
        return t >= rr.start.getTime() && t < rr.end.getTime()
      }).length
      const s = rr.start
      out.push({ label: period === 'month' ? `${s.getMonth() + 1}월` : `${s.getMonth() + 1}.${String(s.getDate()).padStart(2, '0')}`, value: v })
    }
    return out
  }, [dash.demo, apps.data, period, anchor])

  const byRegion = useMemo(() => {
    if (dash.demo) return adminDemo.regions
    const m = new Map<string, number>()
    apps.data.forEach((a) => a.slotLabels.forEach((l) => {
      const region = l.split(' ').slice(-1)[0]
      m.set(region, (m.get(region) ?? 0) + 1)
    }))
    return [...m.entries()].map(([label, value]) => ({ label, value })).sort((a, b) => b.value - a.value)
  }, [dash.demo, apps.data])
  const regionMax = Math.max(...byRegion.map((x) => x.value), 1)

  const current = series[series.length - 1]?.value ?? 0
  const prev = series[series.length - 2]?.value ?? 0
  const diff = prev ? Math.round(((current - prev) / prev) * 100) : 0
  const periodWord = { day: '오늘', week: '이번 주', month: '이번 달' }[period]
  const prevWord = { day: '어제', week: '지난주', month: '지난달' }[period]
  const reallocOk = Math.round((kpi.reallocatedAttempts * kpi.reallocationSuccessRate) / 100)

  // 회차별 현황: 날짜별로 묶는다
  const byDate = useMemo(() => {
    const groups = new Map<string, SlotSummary[]>()
    dash.data.slots.forEach((s) => {
      const key = s.label.split(' ').slice(0, 2).join(' ')
      groups.set(key, [...(groups.get(key) ?? []), s])
    })
    return [...groups.entries()].map(([date, list]) => ({
      date,
      list,
      applicants: list.reduce((n, s) => n + s.applicants, 0),
      paid: list.reduce((n, s) => n + s.paid, 0),
      tables: list.reduce((n, s) => n + s.tables, 0),
      confirmed: list.reduce((n, s) => n + s.confirmed, 0),
      failed: list.reduce((n, s) => n + s.failed, 0),
    }))
  }, [dash.data.slots])
  const funnel = byDate.find((d) => d.tables > 0) ?? byDate[0]
  // 실제 참가: 실데이터는 해당 회차 신청의 참석 상태로 센다 (시안은 참석률로 추정)
  const attended = !funnel
    ? 0
    : dash.demo
      ? Math.round((funnel.confirmed * kpi.attendanceRate) / 100)
      : apps.data.filter((a) => a.attendanceStatus === 'ATTENDED' && a.slotLabels.some((l) => funnel.list.some((s) => s.label === l))).length

  // 매칭이 끝난 회차들 (오래된 순): 추이선용
  const pastRuns = [...byDate].filter((d) => d.tables > 0 || d.failed > 0).reverse()
  // 선택 대기 기한 = 실패한 회차 당일 12:00 (지나면 자동 보관)
  const waitingRun = [...byDate].reverse().find((d) => d.failed > 0 && d.list.some((s) => s.status === 'MATCHED'))
  const resolutionSub = pending.awaitingResolution === 0 ? '선택 대기 없음' : waitingRun ? `기한 ${slotDayLabel(waitingRun.date).split(' ')[0]} 12:00, 지나면 자동 보관` : '선택 기한 전'

  // 행사가 끝난 회차만 실제 참가를 보여준다
  const held = !!funnel && (dash.demo || funnel.list.some((s) => s.status === 'COMPLETED'))

  const exceptions: [string, string, number, string][] = [
    ['조건 충돌', '연령 조건', pending.unresolvedConflicts, 'UNRESOLVED_CONFLICT'],
    ['식당 부족', '식당 미배정 테이블', pending.tablesWithoutVenue, 'VENUE_SHORTAGE'],
    ['알림 발송 실패', '리마인드 3회 실패', pending.messageFailures, 'NOTIFICATION_FAILED'],
    ['노쇼 후보', '당일 취소·노쇼 검토', pending.lateCancelOrNoShowCandidates, 'NO_SHOW_REVIEW'],
    ['미처리 신고', '안전 신고', pending.openReports, 'SAFETY_REPORT'],
    ['환불 실패', '', pending.refundFailures, 'REFUND_FAILED'],
  ]

  return (
    <AdminLayout title="운영 대시보드">
      <DemoNotice show={dash.demo} />
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

      <div className="adm-row">
        <Kpi label={`${periodWord} 신청`} value={String(dash.demo ? adminDemo.kpiHead[period] : current)} sub={`${prevWord} 대비 ${diff >= 0 ? '+' : ''}${dash.demo ? adminDemo.kpiDiff[period] : diff + '%'}`} spark={series.map((s) => s.value)} />
        <Kpi label="확정 테이블" value={String(kpi.confirmedTables)} sub={`${kpi.confirmedParticipants}명, 자동 확정 ${Math.round(kpi.autoConfirmRate)}%`} spark={dash.demo ? [3, 5, 4, 7, 6, 8, 9] : pastRuns.map((d) => d.tables)} />
        <Kpi label="재배치 성공" value={`${reallocOk} / ${kpi.reallocatedAttempts}`} sub={`${Math.round(kpi.reallocationSuccessRate)}%`} spark={dash.demo ? [4, 6, 5, 7, 8, 9, 9] : []} />
        <Kpi label="선택 대기" value={`${pending.awaitingResolution}명`} sub={dash.demo ? adminDemo.resolutionDeadline : resolutionSub} spark={dash.demo ? [2, 3, 2, 5, 4, 7, 6] : pastRuns.map((d) => d.failed)} warn />
      </div>

      <div className="adm-row">
        <Panel title={`${{ day: '일별', week: '주간', month: '월별' }[period]} 신청 추이`}>
          <BarChart data={series} />
        </Panel>
        <Panel title="지역별 신청">
          {byRegion.map((x, i) => (
            <div key={x.label} className="row" style={{ gap: 12 }}>
              <span className="t-body-s" style={{ width: 90 }}>
                {x.label}
              </span>
              <span className="adm-bar">
                <i style={{ width: `${(x.value / regionMax) * 100}%`, background: i === byRegion.length - 1 && byRegion.length > 2 ? 'var(--terracotta)' : undefined }} />
              </span>
              <b className="t-label-m">{x.value}</b>
            </div>
          ))}
          {byRegion.length === 0 && <p className="t-body-s muted">아직 신청이 없어요</p>}
        </Panel>
      </div>

      <div className="adm-row">
        <Panel title="처리할 예외" action={<Button size="S" onClick={() => navigate('/admin/exceptions')}>예외 처리함</Button>}>
          <div>
            {exceptions.map(([title, sub, n, type]) => (
              <button key={title} className={n ? 'adm-list-row' : 'adm-list-row zero'} onClick={() => navigate(`/admin/exceptions?type=${type}`)}>
                <span className="stack" style={{ flex: 1 }}>
                  <span className="t-body-m">{title}</span>
                  {sub && n > 0 && <span className="t-body-s muted">{sub}</span>}
                </span>
                <span className="num">{n}</span>
                <Icon name="chevron-right" size={18} color={n ? undefined : 'var(--sage)'} />
              </button>
            ))}
          </div>
        </Panel>
        {funnel && (
          <Panel title={`${slotDayLabel(funnel.date).split(" ")[0]} 회차 전환`} className="gap18">
            {(
              [
                ['신청', funnel.applicants],
                ['결제', funnel.paid],
                ['확정', funnel.confirmed],
                ['실제 참가', held ? attended : null],
              ] as const
            ).map(([l, v]) => {
              const pct = funnel.applicants && v !== null ? Math.round((v / funnel.applicants) * 100) : 0
              return (
                <div key={l} className="stack" style={{ gap: 6 }}>
                  <div className="row">
                    <span className="t-body-m" style={{ flex: 1 }}>
                      {l}
                    </span>
                    {v === null ? (
                      <span className="t-body-s muted">행사 전</span>
                    ) : (
                      <>
                        <b className="t-label-m">{v}명</b>
                        <span className="t-body-s muted">&nbsp;&nbsp;{pct}%</span>
                      </>
                    )}
                  </div>
                  <span className="adm-bar thin">
                    <i style={{ width: `${pct}%` }} />
                  </span>
                </div>
              )
            })}
          </Panel>
        )}
      </div>

      <div className="adm-section-head">
        <h2 className="t-h2">회차별 현황</h2>
        <Button size="S" variant="secondary" style={{ borderColor: 'var(--ink)' }} onClick={() => window.open('/api/admin/applications/export.csv', '_blank')}>
          CSV 내보내기
        </Button>
      </div>
      <DataTable
        rows={byDate}
        rowKey={(d) => d.date}
        onRow={() => navigate('/admin/slots')}
        columns={[
          { key: 'date', label: '날짜', render: (d) => <span className="strong">{slotDayLabel(d.date)}</span>, sort: (d) => d.date },
          { key: 'time', label: '시간', render: () => '19:30' },
          { key: 'region', label: '지역', render: (d) => <span className="ellipsis" style={{ display: 'block' }}>{d.list.map((s) => s.label.split(' ').slice(-1)[0]).join(', ')}</span> },
          { key: 'app', label: '신청', render: (d) => d.applicants, sort: (d) => d.applicants },
          { key: 'paid', label: '결제', render: (d) => d.paid, sort: (d) => d.paid },
          { key: 'tables', label: '확정 테이블', render: (d) => (d.tables ? `${d.tables}개` : '—'), sort: (d) => d.tables },
          { key: 'conf', label: '확정 인원', render: (d) => (d.confirmed ? `${d.confirmed}명` : '—'), sort: (d) => d.confirmed },
          { key: 'wait', label: '선택 대기', render: (d) => (d.failed ? `${d.failed}명` : '—'), sort: (d) => d.failed },
          { key: 'run', label: '자동 매칭', render: (d) => adminDemo.runLabel(d.list[0]) },
          { key: 'status', label: '상태', render: (d) => <Badge dot={false}>{adminDemo.statusLabel(d.list[0].status)}</Badge> },
        ]}
      />
    </AdminLayout>
  )
}
