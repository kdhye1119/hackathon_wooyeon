import { useCallback, useEffect, useState } from 'react'
import { adminSession, get, post, session } from '../lib/api'
import type { ApplicationView } from '../lib/types'
import { fmt } from '../lib/useApi'
import './demo-panel.css'

/**
 * 심사용 시연 도구 (데스크탑 전용, 좁은 화면에서는 숨김).
 * 서버 시각을 내 신청 회차의 주요 시점으로 옮기고 자동화를 바로 실행해,
 * 며칠 걸리는 흐름(매칭 → 리마인드 → 체크인 → 후기)을 몇 번의 클릭으로 확인할 수 있게 한다.
 * 백엔드 시연 API(/api/admin/demo/*)는 운영자 권한이라, 필요할 때 기본 운영자 계정으로 로그인한다.
 */

const pad = (n: number) => String(n).padStart(2, '0')
/** 서버 LocalDateTime 형식 (시간대 없이 서비스 시각 그대로) */
const ldt = (d: Date) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:00`
const label = (iso: string) => {
  const d = new Date(iso)
  return `${d.getMonth() + 1}/${d.getDate()} (${'일월화수목금토'[d.getDay()]}) ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

const STATUS: Record<string, string> = {
  PENDING: '결제 대기', PAID: '결제 완료', FAILED: '결제 실패', REFUND_AVAILABLE: '환불 선택 가능', REFUNDED: '환불 완료',
  WAITING: '매칭 대기', MATCHING: '매칭 중', REALLOCATING: '재배치 중', CONFIRMED: '매칭 확정', ALTERNATIVE_OFFERED: '매칭 실패·선택 대기',
  TRANSFERRED: '일정 이동', NO_MATCH: '매칭 종료', EXCEPTION: '예외',
  SCHEDULED: '참석 예정', ATTENDED: '참석', NO_SHOW: '노쇼', CANCELED_EARLY: '사전 취소', CANCELED_LATE: '당일 취소',
}
const st = (c: string | null | undefined) => (c ? (STATUS[c] ?? c) : '')

async function ensureAdmin() {
  if (adminSession.token && adminSession.token !== 'demo') return
  const r = await post<{ token: string; name: string | null }>('/api/auth/admin/login', { loginId: 'admin', password: 'admin1234' })
  adminSession.save(r.token, r.name ?? undefined)
}

const TARGET_KEY = 'wt.demoTarget'
const storedTarget = () => {
  try {
    return Number(localStorage.getItem(TARGET_KEY)) || null
  } catch {
    return null
  }
}

/** 지금 시연 대상인 신청: 시나리오로 만든 신청, 없으면 진행 중인 신청 중 행사가 가장 가까운 것 */
function pickTarget(apps: ApplicationView[]) {
  const done = ['ATTENDED', 'NO_SHOW', 'CANCELED_EARLY', 'CANCELED_LATE']
  const active = apps.filter((a) => a.appStatus !== 'CANCELED' && !done.includes(a.attendanceStatus ?? '') && a.matchStatus !== 'NO_MATCH')
  const picked = active.find((a) => a.id === storedTarget())
  if (picked) return picked
  const startOf = (a: ApplicationView) => (a.slots.find((s) => s.assigned) ?? a.slots[0])?.startAt ?? ''
  return active.sort((a, b) => startOf(a).localeCompare(startOf(b)))[0] ?? null
}

type Scenario = 'SUCCESS' | 'FAIL_FEW' | 'FAIL_AGE' | 'FAIL_MEMBERSHIP' | 'REALLOCATE' | 'PAYMENT_PENDING'
const SCENARIOS: { type: Scenario; title: string; desc: string }[] = [
  { type: 'SUCCESS', title: '매칭 성공', desc: '비슷한 나이대 5명과 6명 테이블 확정 · 단체방 열림' },
  { type: 'FAIL_FEW', title: '매칭 실패 · 인원 부족', desc: '나 포함 3명뿐 → 일정 이동·보관·환불 선택' },
  { type: 'FAIL_AGE', title: '매칭 실패 · 나이 차이', desc: '다른 5명이 모두 15살 이상 차이 → 나만 미배치' },
  { type: 'FAIL_MEMBERSHIP', title: '매칭 실패 · 멤버십', desc: '환불 대신 멤버십 7일 연장 · 유지/이동 선택' },
  { type: 'REALLOCATE', title: '2지망 지역으로 자동 재배치', desc: '1지망 을지로/종로 2명 → 2지망 강남/신논현 테이블로' },
  { type: 'PAYMENT_PENDING', title: '결제 대기', desc: '신청만 하고 결제 전 상태 (시각 이동 없음)' },
]

