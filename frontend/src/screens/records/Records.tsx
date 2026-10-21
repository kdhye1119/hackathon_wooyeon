import { useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { AppBar, AppHeader, Badge, Body, Button, Chip, Dialog, Footer, Screen, Sheet, TabBar, useToast } from '../../components/ui'
import { Icon } from '../../components/Icon'
import { get, post } from '../../lib/api'
import { fmt, useApi } from '../../lib/useApi'
import type { ApplicationView } from '../../lib/types'
import { demoApplications, demoSlots } from '../../lib/demo'
import { deleteDiary, demoDiary, loadDiaries, saveDiary, shrinkImage, type Diary, type Mood } from '../../lib/diary'
import starFilled from '../../assets/icons/star-filled.svg'
import starEmpty from '../../assets/icons/star-empty.svg'
import faceFlutter from '../../assets/diary/face-flutter.svg?raw'
import faceCalm from '../../assets/diary/face-calm.svg?raw'
import faceJoy from '../../assets/diary/face-joy.svg?raw'
import faceSad from '../../assets/diary/face-sad.svg?raw'
import miniTable from '../../assets/diary/mini-table.svg'
import remember from '../../assets/diary/remember.svg'
import rememberOff from '../../assets/diary/remember-off.svg'
import calMark from '../../assets/diary/cal-mark.svg'
import reviewDot from '../../assets/diary/review-dot.svg'
import laurel from '../../assets/brand/laurel.svg'
import emptyMark from '../../assets/diary/empty-mark.svg'
import './records.css'

const DAY = ['일', '월', '화', '수', '목', '금', '토']
const regionLabel = (code: string) => demoSlots.regions.find((r) => r.code === code)?.label ?? code
const slotOf = (a: ApplicationView) => a.slots.find((s) => s.assigned) ?? a.slots[0]

// 얼굴 SVG 의 비선택 투명도는 CSS 로 제어
const strip = (svg: string) => svg.replace(/ opacity="[\d.]+"/, '').replace(/ style="display: block;"/, '').replace(/ width="48" height="48"/, ' width="100%" height="100%"')
const FACES: [Mood, string, string][] = [
  ['flutter', '설렘', strip(faceFlutter)],
  ['calm', '편안함', strip(faceCalm)],
  ['joy', '즐거움', strip(faceJoy)],
  ['sad', '아쉬움', strip(faceSad)],
]
const PHOTO_GRADIENTS = [
  'linear-gradient(137deg, #f2c46a 0%, rgba(181,83,47,.85) 39%, rgba(76,69,65,.9) 71%)',
  'linear-gradient(136deg, #aeac78 0%, rgba(242,196,106,.9) 71%)',
  'linear-gradient(136deg, #b5532f 0%, rgba(76,69,65,.9) 71%)',
  'linear-gradient(136deg, #f2c46a 0%, rgba(174,172,120,.9) 71%)',
]
const photoBg = (src: string | undefined, i: number) => (src ? `center / cover no-repeat url(${src})` : PHOTO_GRADIENTS[i % PHOTO_GRADIENTS.length])

type Mate = { userId: number; displayName: string; mbti: string | null }
const demoMates: Mate[] = [
  { userId: 11, displayName: '유진', mbti: null },
  { userId: 12, displayName: '태오', mbti: null },
  { userId: 13, displayName: '소연', mbti: null },
  { userId: 14, displayName: '준호', mbti: null },
]

/** 후기 / 일기 탭 */
function ReviewTabs({ tab, onTab }: { tab: 'review' | 'diary'; onTab: (t: 'review' | 'diary') => void }) {
  return (
    <div className="review-tabs" role="tablist">
      <button role="tab" aria-selected={tab === 'review'} onClick={() => onTab('review')}>
        후기
      </button>
      <button role="tab" aria-selected={tab === 'diary'} onClick={() => onTab('diary')}>
        일기
      </button>
    </div>
  )
}

/** /review/:id — 5-1 후기 / 5-2 일기 */
export function ReviewScreen() {
  const { id = 'latest' } = useParams()
  const [params, setParams] = useSearchParams()
  const tab = params.get('tab') === 'diary' ? 'diary' : 'review'
  const apps = useApi(() => get<ApplicationView[]>('/api/applications'), demoApplications)
  const app =
    id === 'latest'
      ? (apps.data.find((a) => a.nextActions.includes('FEEDBACK')) ?? apps.data.find((a) => a.attendanceStatus === 'ATTENDED') ?? demoApplications[2])
      : (apps.data.find((a) => String(a.id) === id) ?? demoApplications[2])

  return (
    <Screen>
      <AppBar title="후기" />
      <ReviewTabs tab={tab} onTab={(t) => setParams(t === 'diary' ? { tab: 'diary' } : {}, { replace: true })} />
      {tab === 'review' ? <ReviewForm app={app} demo={apps.demo} /> : <DiaryEditor app={app} />}
    </Screen>
  )
}

function Stars({ value, onChange, label }: { value: number; onChange: (v: number) => void; label: string }) {
  return (
    <div className="star-row">
      <span className="t-body-m">{label}</span>
      <div className="row" style={{ gap: 4 }} role="radiogroup" aria-label={label}>
        {[1, 2, 3, 4, 5].map((n) => (
          <button key={n} role="radio" aria-checked={value === n} aria-label={`${n}점`} onClick={() => onChange(n)}>
            <img src={n <= value ? starFilled : starEmpty} width={24} height={24} alt="" />
          </button>
        ))}
      </div>
    </div>
  )
}

/** 5-1 후기 */
function ReviewForm({ app, demo }: { app: ApplicationView; demo: boolean }) {
  const navigate = useNavigate()
  const toast = useToast()
  const mates = useApi(() => get<Mate[]>(`/api/applications/${app.id}/tablemates`), demoMates, [app.id])
  const [rating, setRating] = useState({ table: 5, talk: 4, venue: 4 })
  const [rejoin, setRejoin] = useState<5 | 3 | 1>(5)
  const [pref, setPref] = useState<Record<number, 'again' | 'avoid' | undefined>>({})
  // 시안 예시: 유진·태오 재매칭
  useEffect(() => {
    if (demo) setPref({ 11: 'again', 12: 'again' })
  }, [demo])
  const [publicReview, setPublicReview] = useState(false)
  const [nps, setNps] = useState(9)
  const [comment, setComment] = useState('')
  const [report, setReport] = useState(false)
  const [busy, setBusy] = useState(false)
  const table = app.table
  const start = slotOf(app)?.startAt ?? app.submittedAt

  const submit = async () => {
    setBusy(true)
    try {
      if (!demo)
        await post(`/api/applications/${app.id}/feedback`, {
          tableRating: rating.table,
          conversationRating: rating.talk,
          venueRating: rating.venue,
          rejoinIntent: rejoin,
          recommendScore: nps,
          comment,
          // '후기로 공개'면 홈 화면 참가자 후기에 이름 없이 보인다
          publicReview,
          wantAgainUserIds: Object.entries(pref).filter(([, v]) => v === 'again').map(([k]) => Number(k)),
          avoidUserIds: Object.entries(pref).filter(([, v]) => v === 'avoid').map(([k]) => Number(k)),
        })
      toast.show('후기를 보냈어요. 다음 매칭에 반영할게요')
      window.setTimeout(() => navigate(`/review/${app.id}?tab=diary`, { replace: true }), 900)
    } catch (e) {
      toast.show(e instanceof Error ? e.message : '제출에 실패했어요')
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      <Body className="review-body">
        <div className="checkin-card">
          <span className="checkin-badge">
            <Icon name="check" size={22} />
          </span>
          <div className="stack" style={{ flex: 1, minWidth: 0, gap: 2 }}>
            <span className="t-h3">{app.attendanceStatus === 'ATTENDED' ? '체크인 완료' : '참석 기록'}</span>
            <span className="t-body-s c-sage">
              {fmt.md(start)} ({DAY[new Date(start).getDay()]}) {fmt.time(start)}, {table?.venueName}
            </span>
          </div>
          <Badge dot={false}>{app.attendanceStatus === 'NO_SHOW' ? '불참' : '참석'}</Badge>
        </div>

        <section className="review-section" style={{ gap: 14 }}>
          <h3 className="t-h3">만족도</h3>
          <Stars label="테이블 분위기" value={rating.table} onChange={(v) => setRating({ ...rating, table: v })} />
          <Stars label="대화" value={rating.talk} onChange={(v) => setRating({ ...rating, talk: v })} />
          <Stars label="식당" value={rating.venue} onChange={(v) => setRating({ ...rating, venue: v })} />
        </section>

        <section className="review-section">
          <h3 className="t-h3">재참여 의향</h3>
          <div className="rejoin">
            {(
              [
                [5, '있음'],
                [3, '보통'],
                [1, '없음'],
              ] as const
            ).map(([v, l]) => (
              <button key={v} aria-pressed={rejoin === v} onClick={() => setRejoin(v)}>
                {l}
              </button>
            ))}
          </div>
        </section>

        <section className="pref-card">
          <div className="row" style={{ gap: 6 }}>
            <Icon name="shield" size={18} color="var(--terracotta)" />
            <h3 className="t-h3">사람별 선호</h3>
          </div>
          {mates.data.map((m) => (
            <div key={m.userId} className="row" style={{ gap: 8 }}>
              <span className="chat-avatar">{m.displayName.slice(0, 1)}</span>
              <span className="t-body-s" style={{ flex: 1 }}>
                {m.displayName}
              </span>
              <Chip selected={pref[m.userId] === 'again'} onClick={() => setPref({ ...pref, [m.userId]: pref[m.userId] === 'again' ? undefined : 'again' })}>
                재매칭
              </Chip>
              <Chip selected={pref[m.userId] === 'avoid'} onClick={() => setPref({ ...pref, [m.userId]: pref[m.userId] === 'avoid' ? undefined : 'avoid' })}>
                제외
              </Chip>
            </div>
          ))}
        </section>

        <button className="report-row" onClick={() => setReport(true)}>
          <Icon name="flag" size={18} color="var(--paper)" />
          <span className="label">신고/차단</span>
          <Icon name="chevron-right" size={18} color="var(--paper)" />
        </button>

        <section className="review-section">
          <div className="divider-h" style={{ opacity: 1 }} />
          <div className="review-tabs inline" role="tablist">
            <button role="tab" aria-selected={publicReview} onClick={() => setPublicReview(true)}>
              후기로 공개
            </button>
            <button role="tab" aria-selected={!publicReview} onClick={() => setPublicReview(false)}>
              운영진만 보기
            </button>
          </div>
          <p className="t-body-s muted">공개하면 추천 의향과 한마디가 이름 없이 홈 화면 후기에 보여요</p>
          {/* Figma: 12 간격 + 6 여백 프레임 + 12 간격 */}
          <div className="row" style={{ marginTop: 18, height: 32 }}>
            <h3 className="t-h3" style={{ flex: 1 }}>
              추천 의향
            </h3>
            <b className="nps-value">{nps}</b>
            <span className="t-label-m muted">&nbsp;/ 10</span>
          </div>
          <input
            className="nps-slider"
            type="range"
            min={0}
            max={10}
            step={1}
            value={nps}
            onChange={(e) => setNps(Number(e.target.value))}
            style={{ ['--p' as string]: `${nps * 10}%` }}
            aria-label="추천 의향"
          />
          <div className="row t-label-s muted">
            <span style={{ flex: 1 }}>0 (추천 안 함)</span>
            <span>10 (적극 추천)</span>
          </div>
          <h3 className="t-h3" style={{ marginTop: 18 }}>
            한마디
          </h3>
          <textarea className="request-text" style={{ height: 100 }} value={comment} onChange={(e) => setComment(e.target.value)} placeholder="자유롭게 남겨주세요 (선택)" />
        </section>
      </Body>
      <Footer>
        <p className="t-caption muted" style={{ textAlign: 'center' }}>
          별점과 사람별 선호는 공개되지 않고, 다음 매칭에만 활용돼요.
        </p>
        <Button disabled={busy || app.feedbackSubmitted} onClick={submit}>
          {app.feedbackSubmitted ? '이미 제출했어요' : '제출'}
        </Button>
      </Footer>
      <ReportSheet open={report} onClose={() => setReport(false)} mates={mates.data} tableId={table?.tableId} demo={demo} onDone={(m) => toast.show(m)} />
      {toast.node}
    </>
  )
}

const REPORT_CATEGORIES = ['불쾌한 언행', '무단 불참·지각', '연락 강요', '기타']

function ReportSheet({ open, onClose, mates, tableId, demo, onDone }: { open: boolean; onClose: () => void; mates: Mate[]; tableId?: number; demo: boolean; onDone: (msg: string) => void }) {
  const [target, setTarget] = useState<number | null>(null)
  const [category, setCategory] = useState(REPORT_CATEGORIES[0])
  const [detail, setDetail] = useState('')
  const send = async (kind: 'report' | 'block') => {
    if (!target) return
    try {
      if (!demo) {
        if (kind === 'report') await post('/api/reports', { reportedUserId: target, tableId, category, detail })
        else await post(`/api/blocks/${target}`)
      }
      onClose()
      onDone(kind === 'report' ? '신고를 접수했어요. 상대에게는 알리지 않아요' : '차단했어요. 다시 같은 테이블에 배정되지 않아요')
    } catch (e) {
      onDone(e instanceof Error ? e.message : '요청에 실패했어요')
    }
  }
  return (
    <Sheet open={open} onClose={onClose}>
      <h2 className="t-h2">신고/차단</h2>
      <div className="field">
        <span className="field-label">누구인가요?</span>
        <div className="chips">
          {mates.map((m) => (
            <Chip key={m.userId} selected={target === m.userId} onClick={() => setTarget(m.userId)}>
              {m.displayName}
            </Chip>
          ))}
        </div>
      </div>
      <div className="field">
        <span className="field-label">사유</span>
        <div className="chips">
          {REPORT_CATEGORIES.map((c) => (
            <Chip key={c} selected={category === c} onClick={() => setCategory(c)}>
              {c}
            </Chip>
          ))}
        </div>
      </div>
      <textarea className="request-text" value={detail} onChange={(e) => setDetail(e.target.value)} placeholder="상황을 알려주세요 (선택)" rows={3} />
      <p className="row t-body-s muted" style={{ gap: 6, justifyContent: 'center' }}>
        <Icon name="shield" size={14} />
        상대에게 알리지 않아요
      </p>
      <div className="row" style={{ gap: 10 }}>
        <Button variant="secondary" disabled={!target} onClick={() => send('block')}>
          차단만
        </Button>
        <Button variant="danger" disabled={!target} onClick={() => send('report')}>
          신고하기
        </Button>
      </div>
    </Sheet>
  )
}

/** 5명일 때 Figma 73:490 좌석 좌표 (아바타 왼쪽 위, 12시부터 시계방향) */
const MINI_SEATS_5 = [
  [154, 7],
  [261, 67],
  [220, 147],
  [88, 147],
  [47, 67],
]

function MiniTable({ me = '나', people, remembered, onToggle }: { me?: string; people: string[]; remembered: string[]; onToggle?: (n: string) => void }) {
  const all = [me, ...people]
  const cx = 171
  const cy = 106
  const r = 82
  return (
    <div className="mini-table">
      <img src={miniTable} width={62} height={62} alt="" style={{ position: 'absolute', left: cx - 31, top: cy - 31 }} />
      {all.map((n, i) => {
        const a = -Math.PI / 2 + (i * 2 * Math.PI) / all.length
        const on = remembered.includes(n)
        return (
          <button
            key={n + i}
            className="mini-seat"
            style={all.length === 5 ? { left: MINI_SEATS_5[i][0] - 8, top: MINI_SEATS_5[i][1] } : { left: cx + r * Math.cos(a) - 25, top: cy + r * Math.sin(a) - 23 }}
            onClick={() => i > 0 && onToggle?.(n)}
            aria-pressed={i > 0 ? on : undefined}
            aria-label={i > 0 ? `${n} 다시 만나고 싶어요` : '나'}
          >
            <span className={i === 0 ? 'mini-avatar me' : 'mini-avatar'}>
              {(i === 0 ? '서' : n).slice(0, 1)}
              {i > 0 && <img src={on ? remember : rememberOff} width={14} height={13} alt="" />}
            </span>
            <span className={i === 0 || on ? 'mini-name' : 'mini-name muted'}>{n}</span>
          </button>
        )
      })}
    </div>
  )
}

/** 5-2 일기 */
function DiaryEditor({ app }: { app: ApplicationView }) {
  const navigate = useNavigate()
  const toast = useToast()
  const fileRef = useRef<HTMLInputElement>(null)
  const start = slotOf(app)?.startAt ?? app.submittedAt
  const saved = loadDiaries()[app.id]
  const [d, setD] = useState<Diary>(
    () =>
      saved ?? {
        ...demoDiary,
        appId: app.id,
        date: start,
        venue: app.table?.venueName ?? '',
        region: regionLabel(slotOf(app)?.region ?? ''),
        photos: [],
        text: '',
        moods: [],
        remember: [],
        caption: '우연히, 다섯 사람',
      },
  )
  const set = (p: Partial<Diary>) => setD((prev) => ({ ...prev, ...p }))
  const holdTimer = useRef<number | undefined>(undefined)
  const date = new Date(d.date)
  const slots = Math.max(3, d.photos.length)

  const addPhotos = async (files: FileList | null) => {
    if (!files) return
    try {
      const list = await Promise.all([...files].slice(0, 6 - d.photos.length).map((f) => shrinkImage(f)))
      set({ photos: [...d.photos, ...list] })
    } catch (e) {
      toast.show(e instanceof Error ? e.message : '사진을 불러오지 못했어요')
    }
  }

  return (
    <>
      <Body className="diary-body">
        <div className="diary-head">
          <h2 className="diary-date">
            {date.getMonth() + 1}월 {date.getDate()}일
          </h2>
          <p className="diary-sub">
            {DAY[date.getDay()]}요일 저녁/{d.venue}
          </p>
          <span className="diary-rule">
            <span />
            <i />
            <span />
          </span>
        </div>

        <div className="polaroid-stage">
          <div className="polaroid">
            <div className="polaroid-photo" style={{ background: photoBg(d.photos[d.cover], 0) }}>
              <span className="cover-tag">대표</span>
            </div>
            <input className="polaroid-caption" value={d.caption} onChange={(e) => set({ caption: e.target.value })} aria-label="사진 제목" maxLength={20} />
          </div>
          <span className="tape" />
        </div>

        <div className="photo-strip">
          {Array.from({ length: Math.min(slots, 5) }).map((_, i) => (
            <button
              key={i}
              className="mini-polaroid"
              style={{ transform: `rotate(${[-3, 4, -2, 3, -1][i]}deg)` }}
              onPointerDown={() => {
                holdTimer.current = window.setTimeout(() => {
                  if (d.photos[i]) {
                    set({ cover: i })
                    toast.show('대표 사진으로 지정했어요')
                  }
                }, 500)
              }}
              onPointerUp={() => window.clearTimeout(holdTimer.current)}
              onPointerLeave={() => window.clearTimeout(holdTimer.current)}
              aria-label={`사진 ${i + 1}`}
            >
              <span style={{ background: photoBg(d.photos[i], i + 1) }} />
            </button>
          ))}
          {d.photos.length < 6 && (
            <button className="add-photo" onClick={() => fileRef.current?.click()} aria-label="사진 추가">
              <Icon name="plus" />
            </button>
          )}
          <input ref={fileRef} type="file" accept="image/*" multiple hidden onChange={(e) => addPhotos(e.target.files)} />
        </div>
        <p className="diary-hint">사진을 길게 누르면 대표로 지정</p>

        <div className="stack" style={{ alignItems: 'center', gap: 10 }}>
          <p className="diary-hint" style={{ fontSize: 13, letterSpacing: 0.52 }}>
            그날의 기분
          </p>
          <div className="moods">
            {FACES.map(([code, label, svg]) => {
              const on = d.moods.includes(code)
              return (
                <button key={code} className={on ? 'mood on' : 'mood'} aria-pressed={on} onClick={() => set({ moods: on ? d.moods.filter((m) => m !== code) : [...d.moods, code] })}>
                  <span className="mood-face" dangerouslySetInnerHTML={{ __html: svg }} />
                  <span className="mood-label">{label}</span>
                </button>
              )
            })}
          </div>
        </div>

        <div className="journal">
          <textarea value={d.text} onChange={(e) => set({ text: e.target.value })} placeholder="오늘 저녁을 짧게 남겨보세요" aria-label="일기" maxLength={300} />
        </div>

        <div className="stack" style={{ alignItems: 'center', gap: 12, width: '100%' }}>
          <p className="diary-hint" style={{ fontSize: 13 }}>
            함께한 사람
          </p>
          <MiniTable
            people={d.people}
            remembered={d.remember}
            onToggle={(n) => set({ remember: d.remember.includes(n) ? d.remember.filter((x) => x !== n) : [...d.remember, n] })}
          />
        </div>
        <img src={laurel} width={89.6} height={30.8} alt="" />
        <p className="row diary-hint" style={{ gap: 6 }}>
          <Icon name="shield" size={14} />
          나만 볼 수 있는 기록
        </p>
      </Body>
      <Footer>
        <Button
          onClick={() => {
            if (!saveDiary(d)) return toast.show('저장 공간이 부족해요. 사진 수를 줄여주세요')
            toast.show('일기를 간직했어요')
            window.setTimeout(() => navigate('/calendar'), 700)
          }}
        >
          간직하기
        </Button>
      </Footer>
      {toast.node}
    </>
  )
}

type Cell = { day: number; iso: string; kind: 'applied' | 'matched' | 'logo' | 'photo' | 'today' | 'plain'; app?: ApplicationView; diary?: Diary; needsReview?: boolean }

/** 5-3 캘린더 (+ 5-4 일기 보기, 5-5 기록 없음, 5-6 삭제 확인) */
export function CalendarScreen() {
  const navigate = useNavigate()
  const apps = useApi(() => get<ApplicationView[]>('/api/applications'), demoApplications)
  const [month, setMonth] = useState(() => {
    const n = new Date()
    return new Date(n.getFullYear(), n.getMonth(), 1)
  })
  const [diaries, setDiaries] = useState(loadDiaries)
  const [openDay, setOpenDay] = useState<Cell | null>(null)
  const [confirmDelete, setConfirmDelete] = useState(false)

  // 데모: Figma 의 10.15 일기를 보여준다
  const allDiaries = useMemo(() => (apps.demo && !Object.keys(diaries).length ? { [demoDiary.appId]: demoDiary } : diaries), [apps.demo, diaries])

  const cells = useMemo(() => {
    const y = month.getFullYear()
    const m = month.getMonth()
    const first = new Date(y, m, 1).getDay()
    const days = new Date(y, m + 1, 0).getDate()
    const out: (Cell | null)[] = Array.from({ length: first }, () => null)
    for (let day = 1; day <= days; day++) {
      const iso = `${y}-${String(m + 1).padStart(2, '0')}-${String(day).padStart(2, '0')}`
      const app = apps.data.find((a) => a.appStatus !== 'CANCELED' && slotOf(a)?.startAt.startsWith(iso))
      const diary = Object.values(allDiaries).find((dd) => dd.date.startsWith(iso))
      const past = new Date(`${iso}T23:59:59`).getTime() < Date.now()
      let kind: Cell['kind'] = 'plain'
      if (diary) kind = 'photo'
      else if (app && past) kind = 'logo'
      else if (app?.matchStatus === 'CONFIRMED') kind = 'matched'
      else if (app) kind = 'applied'
      else if (new Date().toDateString() === new Date(iso).toDateString()) kind = 'today'
      out.push({ day, iso, kind, app, diary, needsReview: !!app && past && !app.feedbackSubmitted && app.nextActions.includes('FEEDBACK') })
    }
    return out
  }, [month, apps.data, allDiaries])

  const recent = Object.values(allDiaries).sort((a, b) => b.date.localeCompare(a.date))[0]
  const openCell = (c: Cell) => {
    if (c.kind === 'photo' || c.kind === 'logo') setOpenDay(c)
    else if (c.app) navigate(c.kind === 'matched' ? `/match/${c.app.id}` : `/applications/${c.app.id}`)
  }

  return (
    <Screen>
      <AppHeader />
      <Body className="cal-body">
        <div className="row" style={{ padding: '0 4px', gap: 4 }}>
          <h2 className="cal-title">
            {month.getFullYear()}. {String(month.getMonth() + 1).padStart(2, '0')}
          </h2>
          <button className="icon-btn" aria-label="이전 달" onClick={() => setMonth(new Date(month.getFullYear(), month.getMonth() - 1, 1))}>
            <Icon name="chevron-left" />
          </button>
          <button className="icon-btn" aria-label="다음 달" onClick={() => setMonth(new Date(month.getFullYear(), month.getMonth() + 1, 1))}>
            <Icon name="chevron-right" />
          </button>
        </div>
        <div className="cal-grid">
          {DAY.map((w) => (
            <span key={w} className={w === '목' ? 'cal-week thu' : 'cal-week'}>
              {w}
            </span>
          ))}
          {cells.map((c, i) =>
            !c ? (
              <span key={`e${i}`} />
            ) : (
              <button key={c.iso} className="cal-cell" onClick={() => openCell(c)} disabled={c.kind === 'plain'} aria-label={`${c.day}일`}>
                {c.kind === 'plain' && <span className="cal-day">{c.day}</span>}
                {c.kind === 'today' && <span className="cal-day today">{c.day}</span>}
                {c.kind === 'applied' && (
                  <span className="cal-tile applied">
                    <b>{c.day}</b>
                    <small>{fmt.time(slotOf(c.app!)!.startAt)}</small>
                    <em>{c.app!.matchStatus === 'NO_MATCH' ? '매칭 실패' : '매칭 대기'}</em>
                  </span>
                )}
                {c.kind === 'matched' && (
                  <span className="cal-tile matched">
                    <b>{c.day}</b>
                    <small>{fmt.time(slotOf(c.app!)!.startAt)}</small>
                    <em>{regionLabel(slotOf(c.app!)!.region).split('/')[0]}</em>
                  </span>
                )}
                {c.kind === 'logo' && (
                  <span className="cal-tile logo">
                    <b>{c.day}</b>
                    <img src={calMark} width={26} height={26} alt="" />
                    {c.needsReview && <img className="review-dot" src={reviewDot} width={10} height={10} alt="후기 미작성" />}
                  </span>
                )}
                {c.kind === 'photo' && (
                  <span className="cal-tile photo" style={{ background: photoBg(c.diary!.photos[c.diary!.cover], 0) }}>
                    <b>{c.day}</b>
                  </span>
                )}
              </button>
            ),
          )}
        </div>
        <div className="cal-legend">
          <span>
            <i style={{ background: 'var(--mustard)' }} />
            신청 완료
          </span>
          <span>
            <i style={{ background: 'var(--sage)' }} />
            매칭 완료
          </span>
          <span>
            <i className="dot" />
            후기 미작성
          </span>
        </div>
        {recent && (
          <button className="recent-diary" onClick={() => setOpenDay({ day: new Date(recent.date).getDate(), iso: recent.date.slice(0, 10), kind: 'photo', diary: recent })}>
            <span className="recent-thumb" style={{ background: photoBg(recent.photos[recent.cover], 0) }} />
            <span className="stack" style={{ flex: 1, minWidth: 0, gap: 4 }}>
              <span className="t-label-m">
                {fmt.md(recent.date)} ({DAY[new Date(recent.date).getDay()]}), {recent.venue}
              </span>
              <span className="recent-text">{recent.text}</span>
            </span>
            <Icon name="chevron-right" />
          </button>
        )}
      </Body>
      <TabBar />

      <Sheet open={!!openDay} onClose={() => setOpenDay(null)} className="sheet-center day-sheet">
        {openDay?.diary ? (
          <DiaryView diary={openDay.diary} onDelete={() => setConfirmDelete(true)} onEdit={() => navigate(`/review/${openDay.diary!.appId}?tab=diary`)} onClose={() => setOpenDay(null)} />
        ) : openDay?.app ? (
          <EmptyDay app={openDay.app} />
        ) : null}
      </Sheet>
      <Dialog open={confirmDelete} onClose={() => setConfirmDelete(false)}>
        <Icon name="trash" size={28} />
        <h3 className="dialog-title">이 날의 기록을 지울까요?</h3>
        <p className="t-body-s muted" style={{ textAlign: 'center' }}>
          사진과 기록이 모두 사라지고
          <br />
          되돌릴 수 없어요
        </p>
        <div className="row" style={{ gap: 10, paddingTop: 10, width: '100%' }}>
          <Button variant="secondary" onClick={() => setConfirmDelete(false)}>
            취소
          </Button>
          <Button
            variant="danger"
            onClick={() => {
              if (openDay?.diary) deleteDiary(openDay.diary.appId)
              setDiaries(loadDiaries())
              setConfirmDelete(false)
              setOpenDay(null)
            }}
          >
            지우기
          </Button>
        </div>
      </Dialog>
    </Screen>
  )
}

/** 5-4 캘린더, 일기 보기 */
function DiaryView({ diary, onDelete, onEdit, onClose }: { diary: Diary; onDelete: () => void; onEdit: () => void; onClose: () => void }) {
  const [page, setPage] = useState(diary.cover)
  const date = new Date(diary.date)
  const photos = diary.photos.length ? diary.photos : ['', '', '', '']
  useEffect(() => setPage(diary.cover), [diary])
  const next = (page + 1) % photos.length
  return (
    <>
      <button className="day-trash" aria-label="기록 지우기" onClick={onDelete}>
        <Icon name="trash" size={22} />
      </button>
      <div className="day-head">
        <h2>
          {date.getMonth() + 1}월 {date.getDate()}일
        </h2>
        <p>
          {DAY[date.getDay()]}요일 저녁/{diary.venue}
        </p>
      </div>
      <div className="day-photos" onClick={() => setPage(next)} role="button" aria-label="다음 사진">
        {photos.length > 1 && <span className="day-photo-back" style={{ background: photoBg(photos[next], 1) }} />}
        <div className="day-polaroid">
          <span className="day-polaroid-photo" style={{ background: photoBg(photos[page], 0) }} />
          <p>{diary.caption}</p>
        </div>
      </div>
      <div className="page-dots">
        {photos.map((_, i) => (
          <i key={i} className={i === page ? 'on' : undefined} />
        ))}
      </div>
      {diary.moods.length > 0 && (
        <div className="row" style={{ gap: 14 }}>
          {FACES.filter(([c]) => diary.moods.includes(c)).map(([c, l, svg]) => (
            <span key={c} className="row" style={{ gap: 6 }}>
              <span className="mood-face small" dangerouslySetInnerHTML={{ __html: svg }} />
              <span className="mood-label">{l}</span>
            </span>
          ))}
        </div>
      )}
      {diary.text && <p className="day-text">{diary.text}</p>}
      <div className="day-people">
        {/* 다시 만나고 싶은 사람(♥)을 앞에 */}
        {[...diary.people].sort((a, b) => Number(diary.remember.includes(b)) - Number(diary.remember.includes(a))).map((p) => (
          <span key={p} className={diary.remember.includes(p) ? 'on' : undefined}>
            {diary.remember.includes(p) && <img src={remember} width={14} height={13} alt="" />}
            {p}
          </span>
        ))}
      </div>
      <div className="row" style={{ gap: 10, width: '100%' }}>
        <Button variant="secondary" onClick={onEdit}>
          수정
        </Button>
        <Button onClick={onClose}>닫기</Button>
      </div>
    </>
  )
}

/** 5-5 캘린더, 기록 없음 */
function EmptyDay({ app }: { app: ApplicationView }) {
  const navigate = useNavigate()
  const mates = useApi(() => get<Mate[]>(`/api/applications/${app.id}/tablemates`), demoMates, [app.id])
  const start = slotOf(app)!.startAt
  const date = new Date(start)
  return (
    <>
      <div className="day-head">
        <h2>
          {date.getMonth() + 1}월 {date.getDate()}일
        </h2>
        <p>
          {DAY[date.getDay()]}요일 저녁/{regionLabel(slotOf(app)!.region)}
        </p>
      </div>
      <div className="empty-polaroid">
        <img src={emptyMark} width={56} height={56} alt="" />
        <span>기록 없음</span>
      </div>
      <div className="day-people">
        {mates.data.map((m) => (
          <span key={m.userId}>{m.displayName}</span>
        ))}
      </div>
      <div className="row" style={{ gap: 10, width: '100%' }}>
        <Button variant="secondary" disabled={app.feedbackSubmitted} onClick={() => navigate(`/review/${app.id}`)}>
          {app.feedbackSubmitted ? '후기 작성 완료' : '후기 쓰기'}
        </Button>
        <Button onClick={() => navigate(`/review/${app.id}?tab=diary`)}>일기 쓰기</Button>
      </div>
    </>
  )
}
