import { useState } from 'react'
import { useLocation, useNavigate, useParams } from 'react-router-dom'
import { AppBar, Badge, BellButton, Body, Button, Dialog, Footer, Screen, useToast } from '../../components/ui'
import { Icon, type IconName } from '../../components/Icon'
import { get, post } from '../../lib/api'
import { fmt, useApi } from '../../lib/useApi'
import type { ApplicationView, SlotItem } from '../../lib/types'
import { demoApplications, demoSlots, demoTable } from '../../lib/demo'
import tableSvg from '../../assets/match/table.svg'
import dotsSvg from '../../assets/match/dots.svg'
import './match.css'

const regionLabel = (code: string) => demoSlots.regions.find((r) => r.code === code)?.label ?? code
const DAY = ['일', '월', '화', '수', '목', '금', '토']
const mdDay = (iso: string) => `${fmt.md(iso)} (${DAY[new Date(iso).getDay()]})`

type RoomDetail = {
  roomId: number
  members: { userId: number; displayName: string; ageBand: string | null; mbti: string | null; interests: string[]; me: boolean }[]
  curation: string | null
}

const demoMembers: RoomDetail['members'] = [
  { userId: 1, displayName: '서윤', ageBand: '20대 후반', mbti: 'ENFP', interests: ['전시/공연/아트', '여행'], me: true },
  { userId: 2, displayName: '지민', ageBand: '30대 초반', mbti: 'INFJ', interests: ['여행'], me: false },
  { userId: 3, displayName: '도현', ageBand: '20대 후반', mbti: 'ESTP', interests: ['맛집/카페 탐방'], me: false },
  { userId: 4, displayName: '하은', ageBand: '30대 초반', mbti: 'ISFP', interests: ['전시/공연/아트'], me: false },
  { userId: 5, displayName: '민재', ageBand: '20대 후반', mbti: 'ENTJ', interests: ['맛집/카페 탐방'], me: false },
]

const SEATS_5 = [
  [138, 4],
  [238, 78],
  [200, 186],
  [76, 186],
  [38, 78],
]

/** 원형 테이블 둘레에 좌석 배치 (첫 번째 = 나, 12시 방향부터 시계방향) */
function TableView({ members }: { members: RoomDetail['members'] }) {
  const sorted = [...members].sort((a, b) => Number(b.me) - Number(a.me))
  const cx = 158
  const cy = 128
  const r = 104
  return (
    <div className="table-view">
      <img src={tableSvg} width={112} height={112} alt="" style={{ position: 'absolute', left: cx - 56, top: cy - 56 }} />
      {sorted.map((m, i) => {
        const a = -Math.PI / 2 + (i * 2 * Math.PI) / sorted.length
        // 5명은 Figma 56:747 좌석 좌표(아바타 왼쪽 위) 그대로, 그 외 인원은 원형 배치
        const fixed = sorted.length === 5 ? SEATS_5[i] : null
        const pos = fixed ? { left: fixed[0] - 10, top: fixed[1] } : { left: cx + r * Math.cos(a) - 30, top: cy + r * Math.sin(a) - 20 }
        return (
          <div key={m.userId} className="seat" style={pos}>
            <span className={m.me ? 'seat-avatar me' : 'seat-avatar'}>{m.displayName.slice(0, 1)}</span>
            <span className="t-label-s">{m.me ? `${m.displayName} (나)` : m.displayName}</span>
          </div>
        )
      })}
    </div>
  )
}

