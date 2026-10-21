import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { AdminLayout, BarChart, DataTable, DemoNotice, SessionPicker, useAdminApi, serviceNow } from '../AdminLayout'
import { Badge, Button } from '../../components/ui'
import { Icon } from '../../components/Icon'
import { get } from '../../lib/api'
import type { AdminSlot, ApplicantRow, MatchRun, Policy, TableRow } from '../types'
import { adminDemo, slotDayLabel, slotRegion, tableLabel } from '../demo'

/** 확정 테이블 행 (실데이터는 연령 범위 대신 나이 차) */
type TRow = TableRow & { ageRange?: string; region?: string }

// Figma 13:468 시안 데이터
const DEMO = {
  date: '2026-10-22',
  confirmedTables: 19,
  confirmedPeople: 96,
  unassigned: 11,
  avgScore: 71,
  realloc: { ok: 14, total: 18 },
  buckets: [1, 2, 3, 5, 4, 3, 1],
  reallocRows: [
    ['김서윤', '을지로/종로 (3명)', 'T-1022-EJ-3', '4명 미만이라 병합'],
    ['정민재', '홍대/합정 (7명)', 'T-1022-EJ-3', '7명 이상이라 분리'],
    ['이도현', '강남/신논현', '미배치', '연령 조건 불충족'],
    ['최현우', '10.22 홍대/합정', '11.05 홍대/합정', '정원 초과, 다른 날짜로'],
  ],
  tables: [
    [7, 'T-1022-EJ-3', '을지로/종로', '을지 화로', 5, '27~33세', 82],
    [9, 'T-1022-HD-1', '홍대/합정', '연남 비스트로', 6, '25~31세', 76],
    [11, 'T-1022-GN-2', '강남/신논현', '르 코랄', 4, '30~37세', 63],
  ].map(([tableId, tableCode, region, venueName, memberCount, ageRange, totalScore]) => ({
    ...adminDemo.tables[0],
    tableId: tableId as number,
    tableCode: tableCode as string,
    region: region as string,
    venueName: venueName as string,
    memberCount: memberCount as number,
    ageRange: ageRange as string,
    totalScore: totalScore as number,
  })) as TRow[],
  unassignedRows: [
    ['이도현', '10.22', '강남/신논현', '연령 조건', 'WAIT'],
    ['박지민', '10.22', '잠실', '인원 부족', 'REFUND'],
    ['최현우', '10.22, 11.05', '홍대/합정', '정원 초과', 'MOVE'],
    ['한승우', '10.22', '강남/신논현', '연령 조건', 'EXCEPTION'],
  ],
}

/** 점수 구간: 최소 매칭 점수부터 5점 단위, 90점 이상은 한 구간 (시안: 60~64 … 90+) */
function bucketsFrom(minScore: number) {
  const start = Math.min(85, Math.floor(minScore / 5) * 5)
  const labels: string[] = []
  for (let s = start; s < 90; s += 5) labels.push(`${s}~${s + 4}`)
  labels.push('90+')
  const of = (score: number) => (score >= 90 ? labels.length - 1 : Math.max(0, Math.floor((score - start) / 5)))
  return { labels, of, start }
}

/** 미배치 상태 배지 */
function StateBadge({ s }: { s: string }) {
  if (s === 'REFUND') return <Badge tone="중립" dot={false}>환불 선택</Badge>
  if (s === 'MOVE') return <Badge tone="확정" dot={false}>일정 이동</Badge>
  if (s === 'EXCEPTION') return <Badge tone="경고" dot={false}>예외</Badge>
  return <Badge tone="경고" dot={false}>선택 대기</Badge>
}
const unassignedState = (a: ApplicantRow) =>
  a.matchStatus === 'EXCEPTION' ? 'EXCEPTION' : ['REFUND_REQUESTED', 'REFUND_PROCESSING', 'REFUNDED'].includes(a.paymentStatus) ? 'REFUND' : a.matchStatus === 'TRANSFERRED' ? 'MOVE' : 'WAIT'

const md = (iso: string) => {
  const d = new Date(iso)
  return `${String(d.getMonth() + 1).padStart(2, '0')}.${String(d.getDate()).padStart(2, '0')}`
}

function downloadCsv(rows: TRow[], date: string) {
  const head = ['테이블', '회차', '식당', '인원', '나이 차', '남', '여', '점수', '구성']
  const body = rows.map((t) => [t.tableCode, t.slotLabel, t.venueName ?? '미배정', t.memberCount, t.ageGap, t.male, t.female, t.totalScore, t.formation])
  const csv = [head, ...body].map((r) => r.map((c) => `"${String(c).replace(/"/g, '""')}"`).join(',')).join('\n')
  const a = document.createElement('a')
  a.href = URL.createObjectURL(new Blob(['﻿' + csv], { type: 'text/csv' }))
  a.download = `tables-${date}.csv`
  a.click()
}

