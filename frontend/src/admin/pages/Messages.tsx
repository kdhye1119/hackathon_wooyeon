import { Fragment, useEffect, useMemo, useState } from 'react'
import { AdminLayout, DataTable, DemoNotice, Seg, useAdminApi, cx, useServiceNow } from '../AdminLayout'
import { Badge, Button, useToast } from '../../components/ui'
import { Icon } from '../../components/Icon'
import { del, get, post, put } from '../../lib/api'
import type { NotificationLog, Template } from '../types'
import { adminDemo } from '../demo'
import { INTERESTS } from '../../lib/options'

type Period = 'day' | 'week' | 'month'
const DAY = 86400000

/** Figma 템플릿 목록. code = 서버 MessageTemplate 코드 (푸시는 알림 종류, 단체방은 ROOM_*) */
type Def = { key: string; name: string; channel: string; code: string; when: string; via: string; target: string; vars: string[]; title: string; body: string }
const DEFS: Def[] = [
  { key: 'submitted', name: '신청 완료', channel: '푸시', code: 'APPLICATION_SUBMITTED', when: '신청서 제출 즉시', via: '앱 푸시', target: '신청자 본인', vars: ['slots'], title: '신청이 접수되었어요', body: '{slots} 신청이 완료되었어요. 매칭 전까지는 앱에서 신청을 취소할 수 있어요.' },
  { key: 'paid', name: '결제 완료', channel: '푸시', code: 'PAYMENT_COMPLETED', when: '결제 승인 즉시', via: '앱 푸시', target: '결제자 본인', vars: ['product', 'matchAt'], title: '결제가 완료되었어요', body: '{product} 결제가 완료되었어요. 자동 매칭은 {matchAt}에 진행되고 결과를 바로 알려드릴게요.' },
  { key: 'confirmed', name: '매칭 확정', channel: '푸시', code: 'MATCH_CONFIRMED', when: '테이블 자동 확정 즉시', via: '앱 푸시', target: '확정 테이블 전원', vars: ['slot', 'venue', 'time'], title: '테이블이 확정되었어요', body: '{slot}, {venue}, 집합 {time}. 앱 단체방에서 테이블 큐레이션과 안내를 확인해 보세요.' },
  { key: 'notice', name: '단체방 공지', channel: '단체방', code: 'ROOM_NOTICE', when: '테이블 확정, 단체방 생성 즉시', via: '앱 단체방 시스템 메시지', target: '확정 테이블 전원', vars: ['일시', '식당명', '주소', '지도', '예약명', '식사비', '집합시간', '지역', '큐레이션'], title: '테이블이 확정되었어요!', body: '' },
  { key: 'curation', name: '테이블 큐레이션', channel: '단체방', code: 'ROOM_CURATION', when: '단체방 공지의 {{큐레이션}} 자리에 들어가요', via: '앱 단체방 시스템 메시지', target: '확정 테이블 전원', vars: ['인원', '연령대', '남', '여', '공통관심사', 'MBTI', '분위기'], title: '오늘의 테이블 큐레이션', body: '' },
  { key: 'roomReminder', name: '행사 1일 전 리마인드', channel: '단체방', code: 'ROOM_REMINDER', when: '행사 1일 전 19:30', via: '앱 단체방 시스템 메시지', target: '확정 테이블 전원', vars: ['일시', '식당명', '주소', '지도', '예약명', '식사비', '집합시간'], title: '내일 우연한 식탁에서 만나요!', body: '' },
  { key: 'reminder', name: '행사 1일 전 리마인드', channel: '푸시', code: 'EVENT_REMINDER', when: '행사 1일 전 19:30', via: '앱 푸시', target: '확정 테이블 전원', vars: ['slot', 'venue', 'address', 'reservationName'], title: '내일 우연한 식탁에서 만나요', body: '{slot}, {venue} ({address}), 예약명 {reservationName}. 당일 취소 및 노쇼 시 이후 참여가 제한될 수 있어요.' },
  { key: 'topics', name: '대화 주제/아이스브레이킹', channel: '단체방', code: 'ROOM_TOPICS', when: '행사 시작 시각, 아래에 주제 목록이 붙어요', via: '앱 단체방 시스템 메시지', target: '확정 테이블 전원', vars: ['공통관심사', '대화주제', '게임'], title: '오늘의 대화 주제', body: '' },
  { key: 'failed', name: '매칭 실패/대체 일정', channel: '푸시', code: 'MATCH_FAILED', when: '자동 매칭 실패 즉시', via: '앱 푸시', target: '미배치 신청자', vars: ['reason'], title: '이번에는 테이블이 만들어지지 않았어요', body: '{reason} 다른 일정으로 이동하거나, 이용권 보관/환불 중 하나를 선택해 주세요.' },
  { key: 'refund', name: '환불 요청/완료', channel: '푸시', code: 'REFUND_COMPLETED', when: '환불 완료 즉시', via: '앱 푸시', target: '환불 신청자', vars: ['amount'], title: '환불이 완료되었어요', body: '{amount}원이 원결제수단으로 환불되었어요.' },
  { key: 'feedback', name: '피드백 요청', channel: '푸시', code: 'FEEDBACK_REQUEST', when: '행사 종료 후', via: '앱 푸시', target: '참석자 전원', vars: [], title: '오늘 식탁은 어떠셨나요?', body: '만족도와 다시 만나고 싶은 분을 남겨주시면 다음 매칭에 반영돼요. 응답은 상대에게 공개되지 않아요.' },
  { key: 'next', name: '다음 모집 추천', channel: '푸시', code: 'NEXT_RECOMMENDATION', when: '새 회차 모집 시작', via: '앱 푸시', target: '재참여 의향 응답자', vars: ['slot', 'reason'], title: '다음 식탁을 추천해요', body: '{slot} 모집이 시작됐어요. {reason}' },
]

