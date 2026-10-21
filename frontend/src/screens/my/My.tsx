import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { AppBar, AppHeader, Badge, Body, Button, Dialog, Screen, Sheet, TabBar, TextField, useToast } from '../../components/ui'
import { Icon } from '../../components/Icon'
import { get, post, session } from '../../lib/api'
import { fmt, useApi } from '../../lib/useApi'
import { INTERESTS, REGIONS, labelOf, loadDraft } from '../../lib/options'
import type { ApplicationView, PaymentDto, TicketDto } from '../../lib/types'
import { demoApplications } from '../../lib/demo'
import './my.css'

type Profile = { name: string | null; mbti: string | null; interests: string[]; phone?: string }
const DAY = ['일', '월', '화', '수', '목', '금', '토']
const md = (iso: string) => `${fmt.md(iso)} (${DAY[new Date(iso).getDay()]})`

const demoProfile: Profile = { name: '김서윤', mbti: 'ENFP', interests: ['EXHIBITION_ART', 'TRAVEL', 'FOOD_CAFE'] }
const demoTickets: TicketDto[] = [{ id: 1, product: 'SINGLE', productLabel: '1회권', totalCount: 1, remainingCount: 1, expiresAt: '2027-04-15T23:59:59', usable: true }]

const shortInterest = (code: string) => labelOf(INTERESTS, code).split(/[/ ]/)[0]

type Tone = '확정' | '진행' | '경고' | '중립' | '완료'
/** 참가 이력 한 줄: 참석만이 아니라 신청·결제·매칭·취소·환불까지 모든 결과 */
function historyStatus(a: ApplicationView): { label: string; tone: Tone; note?: string } {
  const refund = a.paymentStatus === 'REFUNDED' ? '환불 완료' : a.paymentStatus.startsWith('REFUND_') && a.paymentStatus !== 'REFUND_AVAILABLE' ? '환불 진행 중' : ''
  if (a.appStatus === 'CANCELED') return { label: '신청 취소', tone: '중립', note: refund || (a.paymentStatus === 'PENDING' ? '결제 전 취소' : '환불 없음') }
  if (a.attendanceStatus === 'ATTENDED') return { label: '참석', tone: '완료', note: a.feedbackSubmitted ? '후기 완료' : '후기 미작성' }
  if (a.attendanceStatus === 'NO_SHOW') return { label: '불참', tone: '경고' }
  if (a.attendanceStatus === 'CANCELED_EARLY' || a.attendanceStatus === 'CANCELED_LATE') return { label: a.attendanceStatus === 'CANCELED_LATE' ? '당일 취소' : '참석 취소', tone: '중립', note: refund || undefined }
  if (a.matchStatus === 'TRANSFERRED') return { label: '일정 이동', tone: '진행', note: '매칭 실패 → 다른 일정' }
  if (a.paymentStatus === 'REFUNDED') return { label: '환불 완료', tone: '중립' }
  if (a.matchStatus === 'NO_MATCH') return { label: '매칭 종료', tone: '중립', note: a.resolution?.choice === 'KEEP_TICKET' ? '이용권 보관' : refund || undefined }
  if (a.matchStatus === 'ALTERNATIVE_OFFERED') return { label: '매칭 실패', tone: '경고', note: '선택 대기' }
  if (a.matchStatus === 'EXCEPTION') return { label: '운영자 확인 중', tone: '경고' }
  if (a.paymentStatus === 'PENDING' || a.paymentStatus === 'FAILED') return { label: a.paymentStatus === 'FAILED' ? '결제 실패' : '결제 대기', tone: '경고' }
  if (a.matchStatus === 'CONFIRMED') return { label: '참석 예정', tone: '진행' }
  if (a.matchStatus === 'REALLOCATING') return { label: '재배치 중', tone: '진행' }
  return { label: '매칭 대기', tone: '진행' }
}

const startOf = (a: ApplicationView) => (a.slots.find((s) => s.assigned) ?? a.slots[0])?.startAt ?? a.submittedAt
/** 행사 날짜 최신순 */
const byLatest = (list: ApplicationView[]) => [...list].sort((x, y) => startOf(y).localeCompare(startOf(x)) || y.id - x.id)

