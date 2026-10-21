import { useMemo, useState } from 'react'
import { AdminLayout, DataTable, DemoNotice, Field, Seg, serviceNow, useAdminApi } from '../AdminLayout'
import { Badge, Button, useToast } from '../../components/ui'
import { get, post } from '../../lib/api'
import { REGIONS } from '../../lib/options'
import type { AdminSlot, Policy } from '../types'
import { adminDemo } from '../demo'
import { fmt } from '../../lib/useApi'

const DAY = ['일', '월', '화', '수', '목', '금', '토']
const WEEK = ['월', '화', '수', '목', '금', '토', '일']
const pad = (n: number) => String(n).padStart(2, '0')
const md = (d: Date) => `${pad(d.getMonth() + 1)}.${pad(d.getDate())}`
const ymd = (d: Date) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
const mdw = (iso: string) => {
  const d = new Date(iso)
  return `${md(d)} (${DAY[d.getDay()]})`
}

type Repeat = 'none' | 'weekly' | 'biweekly'

/** 반복 규칙으로 만들어질 날짜 목록 */
function planDates(start: string, end: string, repeat: Repeat, days: string[]) {
  const s = new Date(`${start}T00:00:00`)
  if (Number.isNaN(s.getTime())) return []
  if (repeat === 'none') return [s]
  const e = new Date(`${end}T00:00:00`)
  const out: Date[] = []
  const step = repeat === 'weekly' ? 7 : 14
  for (const dname of days) {
    const target = DAY.indexOf(dname)
    const first = new Date(s)
    first.setDate(s.getDate() + ((target - s.getDay() + 7) % 7))
    for (let d = new Date(first); d <= e; d = new Date(d.getTime() + step * 86400000)) out.push(d)
  }
  return out.sort((a, b) => a.getTime() - b.getTime())
}

const STATUS: Record<string, string> = { RECRUITING: '모집 중', MATCHING: '매칭 중', MATCHED: '매칭 완료', COMPLETED: '종료', CANCELED: '운영 취소' }

