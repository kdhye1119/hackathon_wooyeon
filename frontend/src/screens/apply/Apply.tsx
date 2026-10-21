import { useEffect, useMemo, useState } from 'react'
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import { AppBar, Badge, Body, Button, Chip, Dialog, Footer, Screen, Sheet, TextField, useToast } from '../../components/ui'
import { Icon } from '../../components/Icon'
import { get, post, put, session } from '../../lib/api'
import { fmt, useApi } from '../../lib/useApi'
import { ALLERGIES, CUISINES, DESIRED_TYPES, PRICE_RANGES, labelOf, loadDraft, toggle } from '../../lib/options'
import type { ApplicationView, OpenSlotsResponse, SubmitResponse, TicketDto, TicketOption } from '../../lib/types'
import { demoApplications, demoSlots, demoTickets } from '../../lib/demo'
import { demoApplyDefaults, initialApply, hasPrevious, loadApply, loadLast, rememberLast, saveApply, type ApplyState } from './applyState'
import chevronDown from '../../assets/icons/chevron-down.svg'
import './apply.css'

function useApplyState() {
  const [s, setS] = useState<ApplyState>(loadApply)
  const set = (p: Partial<ApplyState>) =>
    setS((prev) => {
      const next = { ...prev, ...p }
      saveApply(next)
      return next
    })
  return [s, set] as const
}

function Steps({ step, label }: { step: number; label: string }) {
  return (
    <div className="apply-steps">
      <p className="t-label-s">
        <span className="c-terracotta">{step} / 3</span>
        <span className="c-sage">{label}</span>
      </p>
      <div className="apply-bars">
        {[1, 2, 3].map((i) => (
          <span key={i} className={i <= step ? 'on' : undefined} />
        ))}
      </div>
    </div>
  )
}

export function CheckBox({ checked, round, onChange, children, muted }: { checked: boolean; round?: boolean; onChange?: () => void; children?: React.ReactNode; muted?: boolean }) {
  const inner = (
    <>
      <span className={checked ? 'check-box on' : 'check-box'} style={round ? { borderRadius: 11 } : undefined}>
        {checked && <Icon name="check" size={16} />}
      </span>
      {children && <span className={muted && !checked ? 'c-sage' : undefined}>{children}</span>}
    </>
  )
  // 클릭 가능한 행 안에서는 표시용 span 으로 (button 중첩 방지)
  if (!onChange) return <span className="check-row">{inner}</span>
  return (
    <button type="button" role="checkbox" aria-checked={checked} className="check-row" onClick={onChange}>
      {inner}
    </button>
  )
}

