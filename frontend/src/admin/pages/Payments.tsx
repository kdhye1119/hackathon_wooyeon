import { useMemo, useState } from 'react'
import { AdminLayout, BarChart, DataTable, DemoNotice, Seg, useAdminApi, useServiceNow } from '../AdminLayout'
import { Badge, Button } from '../../components/ui'
import { Icon } from '../../components/Icon'
import { get } from '../../lib/api'
import type { ApplicantRow, PaymentRow } from '../types'
import { adminDemo, productPrice, productShort, slotDayLabel } from '../demo'

type Period = 'day' | 'week' | 'month'
const DAY = 86400000
const p2 = (n: number) => String(n).padStart(2, '0')
const md = (d: Date) => `${p2(d.getMonth() + 1)}.${p2(d.getDate())}`
const won = (n: number) => `${n.toLocaleString('ko-KR')}원`

/** 결제 목록 행 */
type PayRow = { key: string; name: string; slots: string; product: string; amount: number; paidAt: string | null; status: string }
/** 이용권 사용 행 */
type UseRow = { key: string; name: string; product: string; used: string; remain: string; extend: string | null }

// Figma 12:585 시안 데이터
const DEMO = {
  anchor: new Date(2026, 9, 20),
  kpi: { paid: 112, paidSum: 1108800, refunding: 3, failed: 2 },
  chart: [8, 12, 9, 15, 22, 18, 14, 9, 6],
  pays: [
    ['김서윤', '10.22, 11.05', '1회권', 9900, '10.19', 'PAID'],
    ['박도현', '10.22', '멤버십 6개월', 120000, '09.02', 'PAID'],
    ['박지민', '10.22', '1회권', 9900, '10.18', 'REFUND_PROCESSING'],
    ['정예린', '10.22', '1회권', 9900, null, 'PENDING'],
    ['오세린', '10.22', '1회권', 9900, '10.20', 'FAILED'],
    ['김OO', '10.22', '1회권', 9900, '10.17', 'REFUNDED'],
  ].map(([name, slots, product, amount, paidAt, status], i) => ({ key: String(i), name, slots, product, amount, paidAt, status }) as PayRow),
  uses: [
    ['박도현', '멤버십 6개월', '10.22', '2027.03.02까지', null],
    ['이도현', '멤버십 3개월', '10.22', '2027.01.21까지', '+7일, 매칭 실패'],
    ['김서윤', '1회권', '10.22', '보관 1장', '—'],
    ['최OO', '멤버십 1개월', '10.15', '11.30까지', '+7일, 매칭 실패'],
  ].map(([name, product, used, remain, extend], i) => ({ key: String(i), name, product, used, remain, extend }) as UseRow),
}

const STATUS: Record<string, [string, '완료' | '확정' | '경고' | '중립']> = {
  PAID: ['결제 완료', '완료'],
  PENDING: ['결제 대기', '확정'],
  FAILED: ['결제 실패', '경고'],
  REFUND_AVAILABLE: ['환불 가능', '확정'],
  REFUND_REQUESTED: ['환불 중', '확정'],
  REFUND_PROCESSING: ['환불 중', '확정'],
  REFUND_FAILED: ['환불 실패', '경고'],
  REFUNDED: ['환불 완료', '중립'],
  PARTIALLY_REFUNDED: ['부분 환불', '중립'],
}

/** 기준일로 끝나는 9개 구간 */
function buckets(period: Period, anchor: Date) {
  const a = new Date(anchor.getFullYear(), anchor.getMonth(), anchor.getDate())
  return Array.from({ length: 9 }, (_, k) => {
    const i = 8 - k
    if (period === 'day') {
      const s = new Date(a.getTime() - i * DAY)
      return { start: s, end: new Date(s.getTime() + DAY), label: md(s) }
    }
    if (period === 'week') {
      const mon = new Date(a.getTime() - ((a.getDay() + 6) % 7) * DAY - i * 7 * DAY)
      return { start: mon, end: new Date(mon.getTime() + 7 * DAY), label: md(mon) }
    }
    const s = new Date(a.getFullYear(), a.getMonth() - i, 1)
    return { start: s, end: new Date(a.getFullYear(), a.getMonth() - i + 1, 1), label: `${s.getMonth() + 1}월` }
  })
}
const shift = (period: Period, d: Date, n: number) =>
  period === 'day' ? new Date(d.getTime() + n * 9 * DAY) : period === 'week' ? new Date(d.getTime() + n * 63 * DAY) : new Date(d.getFullYear(), d.getMonth() + n * 9, d.getDate())

