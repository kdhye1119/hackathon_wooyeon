import { useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { AdminLayout, DataTable, DemoNotice, useAdminApi } from '../AdminLayout'
import { Badge, Button, Dialog, useToast } from '../../components/ui'
import { Icon } from '../../components/Icon'
import { get, post } from '../../lib/api'
import type { MemberDetail, Policy, ReportDto, TableDetail as Detail, Venue } from '../types'
import { INTERESTS, labelOf } from '../../lib/options'
import { adminDemo, slotDayLabel, slotRegion, tableLabel } from '../demo'

// Figma 14:634 시안 데이터
let seq = 200
const m = (name: string, birth: number, gender: string, mbti: string, interests: string[]): MemberDetail => ({
  ...adminDemo.tableDetail.members[0],
  applicationId: seq++,
  name,
  age: 2026 - birth,
  gender,
  mbti,
  interests,
})
const DEMO: Detail = {
  ...adminDemo.tableDetail,
  tableCode: 'T-1022-EJ-3',
  totalScore: 82,
  scores: { total: 82, age: 0, gender: 24, mbti: 17, interest: 19, desire: 13, newness: 9 },
  minBirthYear: 1993,
  maxBirthYear: 1999,
  male: 2,
  female: 3,
  mbtiSummary: 'E 3, I 2',
  commonInterests: ['전시', '여행', '맛집'],
  chatMessages: 12,
  venue: { venueId: 3, name: '을지 화로', address: '서울 중구 을지로 OO길 12, 2층', reservationName: '우연한 식탁 E-1022-3', priceRange: '1인 2~3만원' },
  members: [
    m('김서윤', 1999, 'FEMALE', 'ENFP', ['전시', '여행', '맛집']),
    m('박도현', 1993, 'MALE', 'INTJ', ['맛집', '자기계발']),
    m('이지민', 1997, 'FEMALE', 'ESFJ', ['전시', '사진/영상']),
    m('최하은', 1995, 'FEMALE', 'ENTP', ['여행', '사진/영상']),
    m('정민재', 1994, 'MALE', 'ISTP', ['운동', '맛집']),
  ],
} as Detail & { totalScore: number }

const DEMO_TEXT = {
  desire: '5명 중 4명 충족',
  exclusion: '차단/비선호 2건 반영, 충돌 없음',
  realloc: '1회, 최초 3명이라 홍대 테이블과 병합',
  confirm: '10.20 24:03, 운영자 개입 없음',
  venueName: '을지 화로, 한식 다이닝, 평점 4.7',
  allergy: '갑각류 1명, 식당 전달 완료',
  assign: '10.20 24:03 자동, 재고 3/3 사용, 예약 확정 문자 수신',
  roomId: 'CR-1022-EJ-3',
  history: [
    ['10.20 24:01', '시스템', '최초 조합 4명 + 홍대/합정 미완성 테이블에서 1명 병합 (재배치 1)'],
    ['10.20 24:03', '시스템', '필수 조건 재검증 통과 → 자동 확정, 식당 배정, 단체방 생성'],
    ['10.21 08:40', '시스템', '정민재 프로필 알레르기 정보 수정 → 식당에 자동 전달'],
  ],
  posts: [
    ['매칭 확정 알림', '10.20 24:03, 푸시 5/5', true],
    ['시간/장소/취소 정책 공지', '10.20 24:03, 시스템 메시지', true],
    ['테이블 조합 큐레이션', '10.20 24:03, 시스템 메시지', true],
    ['행사 1일 전 리마인드', '10.21 19:30 예약, 푸시 + 단체방', false],
    ['대화 주제 3개 + 아이스브레이킹', '10.22 19:30 예약, 단체방', false],
    ['만족도/안전 피드백 요청', '10.22 22:30 예약, 푸시', false],
  ] as [string, string, boolean][],
}

const p2 = (n: number) => String(n).padStart(2, '0')
/** 자정 직후는 전날 24:xx 로 (Figma 표기) */
const at = (iso: string | null) => {
  if (!iso) return ''
  const d = new Date(iso)
  if (d.getHours() === 0) {
    const prev = new Date(d.getTime() - 86400000)
    return `${p2(prev.getMonth() + 1)}.${p2(prev.getDate())} 24:${p2(d.getMinutes())}`
  }
  return `${p2(d.getMonth() + 1)}.${p2(d.getDate())} ${p2(d.getHours())}:${p2(d.getMinutes())}`
}
const POST_LABEL: Record<string, string> = {
  CONFIRM: '매칭 확정 알림',
  NOTICE: '시간/장소/취소 정책 공지',
  CURATION: '테이블 조합 큐레이션',
  REMINDER: '행사 1일 전 리마인드',
  CONTENT: '대화 주제 3개 + 아이스브레이킹',
  TOPICS: '대화 주제 3개 + 아이스브레이킹',
  FEEDBACK: '만족도/안전 피드백 요청',
}
const ATTEND: Record<string, [string, '확정' | '완료' | '경고' | '중립']> = {
  SCHEDULED: ['참석 예정', '확정'],
  ATTENDED: ['참석', '완료'],
  CHECKED_IN: ['체크인', '완료'],
  NO_SHOW: ['노쇼', '경고'],
  CANCELED_LATE: ['당일 취소', '경고'],
  CANCELED_EARLY: ['사전 취소', '중립'],
}

function Info({ rows }: { rows: [string, string][] }) {
  return (
    <>
      {rows.map(([k, v]) => (
        <div key={k} className="adm-info">
          <span>{k}</span>
          <b>{v}</b>
        </div>
      ))}
    </>
  )
}

/** A06-1 테이블 상세 */
export default function TableDetail() {
  const { id } = useParams()
  const navigate = useNavigate()
  const toast = useToast()
  const res = useAdminApi(() => get<Detail>(`/api/admin/tables/${id}`), DEMO, [id])
  const venues = useAdminApi(() => get<Venue[]>('/api/admin/venues'), adminDemo.venues)
  const reports = useAdminApi(() => get<ReportDto[]>('/api/admin/reports'), [] as ReportDto[])
  const policy = useAdminApi(() => get<Policy>('/api/admin/policy'), adminDemo.policy)
  const [venueOpen, setVenueOpen] = useState(false)
  const [venueId, setVenueId] = useState<number | null>(null)
  const t = res.data
  const demo = res.demo
  const total = Math.round(t.scores.total ?? Object.entries(t.scores).reduce((n, [k, v]) => (k === 'total' ? n : n + v), 0))
  // 서버 조건별 점수는 0~1 충족률 → 정책 가중치를 곱해 점수로 (합 = 총점)
  const W: Record<string, number> = { age: policy.data.ageWeight, gender: policy.data.genderWeight, mbti: policy.data.mbtiWeight, interest: policy.data.interestWeight, desire: policy.data.desireWeight, newness: policy.data.newnessWeight }
  const part = (k: string) => {
    const v = t.scores[k] ?? 0
    return Math.round(v <= 1 ? v * (W[k] ?? 0) : v)
  }
  const isFemale = (g: string) => g === 'FEMALE' || g === '여'
  const ei = t.members.reduce((n, x) => (x.mbti?.startsWith('E') ? n + 1 : n), 0)
  const mbtiText = demo ? t.mbtiSummary : `E ${ei}, I ${t.members.filter((x) => x.mbti?.startsWith('I')).length}`
  const interestText = t.commonInterests.map((c) => labelOf(INTERESTS, c).split(/[//(]/)[0].trim()).join(', ')
  const region = slotRegion(t.slotLabel)
  const ageMin = 2026 - t.maxBirthYear
  const ageMax = 2026 - t.minBirthYear
  const desireOk = t.members.filter((x) => x.desireSatisfied).length
  const reallocated = t.members.filter((x) => x.reallocationCount > 0)
  const allergies = t.members.filter((x) => x.foodRestrictions)

  const basis: [string, string][] = [
    ['연령', `${ageMin}~${ageMax}세, 차이 ${t.ageGap}살`],
    ['성비', `여 ${t.female}, 남 ${t.male}`],
    ['MBTI', mbtiText],
    ['공통 관심사', interestText || '없음'],
    ['만나고 싶은 사람', demo ? DEMO_TEXT.desire : `${t.memberCount}명 중 ${desireOk}명 충족`],
    ['이전 만남', t.metBeforePairs ? `${t.metBeforePairs}쌍` : '0쌍, 모두 처음'],
    ['제외 관계', demo ? DEMO_TEXT.exclusion : t.exclusionConflict ? '충돌 있음, 확인 필요' : '차단/비선호 반영, 충돌 없음'],
    ['재배치', demo ? DEMO_TEXT.realloc : reallocated.length ? `${reallocated.length}명, ${reallocated[0].placementReason ?? ''}` : '없음'],
    ['자동 확정', demo ? DEMO_TEXT.confirm : t.autoConfirmed ? `${at(t.confirmedAt)}, 운영자 개입 없음` : t.confirmedAt ? `${at(t.confirmedAt)}, 운영자 확정` : '확정 전'],
  ]
  const scoreParts: [string, string][] = [
    ['gender', '성비'],
    ['mbti', 'MBTI'],
    ['interest', '관심사'],
    ['desire', '성향'],
    ['newness', '새로운 사람'],
  ]
  const posts: [string, string, boolean][] = demo
    ? DEMO_TEXT.posts
    : t.scheduledPosts.map((p) => [POST_LABEL[p.kind] ?? p.kind, `${at(p.scheduledAt)}${p.status === 'SENT' ? '' : p.status === 'FAILED' ? `, 실패 ${p.attempts}회` : ' 예약'}${p.kind === 'REMINDER' ? ', 푸시 + 단체방' : ', 단체방'}`, p.status === 'SENT'])
  const history: string[][] = demo
    ? DEMO_TEXT.history
    : [
        ...(t.reallocatedMembers ? [[at(t.confirmedAt), '시스템', `재배치 ${t.reallocatedMembers}명 반영 (${{ INITIAL: '최초 조합', MERGED: '병합', SPLIT: '분리', REALLOCATED: '재배치' }[t.formation] ?? t.formation})`]] : []),
        ...(t.confirmedAt ? [[at(t.confirmedAt), t.autoConfirmed ? '시스템' : '운영자', `필수 조건 재검증 통과 → ${t.autoConfirmed ? '자동 ' : ''}확정${t.venue ? ', 식당 배정' : ''}${t.chatRoomId ? ', 단체방 생성' : ''}`]] : []),
      ]

  const regionVenues = venues.data.filter((v) => v.active && v.regionLabel === region)
  const assign = async () => {
    if (!venueId) return
    try {
      if (!demo) {
        const next = await post<Detail>(`/api/admin/tables/${t.tableId}/venue`, { venueId })
        res.setData(next)
      } else {
        const v = venues.data.find((x) => x.id === venueId)!
        res.setData({ ...t, venue: { venueId: v.id, name: v.name, address: v.address, reservationName: t.venue?.reservationName ?? null, priceRange: v.priceGuide } })
      }
      setVenueOpen(false)
      toast.show('식당을 바꾸고 단체방에 안내를 올렸어요')
    } catch (e) {
      toast.show(e instanceof Error ? e.message : '바꾸지 못했어요')
    }
  }
  // ---- 운영자 긴급 수정 (MAT-14): 참가자 이동 / 분리·병합 / 해제. 모든 변경은 신청 이력에 운영자로 남는다
  const slotTables = useAdminApi(() => get<{ tableId: number; tableCode: string; status: string; memberCount: number }[]>(`/api/admin/tables?slotId=${t.slotId}`), [], [t.slotId])
  const others = slotTables.data.filter((x) => x.tableId !== t.tableId && x.status === 'CONFIRMED')
  const [edit, setEdit] = useState<null | 'move' | 'split' | 'merge' | 'dissolve'>(null)
  const [picked, setPicked] = useState<number[]>([])
  const [target, setTarget] = useState<number | null>(null)
  const [reason, setReason] = useState('')
  const [editing, setEditing] = useState(false)
  const openEdit = (m: 'move' | 'split' | 'merge' | 'dissolve') => {
    if (demo) return toast.show('시안 화면에서는 바꿀 수 없어요')
    setEdit(m)
    setPicked([])
    setTarget(null)
    setReason('')
  }
  const runEdit = async () => {
    setEditing(true)
    try {
      if (edit === 'dissolve') {
        await post(`/api/admin/tables/${t.tableId}/dissolve`, { reason })
        toast.show('테이블을 해제했어요. 참가자는 재배치되거나 대체 일정 안내를 받아요')
        window.setTimeout(() => navigate('/admin/matching'), 900)
        return
      }
      // 병합: 전원을 대상 테이블로 / 이동: 고른 참가자를 대상 테이블로 / 분리: 고른 참가자를 새 테이블로
      const ids = edit === 'merge' ? t.members.map((m) => m.applicationId) : picked
      let dest: number | null = edit === 'split' ? null : target
      for (const appId of ids) {
        const r = await post<{ tableId: number }>(`/api/admin/tables/${t.tableId}/move`, { applicationId: appId, targetTableId: dest, reason })
        dest = r.tableId
      }
      toast.show(edit === 'merge' ? '테이블을 합쳤어요' : edit === 'split' ? '새 테이블로 분리했어요' : '참가자를 옮겼어요')
      setEdit(null)
      if (dest) window.setTimeout(() => navigate(`/admin/tables/${dest}`), 700)
    } catch (e) {
      toast.show(e instanceof Error ? e.message : '바꾸지 못했어요')
    } finally {
      setEditing(false)
    }
  }
  const editReady =
    edit === 'dissolve' ? reason.trim().length > 0 : edit === 'merge' ? target !== null : edit === 'split' ? picked.length > 0 : picked.length > 0 && target !== null

  return (
    <AdminLayout title="테이블 상세" onBack={() => navigate('/admin/matching')}>
      <DemoNotice show={demo} />
      <div className="adm-td-head">
        <div className="stack" style={{ gap: 4, flex: 1, minWidth: 0 }}>
          <div className="row" style={{ gap: 8 }}>
            <h2 className="t-h1">{tableLabel(t.tableCode, t.slotLabel)}</h2>
            {t.autoConfirmed && (
              <Badge tone="확정" dot={false}>
                자동 확정
              </Badge>
            )}
            <Badge tone="확정" dot={false}>
              {total}점
            </Badge>
          </div>
          <p className="t-body-s">
            {slotDayLabel(t.slotLabel)} {t.slotLabel.split(' ')[2]}, {region}, {t.memberCount}명
          </p>
        </div>
        <button className="adm-outline" disabled={!demo && t.status !== 'CONFIRMED'} title={!demo && t.status !== 'CONFIRMED' ? '확정 상태 테이블만 수정할 수 있어요' : undefined} onClick={() => openEdit('move')}>
          참가자 이동
        </button>
        <button className="adm-outline" disabled={!demo && t.status !== 'CONFIRMED'} title={!demo && t.status !== 'CONFIRMED' ? '확정 상태 테이블만 수정할 수 있어요' : undefined} onClick={() => openEdit('split')}>
          테이블 분리
        </button>
        <button className="adm-outline" disabled={!demo && t.status !== 'CONFIRMED'} title={!demo && t.status !== 'CONFIRMED' ? '확정 상태 테이블만 수정할 수 있어요' : undefined} onClick={() => openEdit('merge')}>
          테이블 병합
        </button>
        <button className="adm-outline" disabled={!demo && t.status !== 'CONFIRMED'} title={!demo && t.status !== 'CONFIRMED' ? '확정 상태 테이블만 수정할 수 있어요' : undefined} onClick={() => openEdit('dissolve')}>
          테이블 해제
        </button>
      </div>

      <div className="adm-td-cols">
        <div className="adm-td-left">
          <section className="adm-panel" style={{ padding: '20px 24px', gap: 12 }}>
            <div className="adm-panel-head">
              <h2 className="t-h2">매칭 근거</h2>
              <b className="t-h2">{total}점</b>
            </div>
            <div className="adm-basis">
              {basis.map(([k, v]) => (
                <div key={k}>
                  <span className="k">
                    <i />
                    {k}
                  </span>
                  <b>{v}</b>
                </div>
              ))}
            </div>
            <div className="adm-score-parts">
              <span>조건별 점수</span>
              {scoreParts.map(([k, l]) => (
                <span key={k} className="v">
                  {l} {part(k)}
                </span>
              ))}
            </div>
          </section>

          <div className="adm-mt-section" style={{ gap: 10 }}>
            <h2 className="t-h2">참가자 구성</h2>
            <DataTable
              rows={t.members}
              rowKey={(x) => x.applicationId}
              columns={[
                { key: 'name', label: '참가자', width: 90, render: (x) => x.name, sort: (x) => x.name },
                { key: 'birth', label: '출생', width: 64, render: (x) => 2026 - x.age, sort: (x) => -x.age },
                { key: 'g', label: '성별', width: 48, render: (x) => (isFemale(x.gender) ? '여' : '남'), sort: (x) => x.gender },
                { key: 'mbti', label: 'MBTI', width: 50, render: (x) => x.mbti, sort: (x) => x.mbti },
                { key: 'int', label: '관심사', width: 120, render: (x) => <span className="ellipsis" style={{ display: 'block', maxWidth: 220 }}>{x.interests.map((s) => s.split(/[//(]/)[0].trim()).join('/')}</span>, sort: (x) => x.interests.join() },
                {
                  key: 'att',
                  label: '참석',
                  render: (x) => {
                    const [l, tone] = ATTEND[x.attendanceStatus] ?? [x.attendanceStatus, '중립']
                    return (
                      <Badge tone={tone} dot={false}>
                        {l}
                      </Badge>
                    )
                  },
                  sort: (x) => x.attendanceStatus,
                },
              ]}
            />
          </div>

          <section className="adm-card16">
            <div className="adm-card16-head">
              <Icon name="utensils" size={18} />
              <h3>자동 배정 식당</h3>
              <button className="adm-outline card" onClick={() => setVenueOpen(true)}>
                다른 식당으로 변경
              </button>
            </div>
            {t.venue ? (
              <Info
                rows={[
                  ['식당', demo ? DEMO_TEXT.venueName : `${t.venue.name}${t.venue.priceRange ? `, ${t.venue.priceRange}` : ''}`],
                  ['주소', t.venue.address],
                  ['예약명', t.venue.reservationName ?? '-'],
                  ['알레르기', demo ? DEMO_TEXT.allergy : allergies.length ? `${allergies.map((x) => x.foodRestrictions).join(', ')} (${allergies.length}명), 식당 전달 완료` : '없음'],
                  ['배정', demo ? DEMO_TEXT.assign : `${at(t.confirmedAt)} 자동 배정`],
                ]}
              />
            ) : (
              <p className="adm-warn-note">식당이 아직 배정되지 않았어요. 다른 식당으로 변경에서 직접 배정해 주세요.</p>
            )}
          </section>

          <section className="adm-card16">
            <div className="adm-card16-head">
              <Icon name="chat" size={18} />
              <h3>변경 이력</h3>
            </div>
            <DataTable
              rows={history}
              rowKey={(r) => r.join('|')}
              empty="변경 이력이 없어요"
              columns={[
                { key: 'at', label: '시각', width: 110, render: (r) => r[0], sort: (r) => r[0] },
                { key: 'who', label: '주체', width: 70, render: (r) => r[1], sort: (r) => r[1] },
                { key: 'what', label: '내용', render: (r) => r[2] },
              ]}
            />
          </section>
        </div>

        <section className="adm-card16 adm-td-right">
          <div className="adm-card16-head">
            <Icon name="chat" size={18} />
            <h3>앱 단체방</h3>
            <Badge tone="확정" dot={false}>
              {t.chatRoomId ? '활성' : '미생성'}
            </Badge>
          </div>
          <div className="stack" style={{ gap: 10 }}>
            <Info
              rows={[
                ['방 ID', demo ? DEMO_TEXT.roomId : t.chatRoomId ? tableLabel(t.tableCode, t.slotLabel).replace(/^T-/, 'CR-') : '-'],
                ['초대', `${t.memberCount} / ${t.memberCount} 자동 초대, 식사 전 퇴장 불가`],
                ['메시지', `${t.chatMessages}개, 신고 ${demo ? 0 : reports.data.filter((r) => r.tableId === t.tableId).length}`],
              ]}
            />
          </div>
          <p className="t-label-m">자동 발송</p>
          {posts.map(([title, sub, done]) => (
            <div key={title} className="adm-post">
              <i className={done ? 'done' : undefined} />
              <div>
                <span>{title}</span>
                <small>{sub}</small>
              </div>
            </div>
          ))}
          <p className="adm-retry-note">실패 시 재시도 3회 → 예외</p>
        </section>
      </div>

      <Dialog open={venueOpen} onClose={() => setVenueOpen(false)}>
        <h3 className="dialog-title">다른 식당으로 변경</h3>
        <p className="t-body-s muted">{region} 식당 중에서 골라주세요. 바꾸면 단체방에 장소 안내가 자동으로 올라가요.</p>
        <div className="stack" style={{ gap: 6, width: '100%' }}>
          {regionVenues.map((v) => (
            <button key={v.id} className="adm-pill" aria-pressed={venueId === v.id} style={{ textAlign: 'left' }} onClick={() => setVenueId(v.id)}>
              {v.name} <span className="t-caption muted">{v.priceGuide}</span>
            </button>
          ))}
          {regionVenues.length === 0 && <p className="t-body-s muted">이 지역에 운영 중인 식당이 없어요</p>}
        </div>
        <div className="row" style={{ gap: 8 }}>
          <Button variant="secondary" size="S" onClick={() => setVenueOpen(false)}>
            취소
          </Button>
          <Button size="S" disabled={!venueId} onClick={assign}>
            변경
          </Button>
        </div>
      </Dialog>
      <Dialog open={edit !== null} onClose={() => setEdit(null)}>
        <h3 className="dialog-title">
          {edit === 'move' ? '참가자 이동' : edit === 'split' ? '테이블 분리' : edit === 'merge' ? '테이블 병합' : '테이블 해제'}
        </h3>
        <p className="t-body-s muted" style={{ textAlign: 'center' }}>
          {edit === 'move' && '같은 회차의 다른 확정 테이블로 옮겨요. 두 단체방에 안내가 자동으로 올라가요.'}
          {edit === 'split' && '고른 참가자로 새 테이블을 만들어요. 식당과 단체방이 자동으로 배정돼요.'}
          {edit === 'merge' && '이 테이블 전원을 고른 테이블로 옮겨요. 이 테이블의 단체방은 종료돼요.'}
          {edit === 'dissolve' && '남은 참가자는 같은 회차 다른 테이블로 재배치하고, 안 되면 대체 일정을 안내해요.'}
        </p>
        {(edit === 'move' || edit === 'split') && (
          <div className="stack" style={{ gap: 6, width: '100%' }}>
            <small className="t-caption muted">옮길 참가자</small>
            {t.members.map((m) => (
              <button
                key={m.applicationId}
                className="adm-pill"
                aria-pressed={picked.includes(m.applicationId)}
                style={{ textAlign: 'left' }}
                onClick={() => setPicked((p) => (p.includes(m.applicationId) ? p.filter((x) => x !== m.applicationId) : [...p, m.applicationId]))}
              >
                {m.name} <span className="t-caption muted">{2026 - m.age}년생, {m.mbti}</span>
              </button>
            ))}
          </div>
        )}
        {(edit === 'move' || edit === 'merge') && (
          <div className="stack" style={{ gap: 6, width: '100%' }}>
            <small className="t-caption muted">옮길 테이블</small>
            {others.length === 0 && <p className="t-body-s muted">같은 회차에 다른 확정 테이블이 없어요</p>}
            {others.map((o) => (
              <button key={o.tableId} className="adm-pill" aria-pressed={target === o.tableId} style={{ textAlign: 'left' }} onClick={() => setTarget(o.tableId)}>
                테이블 {o.tableCode} <span className="t-caption muted">{o.memberCount}명</span>
              </button>
            ))}
          </div>
        )}
        <input className="adm-input" style={{ width: '100%' }} placeholder={edit === 'dissolve' ? '해제 사유 (필수)' : '사유 (이력에 남아요)'} value={reason} onChange={(e) => setReason(e.target.value)} />
        <div className="row" style={{ gap: 8 }}>
          <Button variant="secondary" size="S" onClick={() => setEdit(null)}>
            취소
          </Button>
          <Button size="S" variant={edit === 'dissolve' ? 'danger' : 'primary'} disabled={!editReady || editing} onClick={runEdit}>
            {edit === 'dissolve' ? '해제' : '적용'}
          </Button>
        </div>
      </Dialog>
      {toast.node}
    </AdminLayout>
  )
}