/** 3-2 신청, 매칭 선호 */
export function ApplyPreference() {
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const [s, set] = useApplyState()
  const [loaded, setLoaded] = useState(false)
  useEffect(() => {
    // 재신청이면 지난 신청 내용을 불러온다
    const last = loadLast()
    if (last && hasPrevious()) {
      set({ desiredTypes: last.desiredTypes, priceRanges: last.priceRanges, cuisines: last.cuisines, allergies: last.allergies, avoidFood: last.avoidFood ?? '' })
      setLoaded(true)
    } else if (!session.token || session.token === 'demo') {
      // 시안(3-2)은 재신청 사용자라 지난 신청 내용을 불러온 상태
      if (s.desiredTypes.length === 0) set(demoApplyDefaults)
      setLoaded(true)
    } else {
      get<{ desiredTypes: string[]; priceRanges: string[]; profileComplete: boolean }>('/api/me/profile')
        .then((p) => {
          if (p.profileComplete && p.desiredTypes.length) {
            set({ desiredTypes: p.desiredTypes, priceRanges: p.priceRanges })
            setLoaded(true)
          }
        })
        .catch(() => undefined)
    }
    // 홈에서 날짜를 골라 들어온 경우 미리 선택
    const d = params.get('date')
    if (d) set({ dates: [d] })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const toggleAllergy = (v: string) => {
    if (v === '없음') return set({ allergies: s.allergies.includes('없음') ? [] : ['없음'] })
    set({ allergies: toggle(s.allergies.filter((a) => a !== '없음'), v) })
  }
  const valid = s.desiredTypes.length > 0 && s.priceRanges.length > 0 && s.agreePrivacy && s.agreePolicy

  return (
    <Screen>
      <AppBar title="신청" onBack={() => navigate('/')} />
      <Body className="apply-body" style={{ gap: 24 }}>
        <Steps step={1} label="매칭 선호" />
        {loaded && (
          <div className="loaded-profile">
            <Icon name="refresh" size={18} />
            <p className="t-body-s">지난 신청 내용을 불러왔어요</p>
            <span className="t-label-s muted">바뀐 것만 수정</span>
          </div>
        )}
        <section className="apply-section">
          <div className="stack" style={{ gap: 4 }}>
            <h3 className="t-h3">만나고 싶은 사람</h3>
            <p className="t-label-s muted">복수 선택</p>
          </div>
          <div className="chips">
            {DESIRED_TYPES.map(([code, label]) => (
              <Chip key={code} selected={s.desiredTypes.includes(code)} onClick={() => set({ desiredTypes: toggle(s.desiredTypes, code) })}>
                {label}
              </Chip>
            ))}
          </div>
        </section>
        <section className="apply-section">
          <h3 className="t-h3">식사 조건</h3>
          <div className="chips">
            {PRICE_RANGES.map(([code, label]) => (
              <Chip key={code} selected={s.priceRanges.includes(code)} onClick={() => set({ priceRanges: toggle(s.priceRanges, code) })}>
                {label}
              </Chip>
            ))}
          </div>
          <p className="t-label-s muted">선호 음식 (복수 선택)</p>
          <div className="chips">
            {CUISINES.map((c) => (
              <Chip key={c} selected={s.cuisines.includes(c)} onClick={() => set({ cuisines: toggle(s.cuisines, c) })}>
                {c}
              </Chip>
            ))}
          </div>
          <p className="t-label-s muted">알레르기 (식당에 미리 전달해요)</p>
          <div className="chips">
            {ALLERGIES.map((c) => (
              <Chip key={c} selected={s.allergies.includes(c)} onClick={() => toggleAllergy(c)}>
                {c}
              </Chip>
            ))}
          </div>
          <TextField className="field-form" label="못 먹는 음식 (선택)" placeholder="예: 날것, 고수" value={s.avoidFood} onChange={(e) => set({ avoidFood: e.target.value })} />
        </section>
        <div className="agree-box">
          <CheckBox checked={s.agreePrivacy} onChange={() => set({ agreePrivacy: !s.agreePrivacy })}>
            [필수] 개인정보 동의
          </CheckBox>
          <CheckBox checked={s.agreePolicy} onChange={() => set({ agreePolicy: !s.agreePolicy })}>
            [필수] 환불/노쇼 정책 동의
          </CheckBox>
          <CheckBox checked={s.agreeMarketing} muted onChange={() => set({ agreeMarketing: !s.agreeMarketing })}>
            [선택] 추천 알림
          </CheckBox>
        </div>
      </Body>
      <Footer>
        <Button disabled={!valid} onClick={() => navigate('/apply/schedule')}>
          다음
        </Button>
      </Footer>
    </Screen>
  )
}

const DAY = ['일', '월', '화', '수', '목', '금', '토']

/** 3-3 일정, 지역 (+ 3-3b 기타 지역 알림 시트) */
export function ApplySchedule() {
  const navigate = useNavigate()
  const [s, set] = useApplyState()
  const { data } = useApi(() => get<OpenSlotsResponse>('/api/slots'), demoSlots)
  const [sheet, setSheet] = useState(false)
  const toast = useToast()

  // 처음 들어오면 Figma 기본 선택(홍대/합정, 을지로/종로)
  useEffect(() => {
    if (s.regions.length === 0) set({ regions: ['HONGDAE_HAPJEONG', 'EULJIRO_JONGNO'] })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const now = Date.now()
  const firstDeadline = data.dates.filter((d) => s.dates.includes(d.date)).map((d) => d.applyDeadline).sort()[0]
  const valid = s.regions.length > 0 && s.dates.length > 0

  return (
    <Screen>
      <AppBar title="일정/지역" />
      <Body className="apply-body">
        <Steps step={2} label="일정" />
        <section className="apply-section">
          <h3 className="t-h3">희망 지역</h3>
          <p className="t-label-s muted">서울특별시</p>
          <div className="chips">
            {data.regions.map((r) => (
              <Chip key={r.code} selected={s.regions.includes(r.code)} onClick={() => set({ regions: toggle(s.regions, r.code) })}>
                {r.label}
              </Chip>
            ))}
          </div>
          <button className="other-region" onClick={() => setSheet(true)}>
            <Icon name="map-pin" size={16} />
            <span>{s.otherRegion ? `기타 지역 알림: ${s.otherRegion}` : '기타 지역 알림'}</span>
          </button>
        </section>
        <section className="apply-section" style={{ gap: 10 }}>
          <div className="stack" style={{ gap: 4 }}>
            <h3 className="t-h3">참가 가능한 날짜</h3>
            <p className="t-body-s c-sage">매주 목 19:30</p>
          </div>
          {data.dates.map((d) => {
            const on = s.dates.includes(d.date)
            const urgent = new Date(d.applyDeadline).getTime() - now < 1000 * 60 * 60 * 24 * 3
            return (
              <button key={d.date} className={on ? 'date-option on' : 'date-option'} onClick={() => set({ dates: toggle(s.dates, d.date) })}>
                <CheckBox checked={on} />
                <span className="date-option-main">
                  <span className="t-h3">
                    {fmt.md(d.date)} {DAY[new Date(d.date).getDay()]}
                  </span>
                  <span className="t-body-s c-sage">{fmt.deadline(d.matchAt ?? d.applyDeadline)} 매칭 마감</span>
                </span>
                {urgent ? <Badge tone="경고" dot={false}>마감 임박</Badge> : <Badge tone="진행" dot={false}>모집 중</Badge>}
              </button>
            )
          })}
        </section>
        <div className="summary-box">
          <p className="t-body-m-strong">
            지역 {s.regions.length}, 날짜 {s.dates.length}
          </p>
          {firstDeadline && <p className="t-body-s">{fmt.deadline(firstDeadline)} 이후 환불 불가</p>}
        </div>
      </Body>
      <Footer>
        <Button disabled={!valid} onClick={() => navigate('/apply/pay')}>
          다음
        </Button>
      </Footer>

      <OtherRegionSheet
        open={sheet}
        initial={s.otherRegion}
        onClose={() => setSheet(false)}
        onSubmit={(v) => {
          set({ otherRegion: v })
          setSheet(false)
          toast.show('오픈되면 알림으로 알려드려요')
        }}
      />
      {toast.node}
    </Screen>
  )
}

const PROVINCES = ['서울특별시', '경기도', '인천광역시', '부산광역시', '대전광역시', '대구광역시', '광주광역시']
const POPULAR = ['서울 성수 (11.12 오픈)', '경기 판교', '부산 서면', '대전 둔산']

function OtherRegionSheet({ open, initial, onClose, onSubmit }: { open: boolean; initial: string; onClose: () => void; onSubmit: (v: string) => void }) {
  // 시안(3-3b)만 예시 값·인기 지역을 채우고, 실제 사용자는 빈 입력에서 시작
  const demo = !session.token || session.token === 'demo'
  const [province, setProvince] = useState(demo ? '경기도' : '서울특별시')
  const [area, setArea] = useState(initial || (demo ? '판교' : ''))
  const picked = `${province.slice(0, 2)} ${area}`.trim()
  return (
    <Sheet open={open} onClose={onClose}>
      <h2 className="t-h2">다른 지역 오픈 알림</h2>
      <div className="field" style={{ gap: 6 }}>
        <span className="field-label">희망 지역</span>
        <div className="row" style={{ gap: 8 }}>
          <label className="select-box">
            <select value={province} onChange={(e) => setProvince(e.target.value)} aria-label="시/도">
              {PROVINCES.map((p) => (
                <option key={p}>{p}</option>
              ))}
            </select>
            <img src={chevronDown} width={14} height={14} alt="" />
          </label>
          <span className="field-box" style={{ height: 52, flex: 1 }}>
            <input value={area} onChange={(e) => setArea(e.target.value)} aria-label="동네" placeholder="동네 이름" />
          </span>
        </div>
      </div>
      {demo && (
      <div className="field">
        <span className="field-label">많이 신청한 지역</span>
        <div className="chips">
          {POPULAR.map((p) => {
            const [prov, ...rest] = p.split(' ')
            const name = rest.join(' ').replace(/ \(.*\)/, '')
            const on = picked.endsWith(name) && province.startsWith(prov)
            return (
              <Chip
                key={p}
                selected={on}
                onClick={() => {
                  setProvince(PROVINCES.find((x) => x.startsWith(prov)) ?? province)
                  setArea(name)
                }}
              >
                {p}
              </Chip>
            )
          })}
        </div>
      </div>
      )}
      <p className="t-body-s muted">오픈되면 알림으로 알려드려요</p>
      <div className="row" style={{ gap: 10 }}>
        <Button variant="secondary" onClick={onClose}>
          취소
        </Button>
        <Button disabled={!area.trim()} onClick={() => onSubmit(picked)}>
          알림 신청
        </Button>
      </div>
    </Sheet>
  )
}

const METHODS = [
  ['CARD', '신용/체크카드'],
  ['EASY', '간편결제'],
  ['BANK', '계좌이체'],
] as const

/** 3-4 이용권, 결제 (?app=ID 이면 결제 대기/실패 건 재결제) */
export function ApplyPayment() {
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const retryId = params.get('app')
  const [s, set] = useApplyState()
  const toast = useToast()
  const [busy, setBusy] = useState(false)
  const slots = useApi(() => get<OpenSlotsResponse>('/api/slots'), demoSlots)
  const options = useApi(
    () =>
      get<{ tickets: TicketOption[] }>('/api/meta/options').then((o) =>
        o.tickets.map((t) => {
          // 멤버십(count 0)은 무제한 참여, 3·6개월은 월 환산 금액과 Figma 태그
          const months = Math.round(t.validDays / 30)
          const meta = demoTickets.find((d) => d.code === t.code)
          const sub = t.count === 0 ? (months > 1 ? `월 ${(t.price / months).toLocaleString('ko-KR')}원꼴` : '무제한 참여') : `${t.count}회 참여`
          return { ...t, sub, tag: meta?.tag }
        }),
      ),
    demoTickets,
  )
  const retry = useApi<ApplicationView | null>(
    () => (retryId ? get<ApplicationView>(`/api/applications/${retryId}`) : Promise.resolve(null)),
    retryId ? (demoApplications.find((a) => String(a.id) === retryId) ?? null) : null,
    [retryId],
  )

  // RET-03 보유 이용권이 있으면 결제 없이 바로 신청
  const held = useApi(() => get<TicketDto[]>('/api/me/tickets').then((l) => l.filter((t) => t.usable && (t.remainingCount > 0 || t.totalCount === 0))), [] as TicketDto[])
  const heldTicket = held.data.find((t) => s.product === `HELD:${t.id}`)
  const products = options.data as (TicketOption & { sub: string; tag?: string })[]
  const tickets = [
    ...held.data.map((t) => ({ code: `HELD:${t.id}`, label: `보유 ${t.productLabel.split(' (')[0]}`, price: 0, count: t.remainingCount, validDays: 0, sub: `${t.totalCount === 0 ? '무제한' : `${t.remainingCount}회 남음`}, ${t.expiresAt.slice(0, 10).replace(/-/g, '.')}까지`, tag: '결제 없음' })),
    ...products,
  ]
  const selected = tickets.find((t) => t.code === s.product) ?? tickets[0]
  useEffect(() => {
    // 보유 이용권이 있으면 기본 선택
    if (held.data.length && !s.product.startsWith('HELD:')) set({ product: `HELD:${held.data[0].id}` })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [held.data])
  const regionLabel = (c: string) => slots.data.regions.find((r) => r.code === c)?.label ?? c
  // 일정을 고르지 않고 결제 화면에 바로 들어오면 일정 선택으로 돌려보낸다 (시안 세션 제외)
  useEffect(() => {
    if (!retryId && s.dates.length === 0 && session.token && session.token !== 'demo') navigate('/apply/schedule', { replace: true })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const dates = retry.data ? [...new Set(retry.data.slots.map((x) => x.startAt.slice(0, 10)))] : [...s.dates].sort()
  const regions = retry.data ? [...new Set(retry.data.slots.map((x) => x.region))] : s.regions
  const deadline = retry.data?.refundDeadline ?? slots.data.dates.find((d) => d.date === dates[0])?.applyDeadline
  const summaryDates = dates.map((d) => `${fmt.md(d)} ${fmt.weekday(d)}`).join(', ')
  // 시작 시각: 선택한 회차의 시간 (예: 19:30 → 저녁 7:30)
  const startTime = (retry.data?.slots[0]?.startAt.slice(11, 16) ?? slots.data.slots.find((x) => x.date === dates[0])?.startTime ?? '').slice(0, 5)
  const startLabel = (() => {
    if (!startTime) return ''
    const [h, m] = startTime.split(':').map(Number)
    return `${h >= 17 ? '저녁 ' : h >= 12 ? '오후 ' : '오전 '}${h > 12 ? h - 12 : h}:${String(m).padStart(2, '0')}`
  })()
  const amount = retry.data?.payment?.amount ?? (s.product.startsWith('HELD:') ? 0 : (selected?.price ?? 0))

  const pay = async () => {
    setBusy(true)
    try {
      let appId = retryId ? Number(retryId) : 0
      if (session.token) {
        if (retry.data?.payment) {
          await post(`/api/payments/${retry.data.payment.paymentId}/confirm`, { simulateFailure: false })
        } else {
          const d = loadDraft()
          await put('/api/me/profile', {
            name: d.name || session.user?.name || '참가자',
            birthYear: Number(d.birthYear) || 1999,
            gender: d.gender || 'FEMALE',
            job: d.job || '미입력',
            instagramId: d.instagramId || '미입력',
            mbti: d.mbti || 'ENFP',
            interests: d.interests.length ? d.interests : ['FOOD_CAFE'],
            traits: d.traits.length ? d.traits : ['DEEP_TALK'],
            desiredTypes: s.desiredTypes,
            priceRanges: s.priceRanges,
            foodRestrictions: [s.cuisines.length ? `선호: ${s.cuisines.join(', ')}` : '', s.allergies.filter((a) => a !== '없음').length ? `알레르기: ${s.allergies.join(', ')}` : '']
              .concat(s.avoidFood.trim() ? [`못 먹는 음식: ${s.avoidFood.trim()}`] : [])
              .filter(Boolean)
              .join(' / '),
          })
          await post('/api/me/consents', { privacy: s.agreePrivacy, policy: s.agreePolicy })
          const res = await post<SubmitResponse>('/api/applications', {
            dates: s.dates,
            regions: s.regions,
            otherRegion: s.otherRegion || null,
            product: heldTicket ? null : selected.code,
            ticketId: heldTicket?.id ?? null,
          })
          appId = res.application.id
          if (res.paymentRequired && res.paymentId) await post(`/api/payments/${res.paymentId}/confirm`, { simulateFailure: false })
        }
      }
      rememberLast(s)
      // 다음 신청은 새로 시작 (선호는 rememberLast 로 불러온다)
      saveApply({ ...initialApply })
      navigate(`/apply/done${appId ? `?app=${appId}` : ''}`, {
        replace: true,
        state: { dates, regions: regions.map(regionLabel), ticket: amount ? `${selected?.label ?? ''} ${fmt.won(amount)}` : `${selected?.label ?? ''} 사용`, deadline },
      })
    } catch (e) {
      toast.show(e instanceof Error ? e.message : '결제에 실패했어요')
    } finally {
      setBusy(false)
    }
  }

  return (
    <Screen>
      <AppBar title="이용권/결제" />
      <Body className="apply-body">
        <div className="summary-box" style={{ gap: 8 }}>
          <div className="row t-label-s">
            <span className="c-sage" style={{ flex: 1 }}>
              신청 내용
            </span>
            {!retryId && (
              <button className="c-terracotta" onClick={() => navigate('/apply/schedule')}>
                수정
              </button>
            )}
          </div>
          <p className="t-h3">{summaryDates} {startLabel}</p>
          <p className="t-body-s c-sage">
            {regions.map(regionLabel).join(', ')}, 1인 {s.priceRanges.map((p) => labelOf(PRICE_RANGES, p)).join(', ')} 식당
          </p>
        </div>

        {!retry.data && (
          <section className="apply-section" style={{ gap: 10 }}>
            <h3 className="t-h3">이용권</h3>
            {tickets.map((t) => {
              const on = t.code === selected?.code
              return (
                <button key={t.code} className={on ? 'ticket-option on' : 'ticket-option'} onClick={() => set({ product: t.code })}>
                  <CheckBox checked={on} round />
                  <span className="date-option-main">
                    <span className="row" style={{ gap: 6 }}>
                      <span className="t-h3">{t.label}</span>
                      {t.tag && <Badge dot={false}>{t.tag}</Badge>}
                    </span>
                    <span className="t-body-s c-sage">{t.sub}</span>
                  </span>
                  <span className={on ? 't-h3 c-terracotta' : 't-h3'}>{fmt.won(t.price)}</span>
                </button>
              )
            })}
            <div className="hint-box">
              <Icon name="refresh" size={18} color="var(--terracotta)" />
              <p className="t-body-s c-sage">매칭 실패 시 기한 자동 연장</p>
            </div>
          </section>
        )}

        {amount > 0 && (
        <section className="apply-section" style={{ gap: 10 }}>
          <h3 className="t-h3">결제 수단</h3>
          <div className="chips">
            {METHODS.map(([code, label]) => (
              <Chip key={code} selected={s.method === code} onClick={() => set({ method: code })}>
                {label}
              </Chip>
            ))}
          </div>
        </section>
        )}

        <div className="policy-box">
          <div className="row" style={{ gap: 8 }}>
            <Icon name="alert" size={18} color="var(--paper)" />
            <p className="t-h3">환불/취소 정책</p>
          </div>
          <p className="t-body-s">-&nbsp; {deadline ? fmt.deadline(deadline) : '매칭 마감'} 전, 전액 환불</p>
          <p className="t-body-s">-&nbsp; 이후/환불 불가</p>
          <p className="t-body-s">-&nbsp; 매칭 실패, 이동 / 보관 / 환불</p>
          <p className="t-body-s">-&nbsp; 당일 취소/노쇼, 이용권 차감</p>
        </div>
        <CheckBox checked={s.policyChecked} onChange={() => set({ policyChecked: !s.policyChecked })}>
          정책 확인
        </CheckBox>
      </Body>
      <div className="pay-footer">
        <div className="row">
          <span className="t-body-m c-sage" style={{ flex: 1 }}>
            결제 금액
          </span>
          <span className="t-h2 c-terracotta">{fmt.won(amount)}</span>
        </div>
        <Button disabled={!s.policyChecked || busy || (!retryId && s.dates.length === 0)} onClick={pay}>
          {busy ? '처리 중…' : amount === 0 && !retryId ? '이용권으로 신청하기' : '결제하기'}
        </Button>
      </div>
      {toast.node}
    </Screen>
  )
}

/** 3-5 신청 완료 (?app=ID 이면 신청 상세로도 쓴다) */
export function ApplyDone() {
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const id = params.get('app')
  const app = useApi<ApplicationView | null>(
    () => (id ? get<ApplicationView>(`/api/applications/${id}`) : Promise.resolve(null)),
    id ? (demoApplications.find((a) => String(a.id) === id) ?? null) : null,
    [id],
  )
  const toast = useToast()
  const [confirmCancel, setConfirmCancel] = useState(false)
  const location = useLocation()
  const isDetail = !!app.data && !location.state
  const cancel = async () => {
    setConfirmCancel(false)
    try {
      if (!app.demo) await post(`/api/applications/${app.data!.id}/cancel`)
      toast.show(app.data!.cancelWillRefund ? '신청을 취소했어요. 결제수단으로 자동 환불돼요' : '신청을 취소했어요')
      window.setTimeout(() => navigate('/', { replace: true }), 900)
    } catch (e) {
      toast.show(e instanceof Error ? e.message : '취소하지 못했어요')
    }
  }
  const state = (location.state ?? {}) as { dates?: string[]; regions?: string[]; ticket?: string; deadline?: string }
  const s = loadApply()
  const info = useMemo(() => {
    const a = app.data
    if (a) {
      const dates = [...new Set(a.slots.map((x) => x.startAt.slice(0, 10)))]
      return {
        dates,
        regions: [...new Set(a.slots.map((x) => x.region))].map((r) => demoSlots.regions.find((d) => d.code === r)?.label ?? r),
        ticket: a.payment ? `${a.payment.productLabel} ${fmt.won(a.payment.amount)}` : '보유 이용권',
        deadline: a.refundDeadline ?? undefined,
      }
    }
    return {
      dates: state.dates ?? s.dates,
      regions: state.regions ?? s.regions.map((r) => demoSlots.regions.find((d) => d.code === r)?.label ?? r),
      ticket: state.ticket ?? '1회권 9,900원',
      deadline: state.deadline ?? '2026-10-27T23:59:59',
    }
  }, [app.data, state.dates, state.regions, state.ticket, state.deadline, s.dates, s.regions])

  const dl = info.deadline ? `${fmt.dl(info.deadline).md} ${fmt.dl(info.deadline).time}` : ''
  return (
    <Screen>
      <AppBar onBack={() => navigate('/', { replace: true })} />
      <Body className="done-body">
        <div className="done-check">
          <Icon name={isDetail && app.data!.matchStatus === 'REALLOCATING' ? 'refresh' : 'check'} size={36} />
        </div>
        <h2 className="t-display-m">{isDetail ? app.data!.statusTitle : '신청 완료'}</h2>
        <p className="t-body-s muted" style={{ textAlign: 'center' }}>
          {isDetail ? app.data!.statusMessage : `매칭 결과는 ${info.deadline ? fmt.deadline(info.deadline, true) : '마감 후'}에 알려드려요`}
        </p>
        {isDetail && (app.data!.statusReason || app.data!.reallocationCount > 0 || app.data!.matchAnnounceAt) && (
          <div className="summary-box" style={{ width: '100%' }}>
            {app.data!.matchAnnounceAt && <p className="t-body-s">매칭 발표: {fmt.deadline(app.data!.matchAnnounceAt, true)}</p>}
            {app.data!.reallocationCount > 0 && <p className="t-body-s">자동 재배치 {app.data!.reallocationCount}회</p>}
            {app.data!.statusReason && <p className="t-body-s c-terracotta">사유: {app.data!.statusReason}</p>}
          </div>
        )}
        <dl className="kv-box">
          <div>
            <dt>회차</dt>
            <dd>
              {info.dates.map((d) => fmt.md(d)).join(', ')} ({info.dates[0] ? fmt.weekday(info.dates[0]) : '목'}) 19:30
            </dd>
          </div>
          <div>
            <dt>지역</dt>
            <dd>{info.regions.join(', ')}</dd>
          </div>
          <div>
            <dt>이용권</dt>
            <dd>{info.ticket}</dd>
          </div>
          <div>
            <dt>환불</dt>
            <dd>{dl} 전까지 전액</dd>
          </div>
        </dl>
        <div className="policy-outline">
          <p className="t-label-m c-terracotta">환불/노쇼 정책</p>
          <p className="t-body-s">- {dl} 이후 환불 불가</p>
          <p className="t-body-s">- 당일 취소, 노쇼 시 이용권 차감</p>
          <p className="t-body-s">- 식사 전 단체방 퇴장 불가</p>
        </div>
      </Body>
      <Footer>
        {isDetail && app.data!.nextActions.includes('CANCEL') && (
          <Button variant="ghost" onClick={() => setConfirmCancel(true)}>
            신청 취소
          </Button>
        )}
        <Button onClick={() => navigate('/', { replace: true })}>홈으로</Button>
      </Footer>
      <Dialog open={confirmCancel} onClose={() => setConfirmCancel(false)}>
        <h3 className="t-h2">신청을 취소할까요?</h3>
        <p className="t-body-s muted">
          {app.data?.cancelWillRefund ? `지금 취소하면 ${dl} 전이라 결제수단으로 전액 자동 환불돼요.` : '환불 가능 시점이 지나 환불되지 않아요. 당일 취소는 이용권이 차감돼요.'}
        </p>
        <div className="row" style={{ gap: 10, marginTop: 8 }}>
          <Button variant="secondary" onClick={() => setConfirmCancel(false)}>
            돌아가기
          </Button>
          <Button variant="danger" onClick={cancel}>
            취소하기
          </Button>
        </div>
      </Dialog>
      {toast.node}
    </Screen>
  )
}