/** 4-1 매칭 확정 */
export function MatchConfirmed() {
  const { id } = useParams()
  const navigate = useNavigate()
  const toast = useToast()
  const [confirmCancel, setConfirmCancel] = useState(false)
  const app = useApi(() => get<ApplicationView>(`/api/applications/${id}`), demoApplications.find((a) => String(a.id) === id) ?? demoApplications[0], [id])
  const table = app.data.table ?? demoTable
  const room = useApi(
    () => (table.roomId ? get<RoomDetail>(`/api/chat/rooms/${table.roomId}`) : Promise.reject()),
    { roomId: table.roomId ?? 1, members: demoMembers, curation: null },
    [table.roomId],
  )
  const members = room.data.members.length ? room.data.members : demoMembers
  const ages = [...new Set(members.map((m) => m.ageBand).filter(Boolean))]
  const interests = [...new Set(members.flatMap((m) => m.interests))].slice(0, 3)
  const region = app.data.slots.find((s) => s.assigned)?.region

  const cancel = async () => {
    setConfirmCancel(false)
    try {
      await post(`/api/applications/${app.data.id}/cancel`)
      toast.show('불참을 알렸어요. 이용권이 차감돼요')
      app.reload()
    } catch (e) {
      toast.show(e instanceof Error ? e.message : '요청에 실패했어요')
    }
  }

  return (
    <Screen>
      <AppBar title="매칭 확정" action={<BellButton unread={false} />} />
      <Body className="match-body">
        <div className="stack" style={{ gap: 10 }}>
          <Badge tone="확정">테이블 확정</Badge>
          <h2 className="t-display-m">
            {mdDay(table.startAt)} {fmt.time(table.startAt)}
            <br />
            {region ? regionLabel(region) : table.slotLabel}
          </h2>
          <p className="t-body-s c-sage">{table.memberCount}명 테이블</p>
        </div>

        <div className="venue-card">
          <div className="venue-photo">
            {app.demo && <span className="photo-count">1 / 4</span>}
            {table.mapUrl && (
              <a className="map-chip" href={table.mapUrl} target="_blank" rel="noreferrer">
                <Icon name="map-pin" size={13} />
                네이버 지도
              </a>
            )}
            {app.demo && <img className="photo-dots" src={dotsSvg} width={39} height={6} alt="" />}
          </div>
          <div className="venue-info">
            <div className="row" style={{ gap: 8 }}>
              <h3 className="t-h2" style={{ flex: 1 }}>
                {table.venueName}
              </h3>
            </div>
            <div className="kv-row" style={{ alignItems: 'center' }}>
              <span>주소</span>
              <b className="underline">{table.address}</b>
              <button
                className="copy-chip"
                onClick={() => {
                  navigator.clipboard?.writeText(table.address).catch(() => undefined)
                  toast.show('주소를 복사했어요')
                }}
              >
                <Icon name="copy" size={14} />
                복사
              </button>
            </div>
            <div className="kv-row">
              <span>집합</span>
              <b>{fmt.time(new Date(new Date(table.startAt).getTime() - 10 * 60000).toISOString())}, 식당 입구</b>
            </div>
            <div className="kv-row">
              <span>예약명</span>
              <b className="c-terracotta">
                {table.reservationName ?? '우연한 식탁'} {table.tableCode}
              </b>
            </div>
            <div className="kv-row">
              <span>식사비</span>
              <b>{table.priceGuide ?? '1인 2~3만원'}, 현장 결제</b>
            </div>
          </div>
        </div>

        <div className="summary-card">
          <h3 className="t-h3">테이블 구성</h3>
          <div className="chips" style={{ gap: 6 }}>
            {ages.length > 0 && <Badge dot={false}>{ages.join(' ~ ')}</Badge>}
            {interests.length > 0 && <Badge dot={false}>{interests.map((i) => i.split('/')[0]).join('/')}</Badge>}
          </div>
          <TableView members={members} />
        </div>

        <Button onClick={() => navigate(table.roomId ? `/rooms/${table.roomId}` : '/rooms')}>단체방으로 이동하기</Button>

        <div className="warn-card">
          <div className="row" style={{ gap: 8 }}>
            <Icon name="alert" size={18} color="var(--paper)" />
            <p className="t-label-m">취소/노쇼 정책</p>
          </div>
          <p className="t-body-s">당일 취소/노쇼 시 이용권 차감</p>
          <Button size="S" variant="ghost" onClick={() => setConfirmCancel(true)}>
            불참 알리기
          </Button>
        </div>
      </Body>

      <Dialog open={confirmCancel} onClose={() => setConfirmCancel(false)}>
        <h3 className="t-h2">불참을 알릴까요?</h3>
        <p className="t-body-s muted">당일 취소는 이용권이 차감되고, 반복되면 이용이 제한될 수 있어요.</p>
        <div className="row" style={{ gap: 10, marginTop: 8 }}>
          <Button variant="secondary" onClick={() => setConfirmCancel(false)}>
            돌아가기
          </Button>
          <Button variant="danger" onClick={cancel}>
            불참 알리기
          </Button>
        </div>
      </Dialog>
      {toast.node}
    </Screen>
  )
}

