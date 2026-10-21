import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { AppBar, AppHeader, Body, Button, Footer, Logo, Screen, TabBar } from '../../components/ui'
import { Icon } from '../../components/Icon'
import { get, post } from '../../lib/api'
import { fmt, useApi } from '../../lib/useApi'
import type { ApplicationView, OpenSlotsResponse } from '../../lib/types'

type Recommendation = { slotId: number; label: string; region: string; startAt: string; applyDeadline: string; reason: string }
import { demoApplications, demoReviews, demoSlots } from '../../lib/demo'
import CheckInSheet from './CheckInSheet'
import heart from '../../assets/icons/heart.svg'
import heartFilled from '../../assets/icons/heart-filled.svg'
import './home.css'

export function DateTile({ iso, today }: { iso: string; today?: boolean }) {
  return (
    <div className={today ? 'date-tile today' : 'date-tile'}>
      <b>{fmt.day(iso)}</b>
      <span>{today ? '오늘' : fmt.month(iso)}</span>
    </div>
  )
}

const isToday = (iso: string) => new Date(iso).toDateString() === new Date().toDateString()

type Row = { title?: string; sub: string; warn?: boolean; today?: boolean; action?: { label: string; primary?: boolean; to: string }; dim?: boolean }

/** 신청 상태 → 홈 카드 한 줄 (3-1c 상태별 예시) */
export function describe(a: ApplicationView): Row {
  const venue = a.table?.venueName
  if (a.appStatus === 'CANCELED') {
    const refund = a.paymentStatus === 'REFUNDED' ? '전액 환불' : a.paymentStatus.startsWith('REFUND') ? '환불 진행 중' : '환불 없음'
    return { sub: `신청 취소, ${refund}`, dim: true }
  }
  if (a.attendanceStatus === 'ATTENDED' && isToday(slotDate(a)))
    return { today: true, title: `오늘 ${fmt.time(slotDate(a))}`, sub: `${venue ?? ''}, 체크인 완료`, action: { label: '단체방', primary: true, to: a.table?.roomId ? `/rooms/${a.table.roomId}` : '/rooms' } }
  if (a.paymentStatus === 'REFUND_REQUESTED' || a.paymentStatus === 'REFUND_PROCESSING') return { sub: '환불 처리 중', action: { label: '정보 확인', to: `/applications/${a.id}` } }
  if (a.paymentStatus === 'REFUND_FAILED') return { sub: '환불 지연, 운영자가 확인 중', warn: true, action: { label: '정보 확인', to: `/applications/${a.id}` } }
  if (a.paymentStatus === 'REFUNDED') return { sub: '환불 완료', dim: true }
  if (a.matchStatus === 'EXCEPTION') return { sub: '운영자가 확인 중', warn: true, action: { label: '정보 확인', to: `/applications/${a.id}` } }
  if (a.matchStatus === 'NO_MATCH' && !a.nextActions.includes('CHOOSE_RESOLUTION')) return { sub: a.resolution?.choice === 'KEEP_TICKET' ? '매칭 종료, 이용권 보관' : '매칭 종료', dim: true }
  if (a.paymentStatus === 'FAILED') return { sub: '결제 실패', warn: true, action: { label: '다시 결제', primary: true, to: `/apply/pay?app=${a.id}` } }
  if (a.nextActions.includes('PAY')) return { sub: '결제 대기', warn: true, action: { label: '결제하기', primary: true, to: `/apply/pay?app=${a.id}` } }
  if (a.nextActions.includes('RESPOND_SEAT_OFFER') && a.seatOffer)
    return { sub: `자리가 났어요, ${fmt.deadline(a.seatOffer.expiresAt)}까지 응답`, warn: true, action: { label: '응답하기', primary: true, to: `/match/${a.id}/failed` } }
  if (a.nextActions.includes('CHOOSE_RESOLUTION'))
    return { sub: '매칭 실패', warn: true, action: { label: '선택하기', primary: true, to: `/match/${a.id}/failed` } }
  if (a.nextActions.includes('CHECK_IN') || (a.matchStatus === 'CONFIRMED' && a.attendanceStatus === 'SCHEDULED' && isToday(slotDate(a)))) {
    const start = slotDate(a)
    const open = fmt.time(new Date(new Date(start).getTime() - 30 * 60000).toISOString())
    const close = fmt.time(new Date(new Date(start).getTime() + 30 * 60000).toISOString())
    return { today: true, title: `오늘 ${fmt.time(start)}`, sub: `${venue ?? ''}, 체크인 ${open}~${close}`, action: { label: '체크인', primary: true, to: `/today/${a.id}` } }
  }
  if (a.matchStatus === 'CONFIRMED')
    return { sub: venue ? `${venue}, 매칭 완료` : '매칭 완료', action: { label: '단체방', primary: true, to: a.table?.roomId ? `/rooms/${a.table.roomId}` : '/rooms' } }
  if (a.matchStatus === 'TRANSFERRED') return { sub: '다른 일정으로 이동', action: { label: '정보 확인', to: `/applications/${a.id}` } }
  if (a.matchStatus === 'REALLOCATING') return { sub: '재배치 중, 다른 테이블 찾는 중', action: { label: '정보 확인', to: `/applications/${a.id}` } }
  const announce = a.matchAnnounceAt ? `, 발표 ${fmt.dl(a.matchAnnounceAt).md}` : ''
  return { sub: `매칭 대기${announce}`, action: { label: '정보 확인', to: `/applications/${a.id}` } }
}