function HistoryRow({ a }: { a: ApplicationView }) {
  const navigate = useNavigate()
  const s = a.slots.find((x) => x.assigned) ?? a.slots[0]
  const st = historyStatus(a)
  const regions = [...new Set(a.slots.map((x) => labelOf(REGIONS, x.region)))]
  const where = a.table ? labelOf(REGIONS, s?.region ?? '') : regions.join(', ')
  return (
    <button className="history-row" onClick={() => navigate(a.attendanceStatus === 'ATTENDED' ? `/review/${a.id}` : `/applications/${a.id}`)}>
      <span className="stack" style={{ flex: 1, gap: 2, minWidth: 0 }}>
        <span className="t-label-m">{md(startOf(a))}</span>
        <span className="t-caption c-sage">
          {[where, a.table?.venueName, a.table ? `${a.table.memberCount}명` : null].filter(Boolean).join(', ')}
        </span>
      </span>
      <span className="stack history-status">
        <Badge tone={st.tone} dot={false}>
          {st.label}
        </Badge>
        {st.note && <span className="t-caption c-sage">{st.note}</span>}
      </span>
    </button>
  )
}

/** 6-1 마이 */
export function MyPage() {
  const navigate = useNavigate()
  const profile = useApi(() => get<Profile>('/api/me/profile'), demoProfile)
  const apps = useApi(() => get<ApplicationView[]>('/api/applications'), demoApplications)
  const history = byLatest(apps.data)
  const tickets = useApi(() => get<TicketDto[]>('/api/me/tickets'), demoTickets)
  const draft = loadDraft()
  const p = profile.demo && draft.name ? { name: draft.name, mbti: draft.mbti, interests: draft.interests } : profile.data
  const kept = tickets.data.filter((t) => t.usable && t.totalCount > 0).reduce((n, t) => n + t.remainingCount, 0)
  const member = tickets.data.find((t) => t.usable && t.totalCount === 0)
  const name = p.name || session.user?.name || '참가자'
  const toast = useToast()
  const [withdraw, setWithdraw] = useState(false)
  const [busy, setBusy] = useState(false)
  const policy = useApi(() => get<{ retentionPolicy: string }>('/api/me/withdraw'), {
    retentionPolicy: '탈퇴하면 이름, 연락처, 인스타그램, 직업, 식사 조건은 바로 삭제돼요. 결제/환불 기록은 5년, 신고/차단 기록은 1년간 개인을 알아볼 수 없게 보관한 뒤 삭제해요.',
  })
  const [phoneSheet, setPhoneSheet] = useState(false)
  const [newPhone, setNewPhone] = useState('')
  const [code, setCode] = useState('')
  const [codeSent, setCodeSent] = useState(false)
  const sendCode = async () => {
    try {
      const r = await post<{ devCode: string | null }>('/api/auth/otp', { phone: newPhone, purpose: 'CHANGE_PHONE' })
      setCodeSent(true)
      toast.show(r.devCode ? `인증번호 ${r.devCode} (시연용, 문자 대신 표시)` : '인증번호를 보냈어요')
    } catch (e) {
      toast.show(e instanceof Error ? e.message : '인증번호를 보내지 못했어요')
    }
  }
  const changePhone = async () => {
    setBusy(true)
    try {
      const p = await post<Profile>('/api/me/phone', { phone: newPhone, code })
      profile.setData({ ...profile.data, ...p })
      setPhoneSheet(false)
      setNewPhone('')
      setCode('')
      setCodeSent(false)
      toast.show('연락처를 바꿨어요. 다음 로그인부터 새 번호를 써 주세요')
    } catch (e) {
      toast.show(e instanceof Error ? e.message : '바꾸지 못했어요')
    } finally {
      setBusy(false)
    }
  }
  const doWithdraw = async () => {
    if (profile.demo) return setWithdraw(false)
    setBusy(true)
    try {
      await post('/api/me/withdraw')
      session.clear()
      navigate('/start', { replace: true })
    } catch (e) {
      setWithdraw(false)
      toast.show(e instanceof Error ? e.message : '탈퇴하지 못했어요')
    } finally {
      setBusy(false)
    }
  }

  return (
    <Screen>
      <AppHeader />
      <Body className="my-body">
        <div className="row" style={{ gap: 14 }}>
          {/* 세 글자 한글 이름은 이름 첫 글자(서윤 → 서), 그 외는 첫 글자 */}
          <span className="my-avatar">{/^[가-힣]{3}$/.test(name) ? name[1] : name.slice(0, 1)}</span>
          <div className="stack" style={{ flex: 1, minWidth: 0, gap: 4 }}>
            <h2 className="t-h1">{name}</h2>
            <p className="t-body-s c-sage">{[p.mbti, ...p.interests.slice(0, 3).map(shortInterest)].filter(Boolean).join(', ')}</p>
          </div>
          <Button size="S" variant="secondary" onClick={() => navigate('/onboarding')}>
            프로필 수정
          </Button>
        </div>

        {!profile.demo && profile.data.phone && (
          <div className="ticket-link">
            <Icon name="phone" color="var(--terracotta)" />
            <span className="t-h3">{profile.data.phone.replace(/^(\d{3})(\d{3,4})(\d{4})$/, '$1-$2-$3')}</span>
            <Button size="S" variant="secondary" onClick={() => setPhoneSheet(true)}>
              연락처 변경
            </Button>
          </div>
        )}

        <button className="ticket-link" onClick={() => navigate('/my/tickets')}>
          <Icon name="ticket" color="var(--terracotta)" />
          <span className="t-h3">{member ? `${member.productLabel}, ${member.expiresAt.slice(0, 10).replace(/-/g, '.')}까지` : `보관 이용권 ${kept}장`}</span>
          <Icon name="chevron-right" size={20} />
        </button>

        <section>
          <div className="row" style={{ justifyContent: 'space-between' }}>
            <h3 className="t-h3">참가 이력</h3>
            {history.length > 5 && (
              <button className="t-caption c-sage underline" onClick={() => navigate('/my/history')}>
                자세히 보기
              </button>
            )}
          </div>
          {history.length === 0 && <p className="t-body-s muted" style={{ padding: '14px 0' }}>아직 신청한 식탁이 없어요</p>}
          {history.slice(0, 5).map((a) => (
            <HistoryRow key={a.id} a={a} />
          ))}
        </section>

        <div className="row" style={{ gap: 16, justifyContent: 'center' }}>
          <button
            className="logout t-body-s muted underline"
            onClick={() => {
              session.clear()
              navigate('/start', { replace: true })
            }}
          >
            로그아웃
          </button>
          <button className="logout t-body-s muted underline" onClick={() => setWithdraw(true)}>
            회원 탈퇴
          </button>
        </div>
      </Body>
      <TabBar />
      <Dialog open={withdraw} onClose={() => setWithdraw(false)}>
        <Icon name="alert" size={28} />
        <h3 className="dialog-title">탈퇴할까요?</h3>
        <p className="t-body-s muted" style={{ textAlign: 'center' }}>
          진행 중인 신청은 환불 규정에 따라 취소돼요. 확정된 모임이 있으면 모임이 끝난 뒤 탈퇴할 수 있어요.
        </p>
        <p className="t-caption muted" style={{ textAlign: 'center' }}>
          {policy.data?.retentionPolicy}
        </p>
        <div className="row" style={{ gap: 10, paddingTop: 10, width: '100%' }}>
          <Button variant="secondary" onClick={() => setWithdraw(false)}>
            취소
          </Button>
          <Button variant="danger" disabled={busy} onClick={doWithdraw}>
            탈퇴하기
          </Button>
        </div>
      </Dialog>
      {/* 연락처 변경: 새 번호로 인증번호를 받아 확인 */}
      <Sheet open={phoneSheet} onClose={() => setPhoneSheet(false)}>
        <div className="stack" style={{ gap: 12, padding: '4px 20px 24px' }}>
          <h3 className="t-h2">연락처 변경</h3>
          <TextField label="새 휴대폰 번호" inputMode="numeric" placeholder="01012345678" value={newPhone} onChange={(e) => setNewPhone(e.target.value.replace(/[^0-9]/g, ''))} />
          <Button variant="secondary" disabled={!/^01[0-9]{8,9}$/.test(newPhone) || busy} onClick={sendCode}>
            {codeSent ? '인증번호 다시 받기' : '인증번호 받기'}
          </Button>
          {codeSent && <TextField label="인증번호 6자리" inputMode="numeric" value={code} onChange={(e) => setCode(e.target.value.replace(/[^0-9]/g, '').slice(0, 6))} />}
          <Button disabled={code.length !== 6 || busy} onClick={changePhone}>
            변경하기
          </Button>
        </div>
      </Sheet>
      {toast.node}
    </Screen>
  )
}