/** 미리보기 예시 값 (T-1022-EJ-3) */
const SAMPLE: Record<string, string> = {
  일시: '10/22 (목) 19:30',
  지역: '을지로/종로',
  지도: '지도에서 보기',
  식사비: '1인 2~3만원',
  남: '2',
  여: '3',
  MBTI: 'E 2, I 3 — 이끄는 분과 들어주는 분이 고르게 섞였어요',
  분위기: '깊은 대화를 좋아함, 경청을 잘 함',
  인원: '5',
  연령대: '20대 후반 ~ 30대 초반',
  공통관심사: '전시, 여행, 맛집',
  성향구성: '먼저 말을 거는 분과 잘 들어주는 분',
  식당명: '을지 화로',
  집합시간: '19:20',
  주소: '서울 중구 을지로 OO길 12, 2층',
  예약명: '우연한 식탁 E-1022-3',
  대화주제: '최근 본 전시 중 마음에 남은 작품은?',
  게임: '두 개의 진실, 하나의 거짓',
  slots: '10/22 (목) 19:30 을지로/종로',
  slot: '10/22 (목) 19:30 을지로/종로',
  product: '1회권',
  matchAt: '10.20 24:00',
  venue: '을지 화로',
  time: '19:20',
  address: '서울 중구 을지로 OO길 12, 2층',
  reservationName: '우연한 식탁 E-1022-3',
  reason: '같은 날짜/지역에 조건을 만족하는 테이블이 없었어요.',
  amount: '8,000',
}
/** 단체방 메시지에 템플릿 앞뒤로 자동으로 붙는 부분 (백엔드 TableBriefing 과 같은 구성) */
const TOPIC_LIST = ['1. 최근 본 전시 중 마음에 남은 작품은?', '2. 다시 가고 싶은 여행지와 그곳에서의 한 장면은?', '3. 요즘 나를 가장 설레게 하는 일은 무엇인가요?', '', '🎮 아이스브레이킹', '/ 🎲 두 개의 진실, 하나의 거짓'].join('\n')

const fill = (s: string) => s.replace(/\{\{(.+?)\}\}|\{(\w+)\}/g, (m, a, b) => SAMPLE[a ?? b] ?? m)
/** 푸시 템플릿 변수는 {x}, 단체방 템플릿 변수는 {{한글}} */
const varToken = (v: string) => (/^[a-z]/.test(v) ? `{${v}}` : `{{${v}}}`)