export default function DemoPanel() {
  const loggedIn = !!session.token && session.token !== 'demo'
  const [now, setNow] = useState<string | null>(null)
  const [target, setTarget] = useState<ApplicationView | null>(null)
  const [busy, setBusy] = useState(false)
  const [log, setLog] = useState<string>('')

  const refresh = useCallback(async () => {
    try {
      await ensureAdmin()
      const c = await get<{ now: string }>('/api/admin/demo/clock')
      setNow(c.now)
      if (loggedIn) setTarget(pickTarget(await get<ApplicationView[]>('/api/applications')))
    } catch {
      setNow(null)
    }
  }, [loggedIn])

  useEffect(() => {
    refresh()
  }, [refresh])

  const slot = target ? (target.slots.find((s) => s.assigned) ?? target.slots[0]) : null

  /** 시각 이동 → 자동화 1회 실행 → 화면 새로고침 */
  const run = async (title: string, at: Date | null, opts: { tick?: boolean; before?: () => Promise<string | void>; go?: () => string | null } = {}) => {
    setBusy(true)
    try {
      await ensureAdmin()
      const pre = opts.before ? await opts.before() : ''
      if (at) await post('/api/admin/demo/clock', { at: ldt(at) })
      const t = opts.tick === false ? null : await post<{ matchRunId: number | null; notificationsSent: number; attendanceSettled: number }>('/api/admin/demo/tick')
      const parts = [title, pre, t?.matchRunId ? '자동 매칭 실행됨' : '', t?.attendanceSettled ? `참석 정산 ${t.attendanceSettled}명` : '', t?.notificationsSent ? `알림 ${t.notificationsSent}건` : ''].filter(Boolean)
      setLog(parts.join(' · '))
      // 화면 데이터가 서버 시각 기준으로 다시 그려지도록
      const go = opts.go?.()
      window.setTimeout(() => (go ? window.location.assign(go) : window.location.reload()), 700)
    } catch (e) {
      setLog(e instanceof Error ? e.message : '실패했어요')
      setBusy(false)
    }
  }

  const start = slot ? new Date(slot.startAt) : null
  const at = (days: number, h: number, m: number) => {
    const d = new Date(start!)
    d.setDate(d.getDate() + days)
    d.setHours(h, m, 0, 0)
    return d
  }
  /** 내 회차 날짜·지역에 가상 참가자 5명을 신청·결제까지 */
  const seedMates = async () => {
    const r = await post<unknown[]>('/api/admin/demo/participants', {
      dates: [slot!.startAt.slice(0, 10)],
      regions: [slot!.region],
      count: 5,
      pay: true,
      minBirthYear: 1993,
      maxBirthYear: 2000,
    })
    return `가상 참가자 ${r.length}명 신청`
  }

  /** 새 회차에 나를 신청시키고 시나리오대로 다른 신청자를 채운 뒤, 매칭 시각으로 이동해 자동 매칭 */
  const runScenario = (sc: (typeof SCENARIOS)[number]) => {
    let result = ''
    return run(sc.title, null, {
      tick: false,
      // 결과 화면으로 바로: 실패 → 4-2, 확정 → 4-1, 결제 대기 → 홈
      go: () => result || null,
      before: async () => {
        const me = session.user
        if (!me) throw new Error('참가자로 로그인해 주세요')
        const r = await post<{ applicationId: number; matchAt: string; others: number; date: string }>('/api/admin/demo/user-scenario', {
          userId: me.id,
          type: sc.type,
        })
        try {
          localStorage.setItem(TARGET_KEY, String(r.applicationId))
        } catch {
          /* 저장 못 해도 가장 가까운 신청을 대상으로 잡는다 */
        }
        if (sc.type === 'PAYMENT_PENDING') {
          result = '/'
          return `${label(r.date + 'T19:30:00')} 회차 신청 · 결제 대기`
        }
        await post('/api/admin/demo/clock', { at: ldt(new Date(new Date(r.matchAt).getTime() + 60_000)) })
        const t = await post<{ matchRunId: number | null }>('/api/admin/demo/tick')
        const app = (await get<ApplicationView[]>('/api/applications')).find((x) => x.id === r.applicationId)
        if (app?.matchStatus === 'CONFIRMED') result = `/match/${app.id}`
        else if (app?.matchStatus === 'ALTERNATIVE_OFFERED' || app?.matchStatus === 'NO_MATCH') result = `/match/${app.id}/failed`
        return `다른 신청자 ${r.others}명 · ${t.matchRunId ? '자동 매칭 완료' : '매칭 대기'} · ${st(app?.matchStatus)}`
      },
    })
  }

  const steps: { key: string; title: string; desc: string; onClick: () => void; disabled?: boolean }[] = slot && start
    ? [
        {
          key: 'mates',
          title: '같은 회차에 가상 참가자 5명 채우기',
          desc: '혼자서는 테이블이 안 만들어지니 같은 날짜·지역 신청자를 넣어요',
          onClick: () => run('참가자 채우기', null, { tick: false, before: seedMates }),
          disabled: target?.matchStatus !== 'WAITING',
        },
        {
          key: 'match',
          title: '신청 마감 → 자동 매칭',
          desc: `${slot.matchAt ? fmt.deadline(slot.matchAt) : '마감 시각'} 직후로 옮기고 매칭을 돌려요`,
          onClick: () => run('자동 매칭', new Date(new Date(slot.matchAt).getTime() + 60_000)),
        },
        { key: 'remind', title: '행사 1일 전 리마인드', desc: `${label(ldt(at(-1, 19, 31)))} · 단체방 안내 게시`, onClick: () => run('리마인드', at(-1, 19, 31)) },
        { key: 'checkin', title: '행사 당일 체크인 시간', desc: `${label(ldt(at(0, 19, 10)))} · 홈에서 체크인할 수 있어요`, onClick: () => run('체크인 시간', at(0, 19, 10)) },
        { key: 'content', title: '식사 시작 · 대화 주제 게시', desc: `${label(ldt(at(0, 19, 31)))} · 단체방에 대화 주제`, onClick: () => run('대화 주제', at(0, 19, 31)) },
        { key: 'after', title: '행사 종료 → 참석 정산·후기 요청', desc: `${label(ldt(at(0, 22, 30)))} · 미체크인은 노쇼 후보`, onClick: () => run('행사 종료', at(0, 22, 30)) },
      ]
    : []

  return (
    <aside className="demo-panel" aria-label="시연 도구">
      <p className="dp-cap">심사용 · 데스크탑 전용</p>
      <h2>시연 도구</h2>
      <div className="dp-now">
        <span>서버 시각</span>
        <b>{now ? label(now) : '서버 연결 안 됨'}</b>
      </div>

      {!loggedIn && <p className="dp-note">참가자로 로그인하면 내 신청 회차 기준으로 시점을 옮길 수 있어요. (테스트 계정: 01000000001 / test1234)</p>}
      {loggedIn && !slot && <p className="dp-note">진행 중인 신청이 없어요. 먼저 신청·결제를 해 주세요.</p>}
      {slot && (
        <div className="dp-target">
          <span>대상 신청</span>
          <b>{slot.label}</b>
          <small>
            {[st(target!.paymentStatus), st(target!.matchStatus), st(target!.attendanceStatus)].filter(Boolean).join(' · ')}
          </small>
        </div>
      )}

      {loggedIn && (
        <>
          <h3>시나리오 만들기</h3>
          <p className="dp-note">새 회차에 나를 신청시키고 결과가 나오는 시각까지 바로 이동해요.</p>
          <div className="dp-scen">
            {SCENARIOS.map((sc) => (
              <button key={sc.type} disabled={busy} onClick={() => runScenario(sc)}>
                <b>{sc.title}</b>
                <small>{sc.desc}</small>
              </button>
            ))}
          </div>
          <h3>시간 건너뛰기</h3>
        </>
      )}

      <ol className="dp-steps">
        {steps.map((s) => (
          <li key={s.key}>
            <button disabled={busy || s.disabled} onClick={s.onClick}>
              <b>{s.title}</b>
              <small>{s.desc}</small>
            </button>
          </li>
        ))}
      </ol>

      <div className="dp-row">
        <button disabled={busy} onClick={() => run('자동화 1회 실행', null)}>
          자동화 지금 실행
        </button>
        <button
          disabled={busy}
          onClick={() =>
            run('시각 초기화', null, {
              tick: false,
              before: async () => {
                await post('/api/admin/demo/clock/reset')
              },
            })
          }
        >
          실제 시각으로
        </button>
      </div>
      {log && <p className="dp-log">{log}</p>}
      <a className="dp-link" href="/admin" target="_blank" rel="noreferrer">
        운영자 화면 열기 (매칭 로그·재배치 내역)
      </a>
      <p className="dp-note">시각을 되돌려도 이미 진행된 매칭·정산은 그대로 남아요. 회차 순서대로 앞으로만 옮기는 게 좋아요.</p>
    </aside>
  )
}