const failDemo: ApplicationView = {
  ...demoApplications[1],
  id: 103,
  matchStatus: 'NO_MATCH',
  nextActions: ['CHOOSE_RESOLUTION'],
  slots: [{ ...demoApplications[0].slots[0], assigned: false }],
  resolution: {
    reason: '이번 회차에는 조건이 맞는 인원이 부족했어요',
    alternatives: [
      { id: 31, label: '11.12 목 19:30', region: 'HONGDAE_HAPJEONG', startAt: '2026-11-12T19:30:00', matchAt: '2026-11-10T23:59:59', slotStatus: 'OPEN', assigned: false },
      { id: 32, label: '11.19 목 19:30', region: 'EULJIRO_JONGNO', startAt: '2026-11-19T19:30:00', matchAt: '2026-11-17T23:59:59', slotStatus: 'OPEN', assigned: false },
    ],
    choice: null,
    status: 'PENDING',
    newApplicationId: null,
    refundAmount: 9900,
    refundStatus: null,
  },
}

/** 4-2 매칭 실패: 일정 이동 / 이용권 보관 / 환불 */
export function MatchFailed() {
  const { id } = useParams()
  const navigate = useNavigate()
  const toast = useToast()
  const [busy, setBusy] = useState(false)
  const app = useApi(() => get<ApplicationView>(`/api/applications/${id}`), failDemo, [id])
  const res = app.data.resolution ?? failDemo.resolution!
  // 같은 날짜의 다른 지역은 한 줄로 묶어서 보여준다
  const grouped = Object.values(
    res.alternatives.reduce<Record<string, SlotItem[]>>((acc, s) => {
      ;(acc[s.startAt] ??= []).push(s)
      return acc
    }, {}),
  )
  const slotDay = app.data.slots[0]?.startAt ?? demoTable.startAt
  // 멤버십은 매칭 실패 시 기간이 자동 연장되고 환불 대상이 아니다 (FAQ Q5)
  const membership = app.data.payment?.product?.startsWith('MEMBERSHIP') ?? false

  const choose = async (choice: 'TRANSFER' | 'KEEP_TICKET' | 'REFUND', slot?: SlotItem) => {
    setBusy(true)
    try {
      if (!app.demo) await post(`/api/applications/${app.data.id}/resolution`, { choice, slotId: slot?.id })
      if (choice === 'TRANSFER' && slot) navigate('/match/moved', { replace: true, state: { slot, regions: grouped.find((g) => g[0].startAt === slot.startAt)?.map((s) => regionLabel(s.region)) } })
      else if (choice === 'REFUND') navigate('/match/refunded', { replace: true, state: { amount: res.refundAmount ?? app.data.payment?.amount ?? 0 } })
      else {
        toast.show(membership ? '멤버십 기간이 연장됐어요. 다음 일정에 다시 신청할 수 있어요' : '이용권을 보관했어요. 다음 신청 때 사용할 수 있어요')
        window.setTimeout(() => navigate('/', { replace: true }), 900)
      }
    } catch (e) {
      toast.show(e instanceof Error ? e.message : '요청에 실패했어요')
    } finally {
      setBusy(false)
    }
  }

  return (
    <Screen>
      <AppBar title="매칭 결과" />
      <Body className="match-body">
        <div className="stack" style={{ gap: 10 }}>
          <Badge tone="경고">매칭 실패</Badge>
          <h2 className="t-display-m">매칭 실패</h2>
          <p className="t-body-s muted">{res.reason || '이번 회차에는 조건이 맞는 인원이 부족했어요'}</p>
        </div>

        <div className="option-card selected">
          <div className="row" style={{ gap: 8 }}>
            <h3 className="t-h3" style={{ flex: 1 }}>
              일정 이동
            </h3>
            <Badge dot={false}>결제 유지</Badge>
          </div>
          {grouped.length === 0 && <p className="t-body-s muted">지금 옮길 수 있는 회차가 없어요</p>}
          {grouped.map((g) => (
            <div key={g[0].startAt} className="alt-row">
              <div className="stack" style={{ flex: 1, gap: 2 }}>
                <span className="t-label-m">
                  {fmt.md(g[0].startAt)} {fmt.weekday(g[0].startAt)} {fmt.time(g[0].startAt)}
                </span>
                <span className="t-caption c-sage">{g.map((s) => regionLabel(s.region)).join('/')}</span>
              </div>
              <Button size="S" disabled={busy} onClick={() => choose('TRANSFER', g[0])}>
                이동
              </Button>
            </div>
          ))}
        </div>

        <div className="option-card">
          <div className="row" style={{ gap: 8 }}>
            <Icon name="ticket" size={20} />
            <h3 className="t-h3">{membership ? '멤버십 유지' : '이용권 보관'}</h3>
          </div>
          {membership && <p className="t-body-s muted">매칭에 실패해 멤버십 기간이 7일 연장됐어요</p>}
          <Button size="S" variant="secondary" disabled={busy} onClick={() => choose('KEEP_TICKET')}>
            {membership ? '다음에 신청하기' : '보관하기'}
          </Button>
        </div>

        {!membership && (
        <div className="option-card">
          <div className="row" style={{ gap: 8 }}>
            <Icon name="card" size={20} />
            <h3 className="t-h3">환불받기</h3>
          </div>
          <Button size="S" variant="danger" disabled={busy} onClick={() => choose('REFUND')}>
            환불
          </Button>
        </div>
        )}

        <p className="row t-caption c-sage" style={{ gap: 6 }}>
          <Icon name="clock" size={16} />
          {fmt.md(slotDay)} 12:00 이후 {membership ? '자동으로 멤버십 유지' : '자동 보관'}
        </p>
      </Body>
      {toast.node}
    </Screen>
  )
}