function csv(rows: PayRow[]) {
  const body = [['이름', '회차', '이용권', '금액', '결제일', '상태'], ...rows.map((r) => [r.name, r.slots, r.product, r.amount, r.paidAt ?? '', STATUS[r.status]?.[0] ?? r.status])]
  const text = body.map((r) => r.map((c) => `"${String(c).replace(/"/g, '""')}"`).join(',')).join('\n')
  const a = document.createElement('a')
  a.href = URL.createObjectURL(new Blob(['﻿' + text], { type: 'text/csv' }))
  a.download = 'payments.csv'
  a.click()
}

/** A05 결제 정보 */
export default function Payments() {
  const payments = useAdminApi(() => get<PaymentRow[]>('/api/admin/payments'), adminDemo.payments)
  const apps = useAdminApi(() => get<ApplicantRow[]>('/api/admin/applications'), adminDemo.applicants)
  const demo = payments.demo
  const [period, setPeriod] = useState<Period>('day')
  // 기준일 기본값 = 서비스 시각 (시연 시각 이동 반영)
  const now = useServiceNow()
  const [picked, setAnchor] = useState<Date | null>(null)
  const anchor = picked ?? (demo ? DEMO.anchor : now)
  const bs = buckets(period, demo ? DEMO.anchor : anchor)
  const from = bs[0].start
  const to = bs[8].end
  const rangeLabel = period === 'month' ? `${bs[0].label} ~ ${bs[8].label}` : `${md(from)} ~ ${md(new Date(to.getTime() - DAY))}`

  const inRange = (iso: string | null) => !!iso && new Date(iso) >= from && new Date(iso) < to
  const kpi = useMemo(() => {
    if (demo) return DEMO.kpi
    const list = payments.data.filter((p) => inRange(p.paidAt ?? p.createdAt))
    const paid = list.filter((p) => p.paidAt && p.status !== 'FAILED')
    return {
      paid: paid.length,
      paidSum: paid.reduce((n, p) => n + p.amount, 0),
      refunding: list.filter((p) => p.status.startsWith('REFUND_') && p.status !== 'REFUND_AVAILABLE').length,
      failed: list.filter((p) => p.status === 'FAILED').length,
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [demo, payments.data, from.getTime(), to.getTime()])
  const chart = demo
    ? DEMO.chart
    : bs.map((b) => Math.round(payments.data.filter((p) => p.paidAt && new Date(p.paidAt) >= b.start && new Date(p.paidAt) < b.end).reduce((n, p) => n + p.amount, 0) / 10000))
  const peak = Math.max(...chart)

  // 결제 목록: 신청(이름·회차) 기준. 결제 API 에는 신청자 정보가 없어 신청 목록과 합친다
  const pays: PayRow[] = demo
    ? DEMO.pays
    : apps.data.map((a) => ({
        key: String(a.applicationId),
        name: a.name,
        slots: [...new Set(a.slotLabels.map((l) => slotDayLabel(l).split(' ')[0]))].join(', '),
        product: productShort(a.product),
        amount: productPrice(a.product),
        paidAt: a.paymentStatus === 'PENDING' || a.paymentStatus === 'FAILED' ? null : md(new Date(a.submittedAt)),
        status: a.paymentStatus,
      }))
  const uses: UseRow[] = demo
    ? DEMO.uses
    : apps.data
        .filter((a) => a.paymentStatus === 'PAID' || a.matchStatus === 'NO_MATCH')
        .map((a) => ({
          key: String(a.applicationId),
          name: a.name,
          product: productShort(a.product),
          used: slotDayLabel(a.slotLabels[0] ?? '').split(' ')[0],
          remain: productShort(a.product).startsWith('멤버십') ? '멤버십 이용 중' : a.matchStatus === 'NO_MATCH' ? '보관 1장' : '사용 완료',
          extend: a.matchStatus === 'NO_MATCH' ? '보관, 매칭 실패' : '—',
        }))

  return (
    <AdminLayout title="결제 정보">
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
          <span className="label">{rangeLabel}</span>
          <button aria-label="다음" onClick={() => setAnchor(shift(period, anchor, 1))}>
            <Icon name="chevron-right" size={16} />
          </button>
        </div>
      </div>

      <div className="adm-mt-stats">
        <div className="adm-mt-card">
          <span className="label">결제 완료</span>
          <b>{kpi.paid}건</b>
          <span className="label">{won(kpi.paidSum)}</span>
        </div>
        <div className="adm-mt-card">
          <span className="label">환불 진행</span>
          <b>{kpi.refunding}건</b>
        </div>
        <div className="adm-mt-card warn">
          <span className="label">결제 실패</span>
          <b>{kpi.failed}건</b>
        </div>
      </div>

      <section className="adm-panel">
        <div className="adm-panel-head">
          <h2 className="t-h2">결제 금액 추이</h2>
          <span className="t-body-s muted">단위 만원</span>
        </div>
        <BarChart items={chart.map((v, i) => ({ label: bs[i].label, value: v, hot: v === peak && v > 0 }))} />
      </section>

      <div className="adm-mt-section">
        <div className="row" style={{ justifyContent: 'flex-end' }}>
          <Button size="S" variant="secondary" onClick={() => csv(pays)}>
            CSV 내보내기
          </Button>
        </div>
        <DataTable
          light
          rows={pays}
          rowKey={(r) => r.key}
          empty="결제 내역이 없어요"
          columns={[
            { key: 'name', label: '이름', width: 140, render: (r) => <span className="strong">{r.name}</span>, sort: (r) => r.name },
            { key: 'slots', label: '회차', width: 150, render: (r) => r.slots, sort: (r) => r.slots },
            { key: 'product', label: '이용권', width: 140, render: (r) => r.product, sort: (r) => r.product },
            { key: 'amount', label: '금액', width: 120, render: (r) => won(r.amount), sort: (r) => r.amount },
            { key: 'paidAt', label: '결제일', width: 140, render: (r) => r.paidAt ?? '—', sort: (r) => r.paidAt ?? '' },
            {
              key: 'status',
              label: '상태',
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
          ]}
        />
      </div>

      <div className="adm-mt-section">
        <h2 className="t-h2">이용권 사용 내역</h2>
        <DataTable
          light
          rows={uses}
          rowKey={(r) => r.key}
          empty="이용권 사용 내역이 없어요"
          columns={[
            { key: 'name', label: '이름', width: 110, render: (r) => <span className="strong">{r.name}</span>, sort: (r) => r.name },
            { key: 'product', label: '이용권', width: 130, render: (r) => <span className="t-body-s">{r.product}</span>, sort: (r) => r.product },
            { key: 'used', label: '사용 회차', width: 130, render: (r) => <span className="t-body-s">{r.used}</span>, sort: (r) => r.used },
            { key: 'remain', label: '남은 기간/횟수', width: 160, render: (r) => <span className="t-body-s">{r.remain}</span>, sort: (r) => r.remain },
            {
              key: 'extend',
              label: '연장',
              render: (r) =>
                r.extend === null ? (
                  <span className="t-body-s">—</span>
                ) : (
                  <Badge tone={r.extend === '—' ? '중립' : '확정'} dot={false}>
                    {r.extend}
                  </Badge>
                ),
              sort: (r) => r.extend ?? '',
            },
          ]}
        />
      </div>
    </AdminLayout>
  )
}
