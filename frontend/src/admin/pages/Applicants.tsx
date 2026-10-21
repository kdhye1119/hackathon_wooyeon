import { useMemo, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { AdminLayout, DataTable, DemoNotice, SessionPicker, useAdminApi, serviceNow } from '../AdminLayout'
import { Badge, Button } from '../../components/ui'
import { get } from '../../lib/api'
import type { ApplicantRow } from '../types'
import { adminDemo, genderShort, productShort, slotDayLabel, slotRegion } from '../demo'

type Row = {
  key: string
  name: string
  phone: string
  birth: number | null
  gender: string
  mbti: string
  slots: string
  region: string
  product: string
  pay: string
  match: string
  attended: number
  reports: number
  tableId: number | null
  tableCode: string | null
}

type Tone = '완료' | '확정' | '경고' | '중립'
const PAY: Record<string, [string, Tone]> = {
  PAID: ['결제 완료', '완료'],
  PENDING: ['결제 대기', '확정'],
  FAILED: ['결제 실패', '경고'],
  REFUND_AVAILABLE: ['환불 가능', '확정'],
  REFUND_REQUESTED: ['환불 중', '확정'],
  REFUND_PROCESSING: ['환불 중', '확정'],
  REFUND_FAILED: ['환불 실패', '경고'],
  REFUNDED: ['환불 완료', '중립'],
}
const MATCH: Record<string, [string, Tone]> = {
  WAITING: ['매칭 전', '중립'],
  MATCHING: ['매칭 중', '확정'],
  REALLOCATING: ['재배치 중', '확정'],
  CONFIRMED: ['매칭 완료', '완료'],
  ALTERNATIVE_OFFERED: ['선택 대기', '경고'],
  NO_MATCH: ['선택 대기', '경고'],
  TRANSFERRED: ['일정 이동', '확정'],
  EXCEPTION: ['예외', '경고'],
}

// Figma 154:1798 시안 데이터
const DEMO: Row[] = [
  ['김서윤', 1999, '여', 'ENFP', '10.22, 10.29', '을지로/종로', '1회권', 'PAID', 'CONFIRMED', 2, 0],
  ['박도현', 1993, '남', 'INTJ', '10.22', '을지로/종로', '멤버십 6개월', 'PAID', 'CONFIRMED', 6, 0],
  ['이도현', 1996, '남', 'ISFP', '10.22', '강남/신논현', '멤버십 3개월', 'PAID', 'NO_MATCH', 0, 0],
  ['최현우', 1990, '남', 'ESTJ', '10.22, 11.05', '홍대/합정', '멤버십 1개월', 'PAID', 'TRANSFERRED', 4, 0],
  ['한승우', 1994, '남', 'ENTJ', '10.22', '강남/신논현', '1회권', 'PAID', 'EXCEPTION', 1, 1],
  ['정예린', 1998, '여', 'INFJ', '10.22', '을지로/종로', '1회권', 'PENDING', 'WAITING', 0, 0],
  ['오세린', 2000, '여', 'ESFP', '10.22', '홍대/합정', '1회권', 'FAILED', 'WAITING', 0, 0],
].map(([name, birth, gender, mbti, slots, region, product, pay, match, attended, reports], i) => ({
  key: String(i),
  name: name as string,
  phone: '',
  birth: birth as number,
  gender: gender as string,
  mbti: mbti as string,
  slots: slots as string,
  region: region as string,
  product: product as string,
  pay: pay as string,
  match: match as string,
  attended: attended as number,
  reports: reports as number,
  tableId: match === 'CONFIRMED' ? 7 : null,
  tableCode: null,
}))

const toRow = (a: ApplicantRow): Row => ({
  key: String(a.applicationId),
  name: a.name,
  phone: a.phone,
  birth: a.birthYear,
  gender: genderShort(a.gender),
  mbti: a.mbti ?? '—',
  slots: [...new Set(a.slotLabels.map((l) => slotDayLabel(l).split(' ')[0]))].join(', '),
  region: [...new Set(a.slotLabels.map(slotRegion))].join(', ') || a.otherRegion || '—',
  product: productShort(a.product),
  pay: a.paymentStatus,
  match: a.matchStatus,
  attended: a.pastAttended,
  reports: a.reportsReceived,
  tableId: a.tableId,
  tableCode: a.tableCode,
})

const tag = (map: Record<string, [string, Tone]>, s: string) => {
  const [l, tone] = map[s] ?? [s, '중립']
  return (
    <Badge tone={tone} dot={false}>
      {l}
    </Badge>
  )
}

/** A05-1 신청자 목록 */
export default function Applicants() {
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const q = (params.get('q') ?? '').trim()
  const apps = useAdminApi(() => get<ApplicantRow[]>('/api/admin/applications'), adminDemo.applicants)
  const demo = apps.demo

  // 회차(날짜) 목록 = 신청서의 희망 회차들
  const dates = useMemo(() => {
    const s = new Set<string>()
    apps.data.forEach((a) => a.slotLabels.forEach((l) => s.add(slotDayLabel(l))))
    return [...s].sort((a, b) => a.localeCompare(b))
  }, [apps.data])
  const [picked, setPicked] = useState<string | null>(null)
  const date = picked ?? (demo ? '10.22 (목)' : dates.find((d) => d.startsWith('10.22')) ?? dates[0] ?? '')

  const all = demo ? DEMO : apps.data.filter((a) => q || a.slotLabels.some((l) => slotDayLabel(l) === date)).map(toRow)
  const rows = q ? all.filter((r) => [r.name, r.phone, r.tableCode ?? ''].some((v) => v.includes(q))) : all
  const total = demo ? 128 : rows.length
  const paid = demo ? 112 : rows.filter((r) => r.pay === 'PAID').length
  const idx = dates.indexOf(date)

  const exportCsv = () => {
    const head = ['이름', '연락처', '출생', '성별', 'MBTI', '희망 회차', '희망 지역', '이용권', '결제', '매칭', '참석', '신고']
    const body = rows.map((r) => [r.name, r.phone, r.birth ?? '', r.gender, r.mbti, r.slots, r.region, r.product, PAY[r.pay]?.[0] ?? r.pay, MATCH[r.match]?.[0] ?? r.match, `${r.attended}회`, r.reports])
    const text = [head, ...body].map((r) => r.map((c) => `"${String(c).replace(/"/g, '""')}"`).join(',')).join('\n')
    const a = document.createElement('a')
    a.href = URL.createObjectURL(new Blob(['﻿' + text], { type: 'text/csv' }))
    a.download = `applicants-${date}.csv`
    a.click()
  }

  return (
    <AdminLayout title="신청자 목록">
      <DemoNotice show={demo} />
      <div className="adm-mt-bar">
        <SessionPicker
          value={date}
          options={dates.map((d) => [d, `${d} 회차`])}
          onChange={setPicked}
          onPrev={idx > 0 ? () => setPicked(dates[idx - 1]) : undefined}
          onNext={idx >= 0 && idx < dates.length - 1 ? () => setPicked(dates[idx + 1]) : undefined}
        />
        <button className="adm-today" onClick={() => setPicked(dates.find((d) => d >= slotDayLabel(`${serviceNow().getMonth() + 1}/${serviceNow().getDate()}`)) ?? dates[dates.length - 1])}>
          오늘
        </button>
        <span style={{ flex: 1 }} />
        {q && (
          <button className="adm-today" onClick={() => navigate('/admin/applicants')}>
            ‘{q}’ 검색 해제
          </button>
        )}
        <span className="t-body-s muted">
          신청 {total}, 결제 {paid}
        </span>
      </div>

      <div className="adm-mt-section">
        <div className="row" style={{ justifyContent: 'flex-end' }}>
          <Button size="S" variant="secondary" onClick={exportCsv}>
            CSV 내보내기
          </Button>
        </div>
        <DataTable
          light
          className="compact"
          rows={rows}
          rowKey={(r) => r.key}
          onRow={(r) => r.tableId && navigate(`/admin/tables/${r.tableId}`)}
          empty={q ? '검색 결과가 없어요' : '이 회차 신청자가 없어요'}
          columns={[
            { key: 'name', label: '이름', width: 90, render: (r) => <span className="strong">{r.name}</span>, sort: (r) => r.name },
            { key: 'birth', label: '출생', width: 56, render: (r) => r.birth ?? '—', sort: (r) => r.birth ?? 0 },
            { key: 'g', label: '성별', width: 44, render: (r) => r.gender, sort: (r) => r.gender },
            { key: 'mbti', label: 'MBTI', width: 56, render: (r) => r.mbti, sort: (r) => r.mbti },
            { key: 'slots', label: '희망 회차', width: 120, render: (r) => r.slots, sort: (r) => r.slots },
            { key: 'region', label: '희망 지역', width: 150, render: (r) => r.region, sort: (r) => r.region },
            {
              key: 'product',
              label: '이용권',
              width: 100,
              render: (r) => (
                <Badge tone="중립" dot={false}>
                  {r.product}
                </Badge>
              ),
              sort: (r) => r.product,
            },
            { key: 'pay', label: '결제', width: 90, render: (r) => tag(PAY, r.pay), sort: (r) => r.pay },
            { key: 'match', label: '매칭', width: 100, render: (r) => tag(MATCH, r.match), sort: (r) => r.match },
            { key: 'att', label: '참석', width: 50, render: (r) => `${r.attended}회`, sort: (r) => r.attended },
            {
              key: 'rep',
              label: '신고',
              render: (r) =>
                r.reports ? (
                  <Badge tone="경고" dot={false}>
                    {r.reports}건
                  </Badge>
                ) : (
                  '—'
                ),
              sort: (r) => r.reports,
            },
          ]}
        />
      </div>
    </AdminLayout>
  )
}