const demoPayments: PaymentDto[] = [
  { id: 3, product: 'SINGLE', productLabel: '1회권', amount: 9900, refundedAmount: 0, status: 'PAID', method: 'CARD', pgTxId: '10.29', failureReason: null, createdAt: '2026-10-19T14:02:00', paidAt: '2026-10-19T14:02:00' },
  { id: 2, product: 'SINGLE', productLabel: '1회권', amount: 9900, refundedAmount: 0, status: 'PAID', method: 'CARD', pgTxId: '10.15', failureReason: null, createdAt: '2026-10-13T10:00:00', paidAt: '2026-10-13T10:00:00' },
  { id: 1, product: 'SINGLE', productLabel: '이용권 보관', amount: 9900, refundedAmount: 0, status: 'KEPT', method: 'CARD', pgTxId: '09.24', failureReason: null, createdAt: '2026-09-25T10:00:00', paidAt: null },
]
const usageLabel: Record<string, [string, string]> = {
  PAID: ['신청', '사용'],
  KEPT: ['매칭 실패', '이용권 보관'],
  REFUNDED: ['환불', '환불 완료'],
  FAILED: ['결제 실패', '미결제'],
}

/** 6-2 이용권 관리 */
export function TicketPage() {
  const navigate = useNavigate()
  const tickets = useApi(() => get<TicketDto[]>('/api/me/tickets'), demoTickets)
  const payments = useApi(() => get<PaymentDto[]>('/api/me/payments'), demoPayments)
  // 횟수권(보관 이용권)과 멤버십(무제한, totalCount 0)을 나눠 보여준다
  const usable = tickets.data.filter((t) => t.usable && t.totalCount > 0)
  const kept = usable.reduce((n, t) => n + t.remainingCount, 0)
  const member = tickets.data.find((t) => t.usable && t.totalCount === 0)
  const until = usable.map((t) => t.expiresAt).sort()[0]

  return (
    <Screen>
      <AppBar title="이용권" />
      <Body className="ticket-body">
        <div className="kept-card">
          <div className="row" style={{ gap: 8 }}>
            <Icon name="ticket" size={22} />
            <span className="t-h3" style={{ flex: 1 }}>
              보관 이용권
            </span>
            <b className="t-h2">{kept}장</b>
          </div>
          <p className="t-body-s muted">{until ? `${until.slice(0, 10).replace(/-/g, '.')}까지 사용 가능, 다음 신청 때 자동 적용` : '보관 중인 이용권이 없어요'}</p>
        </div>

        <div className="membership-card">
          <h3 className="t-h3">멤버십</h3>
          {member ? (
            <p className="t-body-s">
              {member.productLabel} 이용 중, {member.expiresAt.slice(0, 10).replace(/-/g, '.')}까지
            </p>
          ) : null}
          <p className="t-body-s muted">기간 내 횟수 제한 없이 참여하고, 매칭에 실패하면 실패한 횟수만큼 기간이 늘어나요</p>
          {!member && (
          <>
          <div className="row" style={{ gap: 8 }}>
            {(
              [
                ['1개월', 35000],
                ['3개월', 75000],
                ['6개월', 120000],
              ] as const
            ).map(([l, p]) => (
              <div key={l} className="plan">
                <span className="t-label-m">{l}</span>
                <span className="t-body-s muted">{fmt.won(p)}</span>
              </div>
            ))}
          </div>
          <Button variant="secondary" onClick={() => navigate('/apply')}>
            멤버십 시작하기
          </Button>
          </>
          )}
        </div>

        <section>
          <h3 className="t-h3">사용 내역</h3>
          {payments.data.map((p) => {
            const [what, result] = usageLabel[p.status] ?? ['결제', p.status]
            return (
              <div key={p.id} className="usage-row">
                <span className="t-label-s muted">{fmt.md(p.createdAt)}</span>
                <span className="t-body-m" style={{ flex: 1 }}>
                  {payments.demo && p.pgTxId ? `${p.pgTxId} ${what}` : p.status === 'PAID' ? `${p.productLabel} ${fmt.won(p.amount)} 결제` : what}
                </span>
                <span className="t-label-s">{p.status === 'PAID' ? `${p.productLabel} ${result}` : result}</span>
              </div>
            )
          })}
        </section>
      </Body>
    </Screen>
  )
}

/** 6-1a 참가 이력 전체 */
export function HistoryPage() {
  const navigate = useNavigate()
  const apps = useApi(() => get<ApplicationView[]>('/api/applications'), demoApplications)
  const history = byLatest(apps.data)
  return (
    <Screen>
      <AppBar title="참가 이력" onBack={() => navigate('/my')} />
      <Body className="my-body">
        <p className="t-caption c-sage">전체 {history.length}건, 행사 날짜 최신순</p>
        <section>
          {history.map((a) => (
            <HistoryRow key={a.id} a={a} />
          ))}
        </section>
      </Body>
    </Screen>
  )
}
