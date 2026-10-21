import { useNavigate } from 'react-router-dom'
import { AppBar, AppHeader, Badge, Body, Button, Screen, TabBar } from '../../components/ui'
import { Icon } from '../../components/Icon'
import { get, session } from '../../lib/api'
import { fmt, useApi } from '../../lib/useApi'
import { INTERESTS, labelOf, loadDraft } from '../../lib/options'
import type { PaymentDto, TicketDto } from '../../lib/types'
import './my.css'

type Profile = { name: string | null; mbti: string | null; interests: string[] }
type HistoryItem = {
  applicationId: number
  slotLabel: string
  region: string
  startAt: string
  tableCode: string | null
  venueName: string | null
  attendanceStatus: string
  feedbackSubmitted: boolean
  feedbackAvailable: boolean
  memberCount?: number
  note?: string
}

const DAY = ['일', '월', '화', '수', '목', '금', '토']
const md = (iso: string) => `${fmt.md(iso)} (${DAY[new Date(iso).getDay()]})`

const demoProfile: Profile = { name: '김서윤', mbti: 'ENFP', interests: ['EXHIBITION_ART', 'TRAVEL', 'FOOD_CAFE'] }
const demoHistory: HistoryItem[] = [
  { applicationId: 99, slotLabel: '', region: '홍대/합정', startAt: '2026-10-15T19:30:00', tableCode: null, venueName: '모래정원', attendanceStatus: 'ATTENDED', feedbackSubmitted: true, feedbackAvailable: true, memberCount: 5 },
  { applicationId: 100, slotLabel: '', region: '홍대/합정', startAt: '2026-10-08T19:30:00', tableCode: null, venueName: '연남 비스트로', attendanceStatus: 'ATTENDED', feedbackSubmitted: false, feedbackAvailable: true, memberCount: 6 },
  { applicationId: 90, slotLabel: '', region: '을지로/종로', startAt: '2026-09-24T19:30:00', tableCode: null, venueName: null, attendanceStatus: 'NO_MATCH', feedbackSubmitted: false, feedbackAvailable: false, note: '매칭 실패 → 10.08 이동' },
]
const demoTickets: TicketDto[] = [{ id: 1, product: 'SINGLE', productLabel: '1회권', totalCount: 1, remainingCount: 1, expiresAt: '2027-04-15T23:59:59', usable: true }]

const shortInterest = (code: string) => labelOf(INTERESTS, code).split(/[/ ]/)[0]

/** 6-1 마이 */
export function MyPage() {
  const navigate = useNavigate()
  const profile = useApi(() => get<Profile>('/api/me/profile'), demoProfile)
  const history = useApi(() => get<HistoryItem[]>('/api/me/history'), demoHistory)
  const tickets = useApi(() => get<TicketDto[]>('/api/me/tickets'), demoTickets)
  const draft = loadDraft()
  const p = profile.demo && draft.name ? { name: draft.name, mbti: draft.mbti, interests: draft.interests } : profile.data
  const kept = tickets.data.filter((t) => t.usable && t.totalCount > 0).reduce((n, t) => n + t.remainingCount, 0)
  const member = tickets.data.find((t) => t.usable && t.totalCount === 0)
  const name = p.name || session.user?.name || '참가자'

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

        <button className="ticket-link" onClick={() => navigate('/my/tickets')}>
          <Icon name="ticket" color="var(--terracotta)" />
          <span className="t-h3">{member ? `${member.productLabel}, ${member.expiresAt.slice(0, 10).replace(/-/g, '.')}까지` : `보관 이용권 ${kept}장`}</span>
          <Icon name="chevron-right" size={20} />
        </button>

        <section>
          <h3 className="t-h3">참가 이력</h3>
          {history.data.length === 0 && <p className="t-body-s muted" style={{ padding: '14px 0' }}>아직 참가한 식탁이 없어요</p>}
          {history.data.map((h) => {
            const attended = h.attendanceStatus === 'ATTENDED'
            return (
              <button key={h.applicationId} className="history-row" onClick={() => (attended ? navigate(`/review/${h.applicationId}`) : undefined)} disabled={!attended}>
                <span className="stack" style={{ flex: 1, gap: 2 }}>
                  <span className="t-label-m">{md(h.startAt)}</span>
                  <span className="t-caption c-sage">
                    {h.region}
                    {h.memberCount ? `, ${h.memberCount}명` : ''}
                  </span>
                </span>
                {attended ? (
                  <>
                    <Badge dot={false}>참석</Badge>
                    <span className="row t-caption c-terracotta" style={{ gap: 4 }}>
                      <Icon name="check" size={14} />
                      {h.feedbackSubmitted ? '후기 완료' : '후기 미작성'}
                    </span>
                  </>
                ) : (
                  <Badge tone={h.attendanceStatus === 'NO_SHOW' ? '경고' : h.note ? '진행' : '중립'} dot={false}>
                    {h.note ?? (h.attendanceStatus === 'NO_SHOW' ? '불참' : h.attendanceStatus === 'CANCELED_EARLY' || h.attendanceStatus === 'CANCELED_LATE' ? '취소' : '예정')}
                  </Badge>
                )}
              </button>
            )
          })}
        </section>

        <button
          className="logout t-body-s muted underline"
          onClick={() => {
            session.clear()
            navigate('/start', { replace: true })
          }}
        >
          로그아웃
        </button>
      </Body>
      <TabBar />
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