export const slotDate = (a: ApplicationView) => (a.slots.find((s) => s.assigned) ?? a.slots[0])?.startAt ?? a.submittedAt

/** 3-1 홈 / 2-5 첫 방문 홈 / 4-5 당일 홈 (/today/:id 이면 체크인 시트) */
export default function Home() {
  const navigate = useNavigate()
  const { id: todayId } = useParams()
  const apps = useApi(() => get<ApplicationView[]>('/api/applications'), demoApplications)
  // /today/:id 로 들어오면 그 신청을 '오늘' 모임으로 보여주고 체크인 시트를 띄운다
  const [checkedIn, setCheckedIn] = useState(false)
  const [dismissed, setDismissed] = useState(false)
  const todayApp = todayId ? apps.data.find((a) => String(a.id) === todayId) ?? null : null
  const slots = useApi(() => get<OpenSlotsResponse>('/api/slots'), demoSlots)
  // RET-02 다음 일정 추천 (이전 지역·날짜 선호 기반)
  const recs = useApi(
    () => get<Recommendation[]>('/api/me/recommendations'),
    [{ slotId: 5, label: '11/5 (목) 19:30 홍대/합정', region: '홍대/합정', startAt: '2026-11-05T19:30:00', applyDeadline: '2026-11-03T23:59:59', reason: '자주 간 홍대/합정' }],
  )

  // 진행 중인 신청만, 행사가 가까운 순으로. 끝난 내역(취소·환불·매칭 종료·일정 이동·참석·노쇼)은 기록 탭에서 보고, 피드백 대기 건은 배너로
  const finished = (a: ApplicationView) =>
    !!describe(a).dim ||
    a.matchStatus === 'TRANSFERRED' ||
    ['NO_SHOW', 'CANCELED_EARLY', 'CANCELED_LATE'].includes(a.attendanceStatus ?? '') ||
    (a.attendanceStatus === 'ATTENDED' && !isToday(slotDate(a)))
  const visible = apps.data
    .filter((a) => !a.nextActions.includes('FEEDBACK') && !finished(a))
    .sort((a, b) => slotDate(a).localeCompare(slotDate(b)))
  const feedback = apps.data.find((a) => a.nextActions.includes('FEEDBACK') && !a.feedbackSubmitted)
  const appliedDates = new Set(apps.data.filter((a) => a.appStatus !== 'CANCELED').flatMap((a) => a.slots.map((s) => s.startAt.slice(0, 10))))
  const upcoming = slots.data.dates.filter((d) => !appliedDates.has(d.date)).slice(0, visible.length ? 2 : 3)

  return (
    <Screen>
      <AppHeader />
      <Body className="home-body">
        <section className="home-section">
          <h2 className="t-h2">내 신청</h2>
          {visible.length === 0 ? (
            <div className="empty-box">
              <p className="t-body-s muted">아직 신청한 식탁이 없어요</p>
            </div>
          ) : (
            <div className="list-card">
              {visible.map((a) => {
                const iso = slotDate(a)
                const forced = todayApp?.id === a.id
                let r = describe(a)
                if (forced && !r.today)
                  r = { today: true, title: `오늘 ${fmt.time(iso)}`, sub: `${a.table?.venueName ?? ''}, 체크인 19:00~20:00`, action: { label: '체크인', primary: true, to: `/today/${a.id}` } }
                if (r.today && checkedIn && forced) r = { ...r, sub: `${a.table?.venueName ?? ''}, 체크인 완료`, action: { label: '단체방', primary: true, to: `/rooms/${a.table?.roomId ?? ''}` } }
                return (
                  <div key={a.id} className="list-row" style={r.dim ? { opacity: 0.6 } : undefined}>
                    <DateTile iso={iso} today={r.today} />
                    <div className="list-main">
                      <p className="t-h3">{r.title ?? fmt.dateLong(iso)}</p>
                      <p className={r.warn ? 't-body-s c-terracotta' : 't-body-s muted'} style={r.warn ? { opacity: 0.75 } : undefined}>
                        {r.sub}
                      </p>
                    </div>
                    {r.action && (
                      <Button size="S" variant={r.action.primary ? 'primary' : 'secondary'} onClick={() => navigate(r.action!.to)}>
                        {r.action.label}
                      </Button>
                    )}
                  </div>
                )
              })}
            </div>
          )}
        </section>

        {feedback && (
          <button className="review-banner" onClick={() => navigate(`/review/${feedback.id}`)}>
            <Icon name="star" size={20} />
            <span>
              {new Date(slotDate(feedback)).getMonth() + 1}월 {fmt.day(slotDate(feedback))}일 모임 후기를 남겨주세요
            </span>
            <Icon name="chevron-right" size={18} />
          </button>
        )}

        <section className="home-section">
          <h2 className="t-h2">다가오는 모집</h2>
          {upcoming.length === 0 && (
            <div className="empty-box">
              <p className="t-body-s muted">지금 신청할 수 있는 회차가 없어요</p>
            </div>
          )}
          <div className="list-card" hidden={upcoming.length === 0}>
            {upcoming.map((d, i) => {
              const rec = (recs.demo && !apps.demo ? [] : recs.data).find((r) => r.startAt.slice(0, 10) === d.date)
              return (
                <div key={d.date} className="list-row">
                  <DateTile iso={d.date} />
                  <div className="list-main">
                    <p className="t-h3">{fmt.dateLong(d.date)}</p>
                    {rec ? (
                      <p className="t-body-s c-terracotta">추천, {rec.reason}</p>
                    ) : (
                      <p className="t-body-s muted">{(slots.data.slots.find((x) => x.date === d.date)?.startTime ?? '19:30').slice(0, 5)}, {fmt.dl(d.applyDeadline).md} 마감</p>
                    )}
                  </div>
                  <Button
                    size="S"
                    variant={visible.length === 0 && i === 0 ? 'primary' : 'secondary'}
                    onClick={() => navigate(`/apply?date=${d.date}`)}
                  >
                    신청
                  </Button>
                </div>
              )
            })}
          </div>
        </section>

        <Reviews demo={apps.demo} />
      </Body>
      <TabBar />
      <CheckInSheet
        app={todayApp}
        open={!!todayApp && !dismissed}
        demo={apps.demo}
        onClose={() => {
          setDismissed(true)
          if (!checkedIn) navigate('/', { replace: true })
        }}
        onDone={() => {
          setCheckedIn(true)
          apps.reload()
        }}
      />
    </Screen>
  )
}