/** A06 매칭 정보 */
export default function Matching() {
  const navigate = useNavigate()
  const slots = useAdminApi(() => get<AdminSlot[]>('/api/admin/slots'), adminDemo.slots)
  const runs = useAdminApi(() => get<MatchRun[]>('/api/admin/match-runs'), adminDemo.matchRuns)
  const tables = useAdminApi(() => get<TableRow[]>('/api/admin/tables'), adminDemo.tables)
  const apps = useAdminApi(() => get<ApplicantRow[]>('/api/admin/applications'), adminDemo.applicants)
  const policy = useAdminApi(() => get<Policy>('/api/admin/policy'), adminDemo.policy)
  const demo = slots.demo

  // 회차 = 날짜. 매칭이 끝난 날짜를 우선으로 보여준다
  const dates = useMemo(() => [...new Set(slots.data.map((s) => s.date))].sort(), [slots.data])
  const defaultDate = demo ? DEMO.date : (slots.data.filter((s) => s.status !== 'RECRUITING').map((s) => s.date).sort().pop() ?? dates[0])
  const [picked, setPicked] = useState<string | null>(null)
  const date = picked ?? defaultDate ?? ''
  const daySlots = slots.data.filter((s) => s.date === date)
  const labels = new Set(daySlots.map((s) => s.label))
  const matched = daySlots.some((s) => s.status !== 'RECRUITING' && s.status !== 'CANCELED')
  const run = runs.data.find((r) => r.slotLabels.some((l) => labels.has(l)))

  const dayTables: TRow[] = demo ? DEMO.tables : tables.data
        .filter((t) => labels.has(t.slotLabel))
        .map((t) => ({ ...t, tableCode: tableLabel(t.tableCode, t.slotLabel), region: slotRegion(t.slotLabel) }))
        .sort((a, b) => a.tableCode.localeCompare(b.tableCode))
  const dayApps = apps.data.filter((a) => a.slotLabels.some((l) => labels.has(l)))
  const unassigned = dayApps.filter((a) => a.paymentStatus !== 'PENDING' && !a.tableCode && ['NO_MATCH', 'EXCEPTION', 'ALTERNATIVE_OFFERED', 'TRANSFERRED'].includes(a.matchStatus))
  const reallocated = dayApps.filter((a) => a.reallocationCount > 0)

  const confirmedTables = demo ? DEMO.confirmedTables : dayTables.length
  const confirmedPeople = demo ? DEMO.confirmedPeople : dayTables.reduce((n, t) => n + t.memberCount, 0)
  const unassignedCount = demo ? DEMO.unassigned : unassigned.length
  const avgScore = demo ? DEMO.avgScore : dayTables.length ? Math.round(dayTables.reduce((n, t) => n + t.totalScore, 0) / dayTables.length) : 0
  const realloc = demo ? DEMO.realloc : { ok: reallocated.filter((a) => a.tableCode).length, total: reallocated.length }
  const minScore = demo ? 60 : Math.round(policy.data.minScore)
  const bk = bucketsFrom(minScore)
  const buckets = demo
    ? DEMO.buckets
    : dayTables.reduce((b, t) => {
        if (t.totalScore >= bk.start) b[bk.of(t.totalScore)]++
        return b
      }, Array(bk.labels.length).fill(0) as number[])

  const reallocRows: string[][] = demo
    ? DEMO.reallocRows
    : reallocated.map((a) => {
        const t = tables.data.find((x) => x.tableId === a.tableId)
        return [a.name, slotRegion(a.slotLabels[0] ?? ''), a.tableCode ? tableLabel(a.tableCode, t?.slotLabel) : '미배치', a.statusReason ?? '재배치']
      })
  const unassignedRows: string[][] = demo
    ? DEMO.unassignedRows
    : unassigned.map((a) => [a.name, a.slotLabels.map((l) => slotDayLabel(l).split(' ')[0]).join(', '), [...new Set(a.slotLabels.map(slotRegion))].join(', '), a.statusReason ?? '조건 불충족', unassignedState(a)])

  const runAt = run?.finishedAt ?? daySlots[0]?.matchAt
  const runText = !matched ? (runAt ? `${md(runAt)} 24:00 자동 매칭 예정` : '') : demo ? '10.20 24:00 자동 매칭 완료' : runAt ? `${md(daySlots[0]?.matchAt ?? runAt)} 24:00 자동 매칭 완료` : '자동 매칭 완료'

  const idx = dates.indexOf(date)
  const today = serviceNow().toISOString().slice(0, 10)

  return (
    <AdminLayout title="매칭 정보">
      <DemoNotice show={demo} />
      <div className="adm-mt-bar">
        <SessionPicker
          value={date}
          options={dates.map((d) => [d, `${slotDayLabel(`${Number(d.slice(5, 7))}/${Number(d.slice(8))} (${'일월화수목금토'[new Date(d).getDay()]})`)} 회차`])}
          onChange={setPicked}
          onPrev={idx > 0 ? () => setPicked(dates[idx - 1]) : undefined}
          onNext={idx < dates.length - 1 ? () => setPicked(dates[idx + 1]) : undefined}
        />
        <button className="adm-today" onClick={() => setPicked(dates.find((d) => d >= today) ?? dates[dates.length - 1])}>
          오늘
        </button>
        <span style={{ flex: 1 }} />
        <span className="t-body-s muted">{runText}</span>
      </div>

      <div className="adm-mt-stats">
        <div className="adm-mt-card">
          <span className="label">확정 테이블</span>
          <b>{confirmedTables}개</b>
          <span className="label">{confirmedPeople}명</span>
        </div>
        <div className="adm-mt-card warn">
          <span className="label">미배치</span>
          <b>{unassignedCount}명</b>
        </div>
        <div className="adm-mt-card">
          <span className="label">평균 매칭 점수</span>
          <b>{avgScore}점</b>
        </div>
      </div>

      <section className="adm-panel">
        <div className="adm-panel-head">
          <h2 className="t-h2">테이블 점수 분포</h2>
          <span className="t-body-s muted">최소 {minScore}점</span>
        </div>
        <BarChart items={buckets.map((n, i) => ({ label: bk.labels[i], value: n, text: `${n}개`, hot: i === 0 }))} />
      </section>

      <div className="adm-mt-section">
        <div className="adm-section-head">
          <h2 className="t-h2">확정 테이블</h2>
          <Button size="S" variant="secondary" onClick={() => downloadCsv(dayTables, date)}>
            테이블 결과 CSV
          </Button>
        </div>
        <DataTable
          light
          rows={dayTables}
          rowKey={(t) => t.tableId}
          onRow={(t) => navigate(`/admin/tables/${t.tableId}`)}
          empty={matched ? '확정된 테이블이 없어요' : '아직 매칭 전이에요'}
          columns={[
            { key: 'code', label: '테이블', width: 130, render: (t) => <span className="strong">{t.tableCode}</span>, sort: (t) => t.tableCode },
            { key: 'region', label: '지역', width: 120, render: (t) => t.region, sort: (t) => t.region ?? '' },
            { key: 'venue', label: '식당', width: 150, render: (t) => (t.venueName ? t.venueName : <Badge tone="경고" dot={false}>식당 미배정</Badge>), sort: (t) => t.venueName ?? '' },
            { key: 'n', label: '인원', width: 70, render: (t) => `${t.memberCount}명`, sort: (t) => t.memberCount },
            { key: 'age', label: '연령', width: 110, render: (t) => t.ageRange ?? `${t.ageGap}살 차`, sort: (t) => t.ageGap },
            { key: 'score', label: '점수', render: (t) => Math.round(t.totalScore), sort: (t) => t.totalScore },
            { key: 'go', label: '', width: 18, render: () => <Icon name="chevron-right" size={18} /> },
          ]}
        />
      </div>

      <div className="adm-mt-section">
        <div className="adm-section-head">
          <h2 className="t-h2">재배치 내역</h2>
          <b className="t-label-m">
            {realloc.ok} / {realloc.total}명 성공
          </b>
        </div>
        <DataTable
          light
          className="compact"
          rows={reallocRows}
          rowKey={(r) => r.join('|')}
          empty="재배치된 신청자가 없어요"
          columns={[
            { key: 'name', label: '이름', width: 120, render: (r) => <span className="strong">{r[0]}</span>, sort: (r) => r[0] },
            { key: 'from', label: '최초', width: 170, render: (r) => r[1], sort: (r) => r[1] },
            { key: 'to', label: '최종', width: 170, render: (r) => r[2], sort: (r) => r[2] },
            { key: 'why', label: '사유', render: (r) => r[3], sort: (r) => r[3] },
          ]}
        />
      </div>

      <div className="adm-mt-section">
        <h2 className="t-h2">미배치</h2>
        <DataTable
          light
          rows={unassignedRows}
          rowKey={(r) => r.join('|')}
          empty="미배치된 신청자가 없어요"
          columns={[
            { key: 'name', label: '이름', width: 130, render: (r) => <span className="strong">{r[0]}</span>, sort: (r) => r[0] },
            { key: 'slot', label: '회차', width: 104, render: (r) => <span className="t-body-s">{r[1]}</span>, sort: (r) => r[1] },
            { key: 'region', label: '지역', width: 104, render: (r) => <span className="t-body-s">{r[2]}</span>, sort: (r) => r[2] },
            { key: 'reason', label: '사유', render: (r) => r[3], sort: (r) => r[3] },
            { key: 'state', label: '상태', width: 130, render: (r) => <StateBadge s={r[4]} />, sort: (r) => r[4] },
          ]}
        />
      </div>
    </AdminLayout>
  )
}