/** 대화 주제 라이브러리 카테고리: 관심사별 + 모든 테이블 공통 + 아이스브레이킹 게임 */
/** 테이블 유형: 구성원 다수가 이 성향이면 노출 */
const TABLE_TYPES: [string, string][] = [
  ['TRAIT_DEEP_TALK', '깊은 대화 테이블'],
  ['TRAIT_HUMOR', '유머 있는 테이블'],
  ['TRAIT_ENERGETIC', '활기찬 테이블'],
  ['TRAIT_CALM', '잔잔한 테이블'],
  ['TRAIT_LISTENER', '경청하는 테이블'],
  ['TRAIT_LEADER', '이끄는 사람 많은 테이블'],
]
const TOPIC_CATS: [string, string][] = [...INTERESTS.map(([c, l]) => [c, l.replace(/ \(.*\)$/, '')] as [string, string]), ...TABLE_TYPES, ['GENERAL', '모든 테이블 공통'], ['ICEBREAKER', '아이스브레이킹 게임']]
const catLabel = (c: string) => TOPIC_CATS.find(([k]) => k === c)?.[1] ?? c
type TopicLib = { builtIn: Record<string, number>; custom: { id: number; category: string; text: string }[] }
const DEMO_LIB: TopicLib = { builtIn: { FOOD_CAFE: 2, EXHIBITION_ART: 2, TRAVEL: 2, SELF_DEVELOPMENT: 2, GENERAL: 6, ICEBREAKER: 4 }, custom: [] }

/** 발송 이력 (같은 템플릿·같은 분 발송을 한 줄로 묶음) */
type Hist = { key: string; at: string; atMs: number; name: string; to: string; channel: string; ok: number; total: number; failedIds: number[]; failedPostIds: number[]; retried: boolean }
type RoomLog = { key: string; kind: 'NOTICE' | 'REMINDER' | 'CONTENT'; roomId: number; at: string; status: 'SENT' | 'FAILED'; postId: number | null; attempts: number }
const ROOM_NAME: Record<RoomLog['kind'], string> = { NOTICE: '단체방 공지, 큐레이션', REMINDER: '행사 1일 전 리마인드', CONTENT: '대화 주제/아이스브레이킹' }
const NAME_BY_TYPE: Record<string, string> = Object.fromEntries(DEFS.filter((d) => d.code).map((d) => [d.code!, d.name]))
Object.assign(NAME_BY_TYPE, { PAYMENT_PENDING: '결제 안내', REALLOCATING: '재배치 안내', TRANSFERRED: '대체 일정 안내', TICKET_KEPT: '이용권 보관', REFUND_REQUESTED: '환불 요청/완료', CANCELED: '신청 취소', TABLE_CHANGED: '테이블 변경', FEEDBACK_REMINDER: '피드백 요청' })
const CHANNEL_BY_TYPE = (_t: string) => '푸시'

const DEMO_HIST: Hist[] = [
  ['10.21 08:00', '대체 일정 안내', 'ALTERNATIVE_OFFERED 9명', '푸시', 9, 9, false],
  ['10.20 24:03', '테이블 큐레이션', '확정 19테이블', '단체방', 19, 19, false],
  ['10.20 24:03', '매칭 확정', '96명', '푸시', 95, 96, true],
  ['10.25 19:30', '행사 1일 전 리마인드', '10.15 회차, 노OO', '푸시', 0, 1, false],
].map(([at, name, to, channel, ok, total, retried], i) => ({ key: String(i), at: at as string, atMs: i, name: name as string, to: to as string, channel: channel as string, ok: ok as number, total: total as number, failedIds: ok === 0 ? [3] : [], failedPostIds: [], retried: retried as boolean }))