type ReviewItem = { id: number; comment: string; recommendScore: number; likes: number; liked: boolean }

/** 참가자 후기: 실데이터는 참가자가 공개를 고른 한마디 (이름 없이), 시안은 Figma 예시 */
/** 홈: 추천순 5개 + 자세히 보기 / 전체(full): 추천순·최신순으로 모두 */
const HOME_REVIEWS = 5
function Reviews({ demo, guest = false, full = false }: { demo: boolean; guest?: boolean; full?: boolean }) {
  const navigate = useNavigate()
  const [sort, setSort] = useState<'best' | 'new'>('best')
  const sortParam = sort === 'best' ? 'recommend' : 'recent'
  // 홈은 하나 더 받아서 '자세히 보기'가 필요한지 판단
  const limit = full ? 30 : HOME_REVIEWS + 1
  const server = useApi<ReviewItem[] | null>(() => get<ReviewItem[]>(`/api/reviews?sort=${sortParam}&limit=${limit}`), null, [sort])
  // 비회원: 실제 공개 후기만 (가데이터 없음)
  const [publicList, setPublicList] = useState<ReviewItem[] | null>(null)
  useEffect(() => {
    if (!guest) return
    get<ReviewItem[]>(`/api/public/reviews?sort=${sortParam}&limit=${limit}`)
      .then(setPublicList)
      .catch(() => setPublicList([]))
  }, [guest, sortParam, limit])
  const [mine, setMine] = useState<Record<number, { liked: boolean; likes: number }>>({})
  const all: ReviewItem[] = guest
    ? (publicList ?? [])
    : demo || server.demo || !server.data
      ? (sort === 'best' ? demoReviews : [...demoReviews].reverse()).map((r) => ({ id: r.id, comment: r.text, recommendScore: r.nps, likes: r.likes + (r.likedByMe ? 1 : 0), liked: r.likedByMe }))
      : server.data
  const list = full ? all : all.slice(0, HOME_REVIEWS)
  const more = !full && all.length > HOME_REVIEWS
  if (guest && publicList !== null && publicList.length === 0)
    return (
      <section className="home-section">
        <h2 className="t-h2">참가자 후기</h2>
        <div className="empty-box">
          <p className="t-body-s muted">아직 공개된 후기가 없어요</p>
        </div>
      </section>
    )
  if (!guest && !demo && !server.demo && server.data && server.data.length === 0) return null
  const toggle = async (r: ReviewItem) => {
    if (guest) return navigate('/start')
    const cur = mine[r.id] ?? { liked: r.liked, likes: r.likes }
    // 먼저 화면에 반영하고 서버 결과로 맞춘다
    setMine((m) => ({ ...m, [r.id]: { liked: !cur.liked, likes: cur.likes + (cur.liked ? -1 : 1) } }))
    if (demo || server.demo) return
    try {
      const res = await post<{ likes: number; liked: boolean }>(`/api/reviews/${r.id}/like`)
      setMine((m) => ({ ...m, [r.id]: res }))
    } catch {
      setMine((m) => ({ ...m, [r.id]: cur }))
    }
  }
  return (
    <section className="home-section">
      <div className="row">
        <h2 className="t-h2" style={{ flex: 1 }}>
          {full ? `후기 ${all.length}개` : '참가자 후기'}
        </h2>
        {full ? (
          <div className="seg" role="tablist">
            <button role="tab" aria-selected={sort === 'best'} onClick={() => setSort('best')}>
              추천순
            </button>
            <button role="tab" aria-selected={sort === 'new'} onClick={() => setSort('new')}>
              최신순
            </button>
          </div>
        ) : (
          more && (
            <button className="t-label-m c-sage more-link" onClick={() => navigate(guest ? '/guest/reviews' : '/reviews')}>
              자세히 보기
              <Icon name="chevron-right" size={16} />
            </button>
          )
        )}
      </div>
      <div className={full ? 'review-list' : 'carousel'}>
        {list.map((r) => {
          const st = mine[r.id] ?? { liked: r.liked, likes: r.likes }
          return (
            <article key={r.id} className="review-card">
              <span className="quote">“</span>
              <p className="review-text">{r.comment}</p>
              <div className="row" style={{ gap: 5 }}>
                <button className={st.liked ? 'like on' : 'like'} onClick={() => toggle(r)} aria-pressed={st.liked}>
                  <img src={st.liked ? heartFilled : heart} width={16} height={15} alt="" />
                  {st.likes}
                </button>
                <span className="nps">
                  <span className="muted">추천</span>
                  <span>{`${r.recommendScore}/10`}</span>
                </span>
              </div>
            </article>
          )
        })}
      </div>
    </section>
  )
}