/** A02 회차 관리 */
export default function Slots() {
  const toast = useToast()
  const slots = useAdminApi(() => get<AdminSlot[]>('/api/admin/slots'), adminDemo.slots)
  const policy = useAdminApi(() => get<Policy>('/api/admin/policy'), adminDemo.policy)

  // 새 회차 만들기 카드는 '+ 새 회차'를 눌렀을 때만 연다
  const [showForm, setShowForm] = useState(false)
  // 기본 시작일 = 가장 늦은 기존 회차 다음 주 (회차가 없으면 다음 목요일)
  const defaultStart = useMemo(() => {
    const last = slots.data.map((x) => x.date).sort().pop()
    const base = last ? new Date(last) : serviceNow()
    const d = new Date(base)
    d.setDate(d.getDate() + (last ? 7 : ((4 - d.getDay() + 7) % 7 || 7)))
    return ymd(d)
  }, [slots.data])
  const [startPicked, setStart] = useState<string | null>(null)
  const start = startPicked ?? defaultStart
  const [time, setTime] = useState('19:30')
  const [repeat, setRepeat] = useState<Repeat>('weekly')
  const [days, setDays] = useState<string[]>(['목'])
  const [endPicked, setEnd] = useState<string | null>(null)
  // 반복 종료 기본값 = 시작일 4주 뒤
  const end = endPicked ?? (start ? ymd(new Date(new Date(start).getTime() + 27 * 86400000)) : '')
  const [regions, setRegions] = useState<string[]>(['HONGDAE_HAPJEONG', 'EULJIRO_JONGNO'])
  const [tables, setTables] = useState(6)
  const [busy, setBusy] = useState(false)

  const plan = useMemo(() => planDates(start, end, repeat, days), [start, end, repeat, days])

  // 날짜별로 묶어서 한 줄 (지역은 이어서 표시)
  const rows = useMemo(() => {
    const m = new Map<string, AdminSlot[]>()
    slots.data.forEach((s) => m.set(s.date, [...(m.get(s.date) ?? []), s]))
    return [...m.entries()]
      .map(([date, list]) => ({ date, list }))
      .sort((a, b) => {
        // 모집 중인 다가오는 회차 먼저, 지난 회차는 뒤로
        const pa = a.list[0].status === 'RECRUITING' ? 0 : 1
        const pb = b.list[0].status === 'RECRUITING' ? 0 : 1
        return pa - pb || a.date.localeCompare(b.date)
      })
  }, [slots.data])

  const create = async () => {
    if (!plan.length || !regions.length) return
    setBusy(true)
    try {
      if (!slots.demo)
        await post('/api/admin/slots/bulk', { dates: plan.map(ymd), regions, startTime: time, capacity: tables * policy.data.maxTableSize })
      toast.show(`회차 ${plan.length * regions.length}개를 만들었어요`)
      slots.reload()
      setShowForm(false)
    } catch (e) {
      toast.show(e instanceof Error ? e.message : '회차를 만들지 못했어요')
    } finally {
      setBusy(false)
    }
  }

  return (
    <AdminLayout title="회차 관리">
      <DemoNotice show={slots.demo} />
      <div className="row" style={{ gap: 20, alignItems: 'flex-start' }}>
        <div className="stack" style={{ flex: 1, minWidth: 0, gap: 16 }}>
          <div className="adm-section-head">
            <h2 className="t-h2">회차 목록</h2>
            <Button size="S" variant={showForm ? 'secondary' : 'primary'} onClick={() => setShowForm(!showForm)}>
              {showForm ? '닫기' : '+ 새 회차'}
            </Button>
          </div>
          <DataTable
            rows={rows}
            rowKey={(r) => r.date}
            columns={[
              { key: 'date', label: '날짜', width: 64, render: (r) => <span className="strong">{mdw(r.date)}</span>, sort: (r) => r.date },
              { key: 'time', label: '시간', width: 64, render: (r) => r.list[0].startTime.slice(0, 5), sort: (r) => r.list[0].startTime },
              { key: 'region', label: '지역', width: 99, render: (r) => <span className="ellipsis" style={{ display: 'block', maxWidth: 99 }}>{r.list.map((s) => s.regionLabel).join(', ')}</span> },
              {
                key: 'cap',
                label: '정원',
                width: 99,
                render: (r) => (
                  <span className="ellipsis" style={{ display: 'block', maxWidth: 99 }}>
                    {r.list[0].status === 'MATCHED' ? '테이블 확정' : `지역별 최대 ${Math.floor(r.list[0].capacity / policy.data.maxTableSize)}테이블 (${r.list[0].capacity}명)`}
                  </span>
                ),
              },
              { key: 'deadline', label: '신청 마감', width: 120, render: (r) => <span className="nowrap">{fmt.deadline(r.list[0].applyDeadline, true)}</span>, sort: (r) => r.list[0].applyDeadline },
              {
                key: 'run',
                label: '자동 매칭 실행',
                width: 130,
                render: (r) => <span className="nowrap">{r.list[0].status === 'RECRUITING' ? fmt.deadline(r.list[0].matchAt, true) : `실행 완료 ${new Date(r.list[0].matchAt).toTimeString().slice(0, 8)}`}</span>,
              },
              { key: 'status', label: '상태', render: (r) => <Badge dot={false}>{STATUS[r.list[0].status] ?? r.list[0].status}</Badge>, sort: (r) => r.list[0].status },
            ]}
          />
        </div>

        {showForm && (
        <section className="adm-form-panel">
          <h2 className="t-h2">새 회차 만들기</h2>
          <div className="adm-form-row" style={{ gap: 12 }}>
            <Field label="시작일">
              <input id="slot-start" type="date" value={start} onChange={(e) => setStart(e.target.value)} />
            </Field>
            <Field label="시간">
              <input type="time" value={time} onChange={(e) => setTime(e.target.value)} />
            </Field>
          </div>
          <div className="adm-field">
            <span>반복</span>
            <Seg
              className="form"
              value={repeat}
              onChange={setRepeat}
              options={[
                ['none', '안 함'],
                ['weekly', '매주'],
                ['biweekly', '격주'],
              ]}
            />
          </div>
          {repeat !== 'none' && (
            <>
              <div className="adm-field">
                <span>요일 (복수 선택)</span>
                <div className="adm-days">
                  {WEEK.map((w) => (
                    <button key={w} aria-pressed={days.includes(w)} onClick={() => setDays(days.includes(w) ? days.filter((x) => x !== w) : [...days, w])}>
                      <span>{w}</span>
                    </button>
                  ))}
                </div>
              </div>
              <Field label="반복 종료">
                <input type="date" value={end} onChange={(e) => setEnd(e.target.value)} />
              </Field>
            </>
          )}
          <div className="adm-preview">
            <b className="t-label-m">회차 {plan.length}개 생성</b>
            <span className="t-body-s muted">
              {plan.length ? `${plan.map(md).join(', ')} (${days.join('/') || DAY[plan[0].getDay()]}) ${time}` : '날짜를 골라주세요'}
            </span>
          </div>
          <div className="adm-field" style={{ gap: 8 }}>
            <span>모집 지역</span>
            {/* 요일 버튼처럼 선택하면 색이 바뀐다 */}
            <div className="adm-days wide">
              {REGIONS.map(([code, label]) => {
                const on = regions.includes(code)
                return (
                  <button key={code} aria-pressed={on} onClick={() => setRegions(on ? regions.filter((r) => r !== code) : [...regions, code])}>
                    <span>{label}</span>
                  </button>
                )
              })}
            </div>
          </div>
          <div className="adm-form-row" style={{ gap: 12 }}>
            <Field label="지역별 최대 테이블">
              <input type="number" min={1} value={tables} onChange={(e) => setTables(Number(e.target.value) || 1)} />
            </Field>
            <Field label="테이블 인원">
              <input readOnly value={`${policy.data.minTableSize} ~ ${policy.data.maxTableSize}명`} />
            </Field>
          </div>
          <Field label="신청 마감 = 자동 매칭 실행">
            <input readOnly value="행사 2일 전 24:00 (회차마다 자동)" />
          </Field>
          <Field label="최소 매칭 점수 (이 회차만)">
            <input readOnly value={`${policy.data.minScore}점, 서비스 기본값 사용`} />
          </Field>
          <p className="adm-hint">실행 시각에 자동 실행</p>
          <Button disabled={busy || !plan.length || !regions.length} onClick={create}>
            회차 {plan.length}개 만들기
          </Button>
        </section>
        )}
      </div>
      {toast.node}
    </AdminLayout>
  )
}