const p2 = (n: number) => String(n).padStart(2, '0')
const md = (d: Date) => `${p2(d.getMonth() + 1)}.${p2(d.getDate())}`
function range(period: Period, a: Date) {
  const d = new Date(a.getFullYear(), a.getMonth(), a.getDate())
  if (period === 'day') return { start: d, end: new Date(d.getTime() + DAY), label: `${md(d)} (${'일월화수목금토'[d.getDay()]})` }
  if (period === 'week') {
    // 기준일까지 최근 7일
    const start = new Date(d.getTime() - 6 * DAY)
    const end = new Date(d.getTime() + DAY)
    return { start, end, label: `${d.getMonth() + 1}월 ${Math.ceil(d.getDate() / 7)}주, ${md(start)} ~ ${md(d)}` }
  }
  return { start: new Date(d.getFullYear(), d.getMonth(), 1), end: new Date(d.getFullYear(), d.getMonth() + 1, 1), label: `${d.getFullYear()}년 ${d.getMonth() + 1}월` }
}
const shift = (p: Period, d: Date, n: number) => (p === 'day' ? new Date(d.getTime() + n * DAY) : p === 'week' ? new Date(d.getTime() + 7 * n * DAY) : new Date(d.getFullYear(), d.getMonth() + n, 1))