function ResultScreen({ icon, tone, title, sub, rows }: { icon: IconName; tone: 'mustard' | 'card'; title: string; sub: string; rows: [string, string][] }) {
  const navigate = useNavigate()
  return (
    <Screen>
      <AppBar back={false} />
      <Body className="done-body" style={{ gap: 16, paddingTop: 40 }}>
        <div className="done-check" style={tone === 'card' ? { background: 'var(--card)' } : undefined}>
          <Icon name={icon} size={34} />
        </div>
        <h2 className="t-display-m">{title}</h2>
        <p className="t-body-s muted">{sub}</p>
        <dl className="kv-box">
          {rows.map(([k, v]) => (
            <div key={k}>
              <dt>{k}</dt>
              <dd>{v}</dd>
            </div>
          ))}
        </dl>
      </Body>
      <Footer>
        <Button onClick={() => navigate('/', { replace: true })}>홈으로</Button>
      </Footer>
    </Screen>
  )
}

/** 4-2a 일정 이동 완료 */
export function MatchMoved() {
  const state = (useLocation().state ?? {}) as { slot?: SlotItem; regions?: string[] }
  const slot = state.slot ?? failDemo.resolution!.alternatives[0]
  return (
    <ResultScreen
      icon="calendar-check"
      tone="mustard"
      title="일정 이동 완료"
      sub="결제는 그대로 옮겨졌어요"
      rows={[
        ['새 회차', `${mdDay(slot.startAt)} ${fmt.time(slot.startAt)}`],
        ['지역', (state.regions ?? ['홍대/합정', '을지로/종로']).join(', ')],
        ['매칭 발표', fmt.deadline(slot.matchAt, true)],
      ]}
    />
  )
}

/** 4-2b 환불 완료 */
export function MatchRefunded() {
  const state = (useLocation().state ?? {}) as { amount?: number }
  return (
    <ResultScreen
      icon="card"
      tone="card"
      title="환불 요청 완료"
      sub="결제한 카드로 자동 환불돼요"
      rows={[
        ['환불 금액', fmt.won(state.amount ?? 9900)],
        ['환불 수단', '신용카드 (결제 카드)'],
        ['처리 기간', '영업일 기준 3~5일'],
      ]}
    />
  )
}
