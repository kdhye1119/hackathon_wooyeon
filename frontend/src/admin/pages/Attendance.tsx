import { useMemo, useState } from 'react'
import { AdminLayout, DataTable, DemoNotice, SessionPicker, useAdminApi, cx } from '../AdminLayout'
import { Badge, useToast } from '../../components/ui'
import { get, post } from '../../lib/api'
import type { ApplicantRow, AttendanceRow } from '../types'
import { adminDemo, slotDayLabel, tableLabel } from '../demo'

type Row = { key: string; applicationId: number; name: string; table: string; checkIn: string; status: string; noShows: number | null; review: boolean }

const STATUS: Record<string, [string, '확정' | '경고' | '중립' | '완료']> = {
  ATTENDED: ['참석', '확정'],
  CHECKED_IN: ['참석', '확정'],
  SCHEDULED: ['참석 예정', '중립'],
  NO_SHOW: ['노쇼 후보', '경고'],
  NO_SHOW_CONFIRMED: ['노쇼 확정', '경고'],
  CANCELED_LATE: ['당일 취소', '확정'],
  CANCELED_EARLY: ['사전 취소', '중립'],
}

// Figma 15:804 시안 데이터
const DEMO = {
  date: '10.15 (목)',
  kpi: { attended: 64, early: 2, late: 1, noShow: 3, checked: 64, total: 70 },
  rows: [
    ['노OO', 'T-1015-EJ-1', '—', 'NO_SHOW', 1, true],
    ['강OO', 'T-1015-HD-2', '—', 'NO_SHOW', 2, true],
    ['임OO', 'T-1015-HD-2', '—', 'NO_SHOW', 0, true],
    ['서OO', 'T-1015-GN-1', '—', 'CANCELED_LATE', null, false],
    ['김서윤', 'T-1015-EJ-1', '19:24', 'ATTENDED', null, false],
  ].map(([name, table, checkIn, status, noShows, review], i) => ({ key: String(i), applicationId: 300 + i, name, table, checkIn, status, noShows, review }) as Row),
}

const hm = (iso: string | null) => (iso ? `${String(new Date(iso).getHours()).padStart(2, '0')}:${String(new Date(iso).getMinutes()).padStart(2, '0')}` : '—')