/** A08 자동 메시지, 대화 콘텐츠 관리 */
export default function Messages() {
  const toast = useToast()
  const templates = useAdminApi(() => get<Template[]>('/api/admin/templates'), adminDemo.templates)
  const logs = useAdminApi(() => get<NotificationLog[]>('/api/admin/notifications'), adminDemo.notifications)
  const roomLogs = useAdminApi(() => get<RoomLog[]>('/api/admin/room-messages'), [] as RoomLog[])
  const demo = logs.demo
  const [period, setPeriod] = useState<Period>('week')
  // 기준일 기본값 = 서비스 시각 (시연 시각 이동 반영)
  const now = useServiceNow()
  const [picked, setAnchor] = useState<Date | null>(null)
  const anchor = picked ?? (demo ? new Date(2026, 9, 20) : now)
  const r = range(period, anchor)
  const [selKey, setSelKey] = useState('curation')
  const lib = useAdminApi(() => get<TopicLib>('/api/admin/topics'), DEMO_LIB)
  const [topicForm, setTopicForm] = useState<{ cat: string; text: string } | null>(null)
  const [openCat, setOpenCat] = useState<string | null>(null)
  const [resultF, setResultF] = useState('all')
  const [channelF, setChannelF] = useState('all')

  const def = DEFS.find((d) => d.key === selKey)!
  const saved = templates.data.find((t) => t.code === def.code) ?? def
  const [title, setTitle] = useState(saved.title)
  const [body, setBody] = useState(saved.body)
  useEffect(() => {
    setTitle(saved.title)
    setBody(saved.body)
  }, [selKey, saved.title, saved.body])

  /** 미리보기: 실제로 나가는 메시지 전체 (단체방은 고정 안내와 다른 템플릿까지 합쳐서) */
  const tpl = (code: string) => {
    if (code === def.code) return { title, body }
    const t = templates.data.find((x) => x.code === code) ?? DEFS.find((d) => d.code === code)
    return { title: t?.title ?? '', body: t?.body ?? '' }
  }
  const preview = (() => {
    if (def.code === 'ROOM_NOTICE' || def.code === 'ROOM_CURATION') {
      const n = tpl('ROOM_NOTICE')
      const c = tpl('ROOM_CURATION')
      const curation = `✨ ${fill(c.title)}\n${fill(c.body)}`
      return { head: `🎉 ${fill(n.title)}`, body: fill(n.body.replace(/\{\{\s*큐레이션\s*\}\}/g, () => curation)) }
    }
    if (def.code === 'ROOM_REMINDER') return { head: `⏰ ${fill(title)}`, body: fill(body) }
    if (def.code === 'ROOM_TOPICS') return { head: `💬 ${fill(title)}`, body: [body.trim() ? fill(body) : '', TOPIC_LIST].filter(Boolean).join('\n\n') }
    return { head: fill(title), body: fill(body) }
  })()

  const insertVar = (v: string) => setBody((b) => `${b}${varToken(v)}`)
  const upsert = (next: Template) => templates.setData(templates.data.some((t) => t.code === def.code) ? templates.data.map((t) => (t.code === def.code ? next : t)) : [...templates.data, next])
  const save = async () => {
    try {
      upsert(templates.demo ? { id: Date.now(), code: def.code, title, body, updatedAt: null } : await put<Template>(`/api/admin/templates/${def.code}`, { title, body }))
      toast.show(def.channel === '푸시' ? `${def.name} 템플릿을 저장했어요. 다음 발송부터 적용돼요` : `${def.name} 템플릿을 저장했어요. 새로 열리는 단체방부터 적용돼요`)
    } catch (e) {
      toast.show(e instanceof Error ? e.message : '저장하지 못했어요')
    }
  }
  const reset = async () => {
    try {
      if (!templates.demo) upsert(await post<Template>(`/api/admin/templates/${def.code}/reset`))
      else upsert({ id: Date.now(), code: def.code, title: def.title, body: def.body, updatedAt: null })
      toast.show('기본 문구로 되돌렸어요')
    } catch (e) {
      toast.show(e instanceof Error ? e.message : '되돌리지 못했어요')
    }
  }

  const hist: Hist[] = useMemo(() => {
    if (demo) return DEMO_HIST
    const g = new Map<string, Hist>()
    logs.data
      .filter((n) => {
        const t = new Date(n.sentAt ?? n.scheduledAt)
        return t >= r.start && t < r.end
      })
      .forEach((n) => {
        const t = new Date(n.sentAt ?? n.scheduledAt)
        const k = `${n.type}|${t.toISOString().slice(0, 16)}`
        const h = g.get(k) ?? { key: k, at: `${md(t)} ${p2(t.getHours())}:${p2(t.getMinutes())}`, atMs: t.getTime(), name: NAME_BY_TYPE[n.type] ?? n.type, to: '', channel: CHANNEL_BY_TYPE(n.type), ok: 0, total: 0, failedIds: [], failedPostIds: [], retried: false }
        h.total++
        if (n.status === 'SENT' || n.status === 'READ') h.ok++
        if (n.status === 'FAILED') h.failedIds.push(n.id)
        if (n.attempts > 1) h.retried = true
        h.to = `${h.total}명`
        g.set(k, h)
      })
    // 단체방 자동 메시지 (같은 종류·같은 분에 올라간 단체방을 한 줄로)
    roomLogs.data
      .filter((m) => {
        const t = new Date(m.at)
        return t >= r.start && t < r.end
      })
      .forEach((m) => {
        const t = new Date(m.at)
        const k = `ROOM_${m.kind}|${t.toISOString().slice(0, 16)}`
        const h = g.get(k) ?? { key: k, at: `${md(t)} ${p2(t.getHours())}:${p2(t.getMinutes())}`, atMs: t.getTime(), name: ROOM_NAME[m.kind], to: '', channel: '단체방', ok: 0, total: 0, failedIds: [], failedPostIds: [], retried: false }
        h.total++
        if (m.status === 'SENT') h.ok++
        else if (m.postId) h.failedPostIds.push(m.postId)
        h.to = `단체방 ${h.total}개`
        g.set(k, h)
      })
    return [...g.values()].sort((a, b) => b.atMs - a.atMs)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [demo, logs.data, roomLogs.data, r.start.getTime(), r.end.getTime()])
  const shown = hist.filter((h) => (resultF === 'all' ? true : resultF === 'fail' ? h.failedIds.length + h.failedPostIds.length > 0 : h.failedIds.length + h.failedPostIds.length === 0) && (channelF === 'all' || h.channel.includes(channelF)))

  const retry = async (h: Hist) => {
    try {
      if (!demo) {
        await Promise.all(h.failedIds.map((id) => post(`/api/admin/notifications/${id}/retry`)))
        await Promise.all(h.failedPostIds.map((id) => post(`/api/admin/room-posts/${id}/retry`)))
      }
      toast.show(`${h.failedIds.length + h.failedPostIds.length}건 다시 보냈어요`)
      logs.reload()
      roomLogs.reload()
    } catch (e) {
      toast.show(e instanceof Error ? e.message : '다시 보내지 못했어요')
    }
  }
  const addTopic = async () => {
    if (!topicForm?.text.trim()) return
    try {
      const item = lib.demo ? { id: Date.now(), category: topicForm.cat, text: topicForm.text.trim() } : await post<TopicLib['custom'][number]>('/api/admin/topics', { category: topicForm.cat, text: topicForm.text.trim() })
      lib.setData({ ...lib.data, custom: [item, ...lib.data.custom] })
      setTopicForm(null)
      setOpenCat(item.category)
      toast.show(`${catLabel(item.category)} 주제를 등록했어요. 다음 대화 주제 게시부터 반영돼요`)
    } catch (e) {
      toast.show(e instanceof Error ? e.message : '등록하지 못했어요')
    }
  }
  const removeTopic = async (id: number) => {
    try {
      if (!lib.demo) await del(`/api/admin/topics/${id}`)
      lib.setData({ ...lib.data, custom: lib.data.custom.filter((t) => t.id !== id) })
    } catch (e) {
      toast.show(e instanceof Error ? e.message : '삭제하지 못했어요')
    }
  }
  const libCats = TOPIC_CATS.filter(([c]) => (lib.data.builtIn[c] ?? 0) > 0 || lib.data.custom.some((t) => t.category === c))

  return (
    <AdminLayout title="자동 메시지, 대화 콘텐츠 관리">
      <DemoNotice show={demo} />
      <div className="row" style={{ gap: 12 }}>
        <Seg
          value={period}
          onChange={setPeriod}
          options={[
            ['day', '일별'],
            ['week', '주간'],
            ['month', '월별'],
          ]}
        />
        <div className="adm-stepper">
          <button aria-label="이전" onClick={() => setAnchor(shift(period, anchor, -1))}>
            <Icon name="chevron-left" size={16} />
          </button>
          <span className="label">{r.label}</span>
          <button aria-label="다음" onClick={() => setAnchor(shift(period, anchor, 1))}>
            <Icon name="chevron-right" size={16} />
          </button>
        </div>
      </div>

      <div className="adm-msg-cols">
        <nav className="adm-msg-list">
          <b>템플릿</b>
          {DEFS.map((d) => (
            <button key={d.key} className={cx(d.key === selKey && 'on')} onClick={() => setSelKey(d.key)}>
              <b>{d.name}</b>
              <small>{d.channel}</small>
            </button>
          ))}
        </nav>

        <section className="adm-card16 adm-msg-edit">
          <div className="row" style={{ gap: 8 }}>
            <h2 className="t-h2" style={{ flex: 1 }}>
              {def.name}
            </h2>
            <Badge tone="확정" dot={false}>
              사용 중
            </Badge>
          </div>
          <div className="adm-msg-meta">
            {[
              ['발송 시점', def.when],
              ['채널', def.via],
              ['대상', def.target],
            ].map(([k, v]) => (
              <div key={k}>
                <small>{k}</small>
                <b>{v}</b>
              </div>
            ))}
          </div>
          {def.vars.length > 0 && (
            <>
              <small className="t-caption">변수</small>
              <div className="row" style={{ gap: 6, flexWrap: 'wrap' }}>
                {def.vars.map((v) => (
                  <button key={v} className="adm-badge-btn" onClick={() => insertVar(v)} title="본문 끝에 넣기">
                    {varToken(v)}
                  </button>
                ))}
              </div>
            </>
          )}
          <div className="adm-msg-body">
            <input value={title} onChange={(e) => setTitle(e.target.value)} aria-label="제목" />
            <textarea value={body} onChange={(e) => setBody(e.target.value)} aria-label="본문" rows={def.channel === '단체방' ? 16 : 4} />
          </div>
          <div className="row" style={{ gap: 8, justifyContent: 'flex-end' }}>
            <button className="adm-outline" onClick={reset}>
              기본 문구로
            </button>
            <Button size="S" disabled={title === saved.title && body === saved.body} onClick={save}>
              저장
            </Button>
          </div>
        </section>

        <aside className="adm-msg-preview">
          <small>{def.channel === '단체방' ? '미리보기, 단체방에 실제로 올라가는 전체 메시지 (예시 값)' : '미리보기, 예시 값'}</small>
          <div className="bubble">
            <p className="head">
              <Icon name="sparkle" size={16} />
              {preview.head}
            </p>
            <p>{preview.body}</p>
          </div>
          <div className="lib">
            <b>대화 주제 라이브러리</b>
            {libCats.map(([k, label]) => {
              const mine = lib.data.custom.filter((t) => t.category === k)
              return (
                <Fragment key={k}>
                  <button className="line" onClick={() => setOpenCat(openCat === k ? null : k)} aria-expanded={openCat === k}>
                    <span>{label}</span>
                    <em>
                      {(lib.data.builtIn[k] ?? 0) + mine.length}개{mine.length > 0 && ` (등록 ${mine.length})`}
                    </em>
                  </button>
                  {openCat === k &&
                    mine.map((t) => (
                      <div key={t.id} className="mine">
                        <span>{t.text}</span>
                        <button aria-label="삭제" onClick={() => removeTopic(t.id)}>
                          <Icon name="trash" size={14} />
                        </button>
                      </div>
                    ))}
                </Fragment>
              )
            })}
            {topicForm ? (
              <div className="stack" style={{ gap: 6, width: '100%' }}>
                <select value={topicForm.cat} onChange={(e) => setTopicForm({ ...topicForm, cat: e.target.value })}>
                  {TOPIC_CATS.map(([k, l]) => (
                    <option key={k} value={k}>
                      {l}
                    </option>
                  ))}
                </select>
                <input value={topicForm.text} onChange={(e) => setTopicForm({ ...topicForm, text: e.target.value })} placeholder="새 대화 주제" autoFocus />
                <div className="row" style={{ gap: 6 }}>
                  <Button size="S" variant="secondary" onClick={() => setTopicForm(null)}>
                    취소
                  </Button>
                  <Button size="S" disabled={!topicForm.text.trim()} onClick={addTopic}>
                    등록
                  </Button>
                </div>
              </div>
            ) : (
              <Button size="S" onClick={() => setTopicForm({ cat: openCat ?? 'GENERAL', text: '' })}>
                + 주제 등록
              </Button>
            )}
          </div>
        </aside>
      </div>

      <div className="adm-mt-section">
        <div className="row" style={{ gap: 8 }}>
          <h2 className="t-h2" style={{ flex: 1 }}>
            발송 이력
          </h2>
          <label className="adm-mini-select">
            <select value={resultF} onChange={(e) => setResultF(e.target.value)}>
              <option value="all">결과 전체 ▾</option>
              <option value="ok">완료만</option>
              <option value="fail">실패만</option>
            </select>
          </label>
          <label className="adm-mini-select">
            <select value={channelF} onChange={(e) => setChannelF(e.target.value)}>
              <option value="all">채널 전체 ▾</option>
              <option value="푸시">푸시</option>
              <option value="단체방">단체방</option>
            </select>
          </label>
        </div>
        <DataTable
          className="adm-msg-table"
          rows={shown}
          rowKey={(h) => h.key}
          empty="이 기간에 보낸 메시지가 없어요"
          columns={[
            { key: 'at', label: '시각', width: 110, render: (h) => <span className={cx('nowrap', h.failedIds.length + h.failedPostIds.length > 0 && 'adm-msg-fail')}>{h.at}</span>, sort: (h) => h.atMs },
            { key: 'name', label: '템플릿', width: 170, render: (h) => h.name, sort: (h) => h.name },
            { key: 'to', label: '수신', width: 190, render: (h) => h.to, sort: (h) => h.total },
            { key: 'ch', label: '채널', width: 110, render: (h) => h.channel, sort: (h) => h.channel },
            { key: 'res', label: '결과', width: 180, render: (h) => (h.failedIds.length + h.failedPostIds.length ? (h.ok ? `${h.ok} / ${h.total}, ${h.failedIds.length + h.failedPostIds.length}건 실패` : '재시도 3회 실패') : `${h.ok} / ${h.total}${h.retried ? ', 재시도 후 성공' : ' 성공'}`), sort: (h) => h.ok / Math.max(h.total, 1) },
            {
              key: 'st',
              label: '',
              render: (h) =>
                h.failedIds.length + h.failedPostIds.length ? (
                  <div className="row" style={{ gap: 6 }}>
                    <Badge tone="경고" dot={false}>
                      예외로 이동
                    </Badge>
                    <button className="adm-outline card" onClick={() => retry(h)}>
                      다시 보내기
                    </button>
                  </div>
                ) : (
                  <Badge tone="확정" dot={false}>
                    완료
                  </Badge>
                ),
            },
          ]}
        />
      </div>
      {toast.node}
    </AdminLayout>
  )
}