/** 비회원 둘러보기: 실제 공개 후기만 보여주고, 신청은 로그인 후 */
export function GuestHome() {
  const navigate = useNavigate()
  return (
    <Screen>
      <header className="app-header">
        <span style={{ flex: 1 }}>
          <Logo />
        </span>
      </header>
      <Body className="home-body">
        <section className="home-section">
          <h2 className="t-h2">처음 만나는 사람들과 저녁 한 끼</h2>
          <p className="t-body-s muted">매주 목요일, 비슷한 사람들끼리 4~6명 테이블로 자동 매칭해 드려요. 참가자들이 직접 남긴 후기를 둘러보세요.</p>
        </section>
        <Reviews demo={false} guest />
      </Body>
      <Footer>
        <Button onClick={() => navigate('/start')}>로그인하고 신청하기</Button>
      </Footer>
    </Screen>
  )
}

/** 참가자 후기 전체: 추천순·최신순 */
export function ReviewsPage({ guest = false }: { guest?: boolean }) {
  const navigate = useNavigate()
  const apps = useApi(() => get<ApplicationView[]>('/api/applications'), demoApplications)
  return (
    <Screen>
      <AppBar title="참가자 후기" onBack={() => navigate(guest ? '/guest' : '/')} />
      <Body className="home-body">
        <Reviews demo={!guest && apps.demo} guest={guest} full />
      </Body>
    </Screen>
  )
}