/** A09 참석/취소/노쇼 */
export default function Attendance() {
  const toast = useToast()
  const list = useAdminApi(() => get<AttendanceRow[]>('/api/admin/attendance'), adminDemo.attendance)
  const apps = useAdminApi(() => get<ApplicantRow[]>('/api/admin/applications'), adminDemo.applicants)
  const demo = list.demo
  const [done, setDone] = useState<Record<number, string>>({})

  const dates = useMemo(() => [...new Set(list.data.map((a) => slotDayLabel(a.slotLabel)))].sort(), [list.data])
  const [picked, setPicked] = useState<string | null>(null)
  // 기본: 가장 최근 지난 회차
  const date = picked ?? (demo ? DEMO.date : dates[dates.length - 1] ?? '')
  const idx = dates.indexOf(date)

  const noShowOf = useMemo(() => new Map(apps.data.map((a) => [a.applicationId, a.noShows])), [apps.data])
  const day = list.data.filter((a) => slotDayLabel(a.slotLabel) === date)
  const rows: Row[] = demo
    ? DEMO.rows
    : day
        .map((a) => ({
          key: String(a.applicationId),
          applicationId: a.applicationId,
          name: a.name,
          table: tableLabel(a.tableCode, a.slotLabel),
          checkIn: hm(a.checkedInAt),
          status: a.attendanceStatus,
          noShows: a.attendanceStatus === 'NO_SHOW' ? noShowOf.get(a.applicationId) ?? 0 : null,
          review: a.openReview,
        }))
        .sort((x, y) => Number(y.review) - Number(x.review))
  const kpi = demo
    ? DEMO.kpi
    : {
        attended: day.filter((a) => a.attendanceStatus === 'ATTENDED').length,
        early: day.filter((a) => a.attendanceStatus === 'CANCELED_EARLY').length,
        late: day.filter((a) => a.attendanceStatus === 'CANCELED_LATE').length,
        noShow: day.filter((a) => a.attendanceStatus === 'NO_SHOW').length,
        checked: day.filter((a) => a.checkedInAt).length,
        total: day.filter((a) => a.attendanceStatus !== 'CANCELED_EARLY').length,
      }

  const review = async (r: Row, noShow: boolean, note: string) => {
    try {
      if (!demo) await post(`/api/admin/attendance/${r.applicationId}/review`, { noShow, note })
      setDone({ ...done, [r.applicationId]: noShow ? note : '출석 처리' })
      toast.show(`${r.name}: ${noShow ? note : '출석으로 정정했어요'}`)
      if (!demo) list.reload()
    } catch (e) {
      toast.show(e instanceof Error ? e.message : '처리하지 못했어요')
    }
  }

  return (
    <AdminLayout title="참석/취소/노쇼">
      <DemoNotice show={demo} />
      <div className="adm-mt-bar">
        <SessionPicker
          value={date}
          options={dates.map((d) => [d, `${d} 회차`])}
          onChange={setPicked}
          onPrev={idx > 0 ? () => setPicked(dates[idx - 1]) : undefined}
          onNext={idx >= 0 && idx < dates.length - 1 ? () => setPicked(dates[idx + 1]) : undefined}
        />
        <button className="adm-today" onClick={() => setPicked(dates[dates.length - 1] ?? null)}>
          오늘
        </button>
        <span style={{ flex: 1 }} />
        <span className="t-body-s muted">
          체크인 {kpi.checked} / {kpi.total}
        </span>
      </div>

      <div className="adm-att-stats">
        {[
          ['참석', kpi.attended, false],
          ['사전 취소', kpi.early, false],
          ['당일 취소', kpi.late, true],
          ['노쇼 후보', kpi.noShow, true],
        ].map(([l, n, warn]) => (
          <div key={l as string}>
            <span>{l}</span>
            <b className={cx(warn ? 'warn' : false)}>{n}</b>
          </div>
        ))}
      </div>

      <DataTable
        className="adm-att-table"
        rows={rows}
        rowKey={(r) => r.key}
        empty="이 회차 참석 기록이 없어요"
        columns={[
          { key: 'name', label: '참가자', width: 110, render: (r) => <span className={cx(r.review && !done[r.applicationId] && 'adm-att-hl')}>{r.name}</span>, sort: (r) => r.name },
          { key: 'table', label: '테이블', width: 130, render: (r) => r.table, sort: (r) => r.table },
          { key: 'in', label: '체크인', width: 100, render: (r) => r.checkIn, sort: (r) => r.checkIn },
          {
            key: 'st',
            label: '상태',
            width: 160,
            render: (r) => {
              const [l, tone] = STATUS[r.status] ?? [r.status, '중립']
              return (
                <Badge tone={tone} dot={false}>
                  {l}
                </Badge>
              )
            },
            sort: (r) => r.status,
          },
          { key: 'ns', label: '누적 노쇼', width: 90, render: (r) => (r.noShows === null ? '—' : `${r.noShows}회`), sort: (r) => r.noShows ?? -1 },
          {
            key: 'act',
            label: '조치',
            render: (r) => {
              if (done[r.applicationId]) return <span className="t-body-s">{done[r.applicationId]}</span>
              if (r.status === 'NO_SHOW')
                return (
                  <div className="row" style={{ gap: 6 }}>
                    {/* 누적 노쇼 2회 이상이면 30일 참여 제한 */}
                    <button className="adm-outline card" onClick={() => review(r, true, (r.noShows ?? 0) >= 2 ? '30일 제한' : '경고')}>
                      {(r.noShows ?? 0) >= 2 ? '30일 제한' : '경고'}
                    </button>
                    <button className="adm-outline" onClick={() => review(r, false, '')}>
                      출석 처리
                    </button>
                  </div>
                )
              if (r.status === 'CANCELED_LATE')
                return (
                  <button
                    className="adm-outline card"
                    onClick={() => {
                      // 당일 취소 경고는 서버 API 가 없어 화면에만 기록
                      setDone({ ...done, [r.applicationId]: '경고' })
                      toast.show(`${r.name}: 당일 취소 경고를 남겼어요`)
                    }}
                  >
                    경고
                  </button>
                )
              return '—'
            },
          },
        ]}
      />
      {toast.node}
    </AdminLayout>
  )
}
