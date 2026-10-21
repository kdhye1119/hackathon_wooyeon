import { Fragment, useEffect, useRef, useState } from 'react'
import { useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { AppHeader, Body, Button, Screen, Sheet, TabBar, useToast } from '../../components/ui'
import { Icon, type IconName } from '../../components/Icon'
import { get, post, session } from '../../lib/api'
import { fmt, useApi } from '../../lib/useApi'
import { demoTable } from '../../lib/demo'
import { DateTile } from '../home/Home'
import './rooms.css'

type Msg = { id: number; roomId: number; type: 'SYSTEM' | 'NOTICE' | 'REMINDER' | 'CONTENT' | 'USER'; senderUserId: number | null; senderName: string | null; content: string; createdAt: string }
type RoomSummary = { roomId: number; tableId: number; title: string; slotLabel: string; startAt: string; status: string; lastMessage: Msg | null }
type RoomDetail = {
  roomId: number
  title: string
  slotLabel: string
  startAt: string
  venue: { name: string; address: string; mapUrl: string | null; reservationName: string | null; priceGuide: string | null } | null
  curation: string | null
  members: { userId: number; displayName: string; me: boolean }[]
  content: { topics: string[]; icebreakers: string[] } | null
  contentPublished: boolean
}

const demoRooms: RoomSummary[] = [
  {
    roomId: 1,
    tableId: 7,
    title: '을지 화로',
    slotLabel: demoTable.slotLabel,
    startAt: '2026-10-22T19:30:00',
    status: 'OPEN',
    lastMessage: { id: 5, roomId: 1, type: 'USER', senderUserId: 1, senderName: '서윤', content: '반가워요! 저도 그날 저녁 기대 중이에요', createdAt: '2026-10-19T21:10:00' },
  },
  { roomId: 2, tableId: 5, title: '모래정원', slotLabel: '10.15', startAt: '2026-10-15T19:30:00', status: 'ARCHIVED', lastMessage: null },
  { roomId: 3, tableId: 3, title: '연남 비스트로', slotLabel: '10.08', startAt: '2026-10-08T19:30:00', status: 'ARCHIVED', lastMessage: null },
]
const demoUnread: Record<number, number> = { 1: 3 }

function relTime(iso: string) {
  const d = new Date(iso)
  const days = Math.floor((new Date().setHours(0, 0, 0, 0) - new Date(d).setHours(0, 0, 0, 0)) / 86400000)
  if (days <= 0) return fmt.time(iso)
  if (days === 1) return '어제'
  return fmt.md(iso)
}

/** 4-0 단체방 목록 */
export function RoomList() {
  const navigate = useNavigate()
  const { data } = useApi(() => get<RoomSummary[]>('/api/chat/rooms'), demoRooms)
  const now = Date.now()
  const active = data.filter((r) => r.status !== 'ARCHIVED' && r.status !== 'CLOSED' && new Date(r.startAt).getTime() + 1000 * 60 * 60 * 24 > now)
  const past = data.filter((r) => !active.includes(r))

  return (
    <Screen>
      <AppHeader />
      <Body className="rooms-body">
        <section className="home-section">
          <h2 className="t-h2">진행 중</h2>
          {active.length === 0 ? (
            <div className="empty-box">
              <p className="t-body-s muted">매칭이 확정되면 단체방이 열려요</p>
            </div>
          ) : (
            <div className="list-card">
              {active.map((r) => (
                <button key={r.roomId} className="list-row room-row" onClick={() => navigate(`/rooms/${r.roomId}`)}>
                  <DateTile iso={r.startAt} />
                  <div className="list-main">
                    <div className="row">
                      <span className="t-h3" style={{ flex: 1 }}>
                        {r.title}
                      </span>
                      {r.lastMessage && <span className="t-label-s muted">{relTime(r.lastMessage.createdAt)}</span>}
                    </div>
                    <div className="row" style={{ gap: 8 }}>
                      <span className="t-body-s muted ellipsis" style={{ flex: 1 }}>
                        {r.lastMessage?.content ?? '단체방이 열렸어요'}
                      </span>
                      {demoUnread[r.roomId] && session.token === 'demo' ? <span className="unread-count">{demoUnread[r.roomId]}</span> : null}
                    </div>
                  </div>
                </button>
              ))}
            </div>
          )}
        </section>
        {past.length > 0 && (
          <section className="home-section">
            <h2 className="t-h2">지난 모임</h2>
            <div className="list-card">
              {past.map((r) => (
                <button key={r.roomId} className="list-row room-row past" onClick={() => navigate(`/rooms/${r.roomId}`)}>
                  <DateTile iso={r.startAt} />
                  <div className="list-main">
                    <div className="row">
                      <span className="t-h3" style={{ flex: 1 }}>
                        {r.title}
                      </span>
                      <span className="t-label-s muted">{fmt.md(new Date(new Date(r.startAt).getTime() + 86400000).toISOString())}</span>
                    </div>
                    <span className="t-body-s muted ellipsis">식사가 끝나 단체방이 보관됐어요</span>
                  </div>
                </button>
              ))}
            </div>
          </section>
        )}
      </Body>
      <TabBar />
    </Screen>
  )
}

const demoDetail: RoomDetail = {
  roomId: 1,
  title: '을지 화로',
  slotLabel: demoTable.slotLabel,
  startAt: demoTable.startAt,
  venue: { name: '을지 화로', address: demoTable.address, mapUrl: demoTable.mapUrl, reservationName: 'E-1022-3', priceGuide: '1인 2~3만원' },
  curation: '전시, 여행, 맛집을 좋아하는 5명이고, 모두 처음 만나는 분들이에요',
  members: [
    { userId: 1, displayName: '서윤', me: true },
    { userId: 2, displayName: '지민', me: false },
    { userId: 3, displayName: '도현', me: false },
    { userId: 4, displayName: '하은', me: false },
    { userId: 5, displayName: '민재', me: false },
  ],
  content: { topics: ['최근에 본 전시 중 아직도 생각나는 작품은?', '요즘 가장 좋았던 맛집 한 곳', '다음 여행지로 딱 한 곳만 고른다면?'], icebreakers: ['문장 3개 중 거짓 하나 맞히기'] },
  contentPublished: true,
}

const demoMessages: Msg[] = [
  { id: 1, roomId: 1, type: 'NOTICE', senderUserId: null, senderName: null, content: '테이블 확정\n10.22(목) 19:30, 을지 화로 (을지로/종로)\n예약명 우연한 식탁 E-1022-3, 1인 2~3만원', createdAt: '2026-10-20T00:01:00' },
  { id: 2, roomId: 1, type: 'SYSTEM', senderUserId: null, senderName: null, content: '큐레이션\n전시, 여행, 맛집을 좋아하는 5명이고, 모두 처음 만나는 분들이에요', createdAt: '2026-10-20T00:01:00' },
  { id: 3, roomId: 1, type: 'USER', senderUserId: 2, senderName: '지민', content: '안녕하세요! 목요일에 뵐게요 :)', createdAt: '2026-10-20T20:12:00' },
  { id: 4, roomId: 1, type: 'USER', senderUserId: 1, senderName: '서윤', content: '반가워요! 저도 그날 저녁 기대 중이에요', createdAt: '2026-10-20T21:10:00' },
  { id: 5, roomId: 1, type: 'REMINDER', senderUserId: null, senderName: null, content: '내일 19:30\n19:20 입구 집합', createdAt: '2026-10-21T19:30:00' },
  { id: 6, roomId: 1, type: 'CONTENT', senderUserId: null, senderName: null, content: '대화 주제\n최근에 본 전시 중 아직도 생각나는 작품은?\n요즘 가장 좋았던 맛집 한 곳\n다음 여행지로 딱 한 곳만 고른다면?', createdAt: '2026-10-22T19:30:00' },
  { id: 7, roomId: 1, type: 'CONTENT', senderUserId: null, senderName: null, content: '아이스브레이킹\n문장 3개 중 거짓 하나 맞히기', createdAt: '2026-10-22T19:30:00' },
]

const CARD_ICON: [RegExp, IconName][] = [
  [/^테이블|확정/, 'calendar-check'],
  [/^큐레이션/, 'sparkle'],
  [/^대화 주제/, 'chat'],
  [/^아이스브레이킹/, 'star'],
]

/** 시스템 메시지를 카드로: 첫 줄 = 제목, 나머지 = 본문 */
function SystemCard({ m }: { m: Msg }) {
  const [title, ...lines] = m.content.split('\n')
  const icon: IconName = m.type === 'REMINDER' ? 'bell' : (CARD_ICON.find(([re]) => re.test(title))?.[1] ?? 'megaphone')
  const topics = m.type === 'CONTENT' && /주제/.test(title)
  return (
    <div className={topics ? 'sys-card topics' : 'sys-card'}>
      <div className="row" style={{ gap: 6 }}>
        <Icon name={icon} size={18} color="var(--terracotta)" />
        <p className="t-label-m c-terracotta" style={{ flex: 1 }}>
          {title}
        </p>
        <span className="t-caption c-sage">우연한 식탁</span>
      </div>
      {topics
        ? lines
            .filter((l) => l.trim())
            .map((l, i) => {
              // "1. 주제" 줄만 번호 행으로, 안내 문장·게임 제목은 그대로
              const n = /^(\d+)\.\s*(.*)$/.exec(l)
              return n ? (
                <div key={i} className="topic-row">
                  <span className="topic-no">{n[1]}</span>
                  <p className="t-body-s">{n[2]}</p>
                </div>
              ) : (
                <p key={i} className={/^🎮/.test(l) ? 't-label-m topic-sub' : 't-body-s'}>
                  {l}
                </p>
              )
            })
        : lines.map((l, i) => (
            <p key={i} className="t-body-s">
              {l}
            </p>
          ))}
    </div>
  )
}

const DAY = ['일', '월', '화', '수', '목', '금', '토']
function dayLabel(m: Msg) {
  const d = `${fmt.md(m.createdAt)} (${DAY[new Date(m.createdAt).getDay()]})`
  if (m.type === 'REMINDER') return `${d}, 리마인드`
  if (m.type === 'CONTENT') return `${d} ${fmt.time(m.createdAt)}, 식사 시작`
  return d
}

/** 4-3 단체방 (+ 4-4 운영진에게 요청) */
export function ChatRoom() {
  const { roomId } = useParams()
  const navigate = useNavigate()
  const toast = useToast()
  const me = session.user?.id ?? 1
  const detail = useApi(() => get<RoomDetail>(`/api/chat/rooms/${roomId}`), demoDetail, [roomId])
  const msgs = useApi(() => get<Msg[]>(`/api/chat/rooms/${roomId}/messages`), demoMessages, [roomId])
  const [text, setText] = useState('')
  const [params] = useSearchParams()
  const [sheet, setSheet] = useState(params.has('request'))
  const endRef = useRef<HTMLDivElement>(null)
  const archived = new Date(detail.data.startAt).getTime() + 1000 * 60 * 60 * 6 < Date.now() && !msgs.demo

  // 새 메시지 동기화: 3초마다 afterId 이후만 가져온다 (STOMP 대체)
  const lastId = useRef(0)
  lastId.current = msgs.data[msgs.data.length - 1]?.id ?? 0
  const { demo, setData } = msgs
  useEffect(() => {
    if (demo) return
    const t = window.setInterval(async () => {
      try {
        const more = await get<Msg[]>(`/api/chat/rooms/${roomId}/messages?afterId=${lastId.current}`)
        if (more.length) setData((prev) => [...prev, ...more.filter((m) => !prev.some((p) => p.id === m.id))])
      } catch {
        /* 다음 주기에 재시도 */
      }
    }, 3000)
    return () => window.clearInterval(t)
  }, [demo, setData, roomId])

  // (i) 모임 정보: 이 단체방에 해당하는 신청의 4-1 매칭 확정 화면
  const openInfo = async () => {
    if (msgs.demo) return navigate('/match/101')
    try {
      const apps = await get<{ id: number; table: { roomId: number | null } | null }[]>('/api/applications')
      const app = apps.find((a) => String(a.table?.roomId) === roomId)
      if (app) navigate(`/match/${app.id}`)
    } catch {
      toast.show('모임 정보를 불러오지 못했어요')
    }
  }

  useEffect(() => {
    endRef.current?.scrollIntoView({ block: 'end' })
  }, [msgs.data.length])

  const send = async () => {
    const content = text.trim()
    if (!content) return
    setText('')
    if (msgs.demo) {
      msgs.setData((prev) => [...prev, { id: Date.now(), roomId: Number(roomId), type: 'USER', senderUserId: me, senderName: '나', content, createdAt: new Date().toISOString() }])
      return
    }
    try {
      const m = await post<Msg>(`/api/chat/rooms/${roomId}/messages`, { content })
      msgs.setData((prev) => (prev.some((p) => p.id === m.id) ? prev : [...prev, m]))
    } catch (e) {
      setText(content)
      toast.show(e instanceof Error ? e.message : '메시지를 보내지 못했어요')
    }
  }

  const suggestTopic = () => {
    const topics = detail.data.content?.topics ?? demoDetail.content!.topics
    toast.show(`💬 ${topics[Math.floor(Math.random() * topics.length)]}`)
  }

  const d = detail.data
  const notice = `공지, ${fmt.md(d.startAt)}(${DAY[new Date(d.startAt).getDay()]}) ${fmt.time(new Date(new Date(d.startAt).getTime() - 600000).toISOString())} ${d.venue?.name ?? d.title} 입구${d.venue?.reservationName ? `, 예약명 ${d.venue.reservationName}` : ''}`

  let lastDay = ''
  return (
    <div className="screen chat">
      <header className="chat-header">
        <button className="icon-btn" aria-label="뒤로" onClick={() => navigate('/rooms')}>
          <Icon name="chevron-left" />
        </button>
        <div className="stack" style={{ flex: 1, minWidth: 0 }}>
          <span className="t-h3">{d.title}</span>
          <span className="t-caption c-sage">
            {fmt.md(d.startAt)} ({DAY[new Date(d.startAt).getDay()]}), {d.members.length}명
          </span>
        </div>
        <button className="request-chip" onClick={() => setSheet(true)}>
          <Icon name="megaphone" size={16} />
          운영진에게 요청
        </button>
        <button className="icon-btn" aria-label="모임 정보" onClick={openInfo}>
          <Icon name="info" size={22} />
        </button>
      </header>
      <div className="chat-notice">
        <Icon name="megaphone" size={18} color="var(--terracotta)" />
        <p className="t-body-s">{notice}</p>
      </div>

      <div className="chat-scroll">
        {msgs.data.map((m) => {
          const day = m.createdAt.slice(0, 10) + (m.type === 'REMINDER' || m.type === 'CONTENT' ? m.type : '')
          const showDay = day !== lastDay && !(m.type === 'CONTENT' && lastDay.endsWith('CONTENT'))
          lastDay = day
          const mine = m.type === 'USER' && m.senderUserId === me
          return (
            <Fragment key={m.id}>
              {showDay && (
                <div className="day-pill">
                  <span>{dayLabel(m)}</span>
                </div>
              )}
              {m.type !== 'USER' ? (
                <SystemCard m={m} />
              ) : mine ? (
                <div className="bubble-row mine">
                  <p className="bubble">{m.content}</p>
                </div>
              ) : (
                <div className="bubble-row">
                  <span className="chat-avatar">{(m.senderName ?? '?').slice(0, 1)}</span>
                  <div className="stack" style={{ gap: 4, minWidth: 0 }}>
                    <span className="t-caption c-sage">{m.senderName}</span>
                    <p className="bubble">{m.content}</p>
                  </div>
                </div>
              )}
            </Fragment>
          )
        })}
        {archived && (
          <button className="feedback-cta" onClick={() => navigate('/review/latest')}>
            <span className="label">식사는 어떠셨어요? 후기 남기기</span>
            <Icon name="chevron-right" size={18} />
          </button>
        )}
        <div ref={endRef} />
      </div>

      <div className="topic-suggest">
        <button onClick={suggestTopic}>
          <Icon name="sparkle" size={16} />
          대화 주제 추천 받기
        </button>
      </div>
      <form
        className="chat-input"
        onSubmit={(e) => {
          e.preventDefault()
          send()
        }}
      >
        <input value={text} onChange={(e) => setText(e.target.value)} placeholder="메시지 보내기" aria-label="메시지" maxLength={1000} />
        <button type="submit" aria-label="보내기" disabled={!text.trim()}>
          <Icon name="send" size={20} />
        </button>
      </form>

      <RequestSheet
        open={sheet}
        onClose={() => setSheet(false)}
        onSent={() => {
          setSheet(false)
          toast.show('운영진에게 전달했어요. 답변은 알림으로 알려드려요')
        }}
      />
      {toast.node}
    </div>
  )
}

const REQUESTS = [
  ['VENUE', '식당 변경', '메뉴/분위기/위치'],
  ['SCHEDULE', '일정 변경', '다른 회차로 옮기기'],
  ['DISCOMFORT', '불편 사항', '테이블 내 불편한 상황'],
  ['ETC', '기타', ''],
] as const

function RequestSheet({ open, onClose, onSent }: { open: boolean; onClose: () => void; onSent: () => void }) {
  const [kind, setKind] = useState<string>('VENUE')
  const [detail, setDetail] = useState('')
  return (
    <Sheet open={open} onClose={onClose}>
      <h2 className="t-h2">운영진에게 요청</h2>
      <div className="stack" style={{ gap: 8 }}>
        {REQUESTS.map(([code, title, sub]) => {
          const on = kind === code
          return (
            <button key={code} className={on ? 'request-option on' : 'request-option'} onClick={() => setKind(code)} role="radio" aria-checked={on}>
              <span className="stack" style={{ flex: 1, gap: 2 }}>
                <span className="t-label-m">{title}</span>
                {sub && <span className="t-body-s muted">{sub}</span>}
              </span>
              <span className={on ? 'radio on' : 'radio'}>{on && <Icon name="check" size={14} />}</span>
            </button>
          )
        })}
      </div>
      <textarea className="request-text" value={detail} onChange={(e) => setDetail(e.target.value)} placeholder="갑각류 알레르기가 있어서 다른 식당이면 좋겠어요" rows={3} />
      <p className="row t-body-s muted" style={{ gap: 6, justifyContent: 'center' }}>
        <Icon name="shield" size={14} />
        운영진에게만 보여요
      </p>
      <div className="row" style={{ gap: 10 }}>
        <Button variant="secondary" onClick={onClose}>
          취소
        </Button>
        <Button disabled={!detail.trim()} onClick={onSent}>
          보내기
        </Button>
      </div>
    </Sheet>
  )
}
