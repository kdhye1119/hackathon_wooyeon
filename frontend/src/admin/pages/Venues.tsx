import { useMemo, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { AdminLayout, DataTable, DemoNotice, Field, Panel, useAdminApi } from '../AdminLayout'
import { Badge, Button, Dialog, useToast } from '../../components/ui'
import { Icon } from '../../components/Icon'
import { adminSession, api, del, get, post, put } from '../../lib/api'
import { REGIONS } from '../../lib/options'
import type { AdminSlot, FeedbackSummary, Venue } from '../types'
import { adminDemo } from '../demo'

/** 백엔드 식당 모델에 없는 항목 (종류·담당자·예약 방법·가능 요일·메모·평점)은 이 기기에 저장 */
type VenueMeta = { cuisine: string; manager: string; method: string; days: string[]; memo: string; rating?: number; deadline: string }
const META_KEY = 'wt.admin.venueMeta'
const DEMO_META: Record<string, Partial<VenueMeta>> = {
  '을지 화로': { cuisine: '한식 다이닝', rating: 4.7, manager: '박OO 매니저', method: '전화', memo: '주차 불가, 알레르기 메뉴 사전 요청 가능' },
  '종로 담': { cuisine: '노포', rating: 4.5 },
  '연남 비스트로': { cuisine: '비스트로', rating: 4.4 },
  '합정 소반': { cuisine: '한식', rating: 4.6 },
  '신논현 키친': { cuisine: '와인 다이닝', rating: 4.8 },
  '모래정원': { cuisine: '이탈리안', rating: 4.6 },
}
function loadMeta(): Record<string, Partial<VenueMeta>> {
  try {
    const demo = !adminSession.token || adminSession.token === 'demo'
    return { ...(demo ? DEMO_META : {}), ...JSON.parse(localStorage.getItem(META_KEY) ?? '{}') }
  } catch {
    return {}
  }
}
function saveMeta(name: string, m: Partial<VenueMeta>) {
  try {
    const all = JSON.parse(localStorage.getItem(META_KEY) ?? '{}')
    all[name] = { ...all[name], ...m }
    localStorage.setItem(META_KEY, JSON.stringify(all))
  } catch {
    /* noop */
  }
}

const PRICE: Record<string, string> = { P10_20: '1~2만원', P20_30: '2~3만원', P30_PLUS: '3만원+' }
const pad = (n: number) => String(n).padStart(2, '0')
const md = (iso: string) => {
  const d = new Date(iso)
  return `${pad(d.getMonth() + 1)}.${pad(d.getDate())}`
}

type Availability = { venueId: number; name: string; slotId: number; capacity: number; booked: number }

/** 다음 매칭 회차(확정)와 다음 모집 회차의 식당별 재고 */
function useAvailability(slots: AdminSlot[], demo: boolean) {
  const matched = slots.filter((s) => s.status === 'MATCHED').sort((a, b) => b.date.localeCompare(a.date))[0]
  const next = slots.filter((s) => s.status === 'RECRUITING').sort((a, b) => a.date.localeCompare(b.date))[0]
  const ids = [matched, next].filter(Boolean).map((s) => s!.id)
  const avail = useAdminApi(
    () => Promise.all(ids.map((id) => get<Availability[]>(`/api/admin/slots/${id}/venue-availability`).catch(() => [] as Availability[]))).then((l) => l.flat()),
    [] as Availability[],
    [ids.join(',')],
  )
  const at = (venueId: number, slot?: AdminSlot) => (slot ? avail.data.find((a) => a.venueId === venueId && a.slotId === slot.id) : undefined)
  return { matched, next, at, demo: demo || avail.demo }
}

/** A03 식당 관리 */
export function Venues() {
  const navigate = useNavigate()
  const toast = useToast()
  const venues = useAdminApi(() => get<Venue[]>('/api/admin/venues'), adminDemo.venues)
  const slots = useAdminApi(() => get<AdminSlot[]>('/api/admin/slots'), adminDemo.slots)
  const meta = loadMeta()
  const av = useAvailability(slots.data, slots.demo)
  const [confirm, setConfirm] = useState<Venue | null>(null)
  const DEMO_ALLOC: Record<number, [number, number, number]> = { 1: [4, 4, 3], 2: [2, 2, 2], 3: [3, 3, 2], 4: [2, 2, 2], 5: [1, 1, 0], 6: [1, 2, 2] }

  const remove = async (v: Venue) => {
    setConfirm(null)
    try {
      if (!venues.demo) await put(`/api/admin/venues/${v.id}`, { ...v, region: v.region, active: false })
      toast.show(`${v.name}을(를) 비활성화했어요`)
      venues.setData(venues.data.map((x) => (x.id === v.id ? { ...x, active: false } : x)))
    } catch (e) {
      toast.show(e instanceof Error ? e.message : '처리하지 못했어요')
    }
  }

  return (
    <AdminLayout title="식당 관리">
      <DemoNotice show={venues.demo} />
      <div className="adm-section-head">
        <h2 className="t-h2">파트너 식당 관리</h2>
        <Button size="S" onClick={() => navigate('/admin/venues/new')}>
          + 식당 추가
        </Button>
      </div>
      <DataTable
        rows={venues.data}
        rowKey={(v) => v.id}
        onRow={(v) => navigate(`/admin/venues/${v.id}`)}
        columns={[
          { key: 'name', label: '식당', width: 110, render: (v) => <span className="strong">{v.name}</span>, sort: (v) => v.name },
          { key: 'kind', label: '종류', width: 90, render: (v) => meta[v.name]?.cuisine ?? '—', sort: (v) => meta[v.name]?.cuisine ?? '' },
          { key: 'price', label: '가격대', width: 70, render: (v) => PRICE[v.priceRange] ?? v.priceGuide, sort: (v) => v.priceRange },
          { key: 'region', label: '지역', width: 104, render: (v) => v.regionLabel, sort: (v) => v.regionLabel },
          { key: 'rating', label: '평점', width: 44, render: (v) => meta[v.name]?.rating ?? '—', sort: (v) => meta[v.name]?.rating ?? 0 },
          { key: 'tables', label: '테이블', width: 64, render: (v) => `${v.tablesPerSlot}테이블`, sort: (v) => v.tablesPerSlot },
          { key: 'seats', label: '인원', width: 56, render: (v) => `${v.tablesPerSlot * v.seatsPerTable}명`, sort: (v) => v.tablesPerSlot * v.seatsPerTable },
          {
            key: 'alloc',
            label: `${av.matched ? md(av.matched.date) : ''} 배정`,
            width: 72,
            render: (v) => {
              if (av.demo) return `${DEMO_ALLOC[v.id]?.[1] ?? 0} / ${DEMO_ALLOC[v.id]?.[0] ?? v.tablesPerSlot}`
              const a = av.at(v.id, av.matched)
              return a ? `${a.booked} / ${a.capacity}` : '—'
            },
          },
          {
            key: 'status',
            label: '상태',
            render: (v) => {
              if (!v.active) return <Badge tone="중립" dot={false}>비활성</Badge>
              const day = av.next ? md(av.next.date) : ''
              const free = av.demo ? DEMO_ALLOC[v.id]?.[2] ?? 0 : (() => {
                const a = av.at(v.id, av.next)
                return a ? a.capacity - a.booked : v.tablesPerSlot
              })()
              return free > 0 ? <Badge dot={false}>{day} 가능 {free}</Badge> : <Badge tone="경고" dot={false}>{day} {v.seatsPerTable}인석 부족</Badge>
            },
          },
          {
            key: 'del',
            label: '',
            width: 28,
            render: (v) => (
              <button
                className="adm-icon-btn"
                aria-label={`${v.name} 삭제`}
                onClick={(e) => {
                  e.stopPropagation()
                  setConfirm(v)
                }}
              >
                <Icon name="trash" size={18} />
              </button>
            ),
          },
        ]}
      />
      <div className="adm-warn-note">
        <b>배정 실패 시</b>
        <p className="t-body-s">같은 지역 대체 식당 → 실패 시 예외</p>
      </div>
      <Dialog open={!!confirm} onClose={() => setConfirm(null)}>
        <h3 className="dialog-title">{confirm?.name}을(를) 삭제할까요?</h3>
        <p className="t-body-s muted" style={{ textAlign: 'center' }}>
          이미 배정된 테이블은 유지되고, 이후 자동 배정에서 빠져요
        </p>
        <div className="row" style={{ gap: 10, paddingTop: 10 }}>
          <Button variant="secondary" onClick={() => setConfirm(null)}>
            취소
          </Button>
          <Button variant="danger" onClick={() => confirm && remove(confirm)}>
            삭제
          </Button>
        </div>
      </Dialog>
      {toast.node}
    </AdminLayout>
  )
}

/** A03-1 식당 상세 */
export function VenueDetail() {
  const { id } = useParams()
  const navigate = useNavigate()
  const toast = useToast()
  const venues = useAdminApi(() => get<Venue[]>('/api/admin/venues'), adminDemo.venues)
  const slots = useAdminApi(() => get<AdminSlot[]>('/api/admin/slots'), adminDemo.slots)
  const feedback = useAdminApi(() => get<FeedbackSummary>('/api/admin/feedback'), adminDemo.feedback)
  const v = venues.data.find((x) => String(x.id) === id) ?? venues.data[2]
  const meta = loadMeta()[v?.name ?? ''] ?? {}
  const dates = useMemo(() => [...new Set(slots.data.filter((s) => s.region === v?.region).map((s) => s.date))].sort(), [slots.data, v?.region])
  const avail = useAdminApi(
    () =>
      Promise.all(
        slots.data
          .filter((s) => s.region === v?.region)
          .map((s) => get<Availability[]>(`/api/admin/slots/${s.id}/venue-availability`).then((l) => ({ slot: s, a: l.find((x) => x.venueId === v?.id) })).catch(() => ({ slot: s, a: undefined }))),
      ),
    [] as { slot: AdminSlot; a?: Availability }[],
    [slots.data.length, v?.id],
  )
  const DEMO_ROWS: [string, number, number, string, '확정' | '진행' | '경고'][] = [
    ['10.15 (목)', 3, 3, '예약 확정', '확정'],
    ['10.22 (목)', 3, 3, '예약 확정', '확정'],
    ['11.05 (목)', 3, 1, '예약 요청 중', '진행'],
    ['11.12 (목)', 2, 0, '배정 전', '진행'],
    ['11.19 (목)', 0, 0, '휴무', '경고'],
  ]
  const rows = venues.demo
    ? DEMO_ROWS
    : avail.data.map(({ slot, a }) => {
        const d = new Date(slot.date)
        const cap = a?.capacity ?? v.tablesPerSlot
        const booked = a?.booked ?? 0
        const label = cap === 0 ? '휴무' : slot.status === 'RECRUITING' ? (booked ? '예약 요청 중' : '배정 전') : '예약 확정'
        return [`${md(slot.date)} (${'일월화수목금토'[d.getDay()]})`, cap, booked, label, cap === 0 ? '경고' : label === '예약 확정' ? '확정' : '진행'] as [string, number, number, string, '확정' | '진행' | '경고']
      })

  const toggleActive = async () => {
    try {
      if (!venues.demo) await put(`/api/admin/venues/${v.id}`, { ...v, active: !v.active })
      venues.setData(venues.data.map((x) => (x.id === v.id ? { ...x, active: !x.active } : x)))
      toast.show(v.active ? '일시 중지했어요' : '다시 제휴를 시작했어요')
    } catch (e) {
      toast.show(e instanceof Error ? e.message : '처리하지 못했어요')
    }
  }

  if (!v) return null
  return (
    <AdminLayout title="식당 상세" onBack={() => navigate('/admin/venues')}>
      <Panel style={{ gap: 10 }}>
        <div className="row" style={{ gap: 12 }}>
          <h2 className="t-display-m">{v.name}</h2>
          <Badge dot={false} tone={v.active ? '확정' : '중립'}>
            {v.active ? '제휴 중' : '일시 중지'}
          </Badge>
          <span style={{ flex: 1 }} />
          <Button size="S" variant="secondary" onClick={() => navigate(`/admin/venues/new?edit=${v.id}`)}>
            정보 수정
          </Button>
          <Button size="S" variant="ghost" onClick={toggleActive}>
            {v.active ? '일시 중지' : '다시 제휴'}
          </Button>
          <Button
            size="S"
            variant="danger"
            onClick={async () => {
              try {
                if (!venues.demo) await del(`/api/admin/venues/${v.id}`).catch(() => put(`/api/admin/venues/${v.id}`, { ...v, active: false }))
                navigate('/admin/venues')
              } catch (e) {
                toast.show(e instanceof Error ? e.message : '삭제하지 못했어요')
              }
            }}
          >
            삭제
          </Button>
        </div>
        <div className="row" style={{ gap: 8, flexWrap: 'wrap' }}>
          {[meta.cuisine, v.regionLabel, meta.rating ? `평점 ${meta.rating}` : null, `1인 ${PRICE[v.priceRange] ?? v.priceGuide}`, `최대 ${v.tablesPerSlot}테이블, ${v.tablesPerSlot * v.seatsPerTable}명`]
            .filter(Boolean)
            .map((t) => (
              <Badge key={t!} dot={false}>
                {t}
              </Badge>
            ))}
        </div>
      </Panel>
      <div className="adm-row">
        <div className="stack" style={{ gap: 16, flex: 1, minWidth: 0 }}>
          <Panel title="기본 정보" style={{ gap: 12 }}>
            <a className="adm-map" href={v.mapUrl ?? `https://map.naver.com/v5/search/${encodeURIComponent(v.address)}`} target="_blank" rel="noreferrer">
              <Icon name="map-pin" color="var(--terracotta)" />
              <span className="t-label-s muted">지도 보기</span>
            </a>
            <div className="adm-kv">
              <span>주소</span>
              <span>{v.address}</span>
            </div>
            <div className="adm-kv">
              <span>연락처</span>
              <span>
                {v.phone ?? '—'}
                {meta.manager ? `, 담당 ${meta.manager}` : ''}
              </span>
            </div>
            <div className="adm-kv">
              <span>예약 방법</span>
              <span>
                {meta.method ?? '전화'} 예약, {meta.deadline ?? '회차 3일 전'}까지
              </span>
            </div>
            <div className="adm-kv">
              <span>단체석</span>
              <span>
                {v.seatsPerTable >= 6 ? '4~6' : `4~${v.seatsPerTable}`}인 테이블 {v.tablesPerSlot}개
              </span>
            </div>
            <div className="adm-kv">
              <span>메모</span>
              <span>{meta.memo || '—'}</span>
            </div>
          </Panel>
          <Panel
            title="식당 만족도"
            action={
              <span className="row" style={{ gap: 4, alignItems: 'baseline' }}>
                <b className="t-display-m">{feedback.data.avgVenue.toFixed(1)}</b>
                <span className="t-body-s muted">/ 5, 후기 {feedback.data.responses}건</span>
              </span>
            }
            style={{ gap: 12 }}
          >
            {feedback.data.recent.slice(0, 3).map((f) => (
              <div key={f.id} className="adm-review-line">
                <b className="t-label-m">T-{f.tableId}</b>
                <span className="t-body-s" style={{ flex: 1 }}>
                  {f.comment || '코멘트 없음'}
                </span>
                <b className="t-label-m">{f.venueRating.toFixed(1)}</b>
              </div>
            ))}
          </Panel>
        </div>
        <Panel title="회차별 예약" action={<Button size="S" variant="secondary" onClick={() => navigate(`/admin/venues/new?edit=${v.id}`)}>가능 테이블 수정</Button>} style={{ gap: 0, flex: 1 }}>
          <div className="adm-mini-table" style={{ marginTop: 12 }}>
            <div className="head">
              <span style={{ width: 90 }}>회차</span>
              <span style={{ width: 60 }}>가능</span>
              <span style={{ width: 60 }}>배정</span>
              <span>상태</span>
            </div>
            {rows.map(([d, cap, booked, label, tone]) => (
              <div key={d} className="line">
                <b className="t-label-m" style={{ width: 90 }}>
                  {d}
                </b>
                <span style={{ width: 60 }}>{cap}</span>
                <span style={{ width: 60 }}>{booked}</span>
                <Badge tone={tone} dot={false}>
                  {label}
                </Badge>
              </div>
            ))}
            {!venues.demo && dates.length === 0 && <p className="t-body-s muted" style={{ padding: 12 }}>이 지역 회차가 없어요</p>}
          </div>
        </Panel>
      </div>
      {toast.node}
    </AdminLayout>
  )
}

const CUISINES = ['한식', '중식', '일식', '양식']
const METHODS = ['전화', '네이버 예약', '기타']
const WEEK = ['월', '화', '수', '목', '금', '토', '일']
const PROVINCES = ['서울특별시', '경기도', '인천광역시', '부산광역시']

function Pills({ options, value, onChange, multi }: { options: string[]; value: string[]; onChange: (v: string[]) => void; multi?: boolean }) {
  return (
    <div className="row" style={{ gap: 8, flexWrap: 'wrap' }}>
      {options.map((o) => {
        const on = value.includes(o)
        return (
          <button key={o} className="adm-pill" aria-pressed={on} onClick={() => onChange(multi ? (on ? value.filter((x) => x !== o) : [...value, o]) : [o])}>
            {o}
          </button>
        )
      })}
    </div>
  )
}

/** A03-2 식당 추가 (?edit=id 이면 수정) */
export function VenueForm() {
  const navigate = useNavigate()
  const toast = useToast()
  const editId = new URLSearchParams(window.location.search).get('edit')
  const venues = useAdminApi(() => get<Venue[]>('/api/admin/venues'), adminDemo.venues)
  const editing = editId ? venues.data.find((v) => String(v.id) === editId) : undefined
  const m = editing ? loadMeta()[editing.name] ?? {} : {}
  const [name, setName] = useState(editing?.name ?? '')
  const [phone, setPhone] = useState(editing?.phone ?? '')
  const [address, setAddress] = useState(editing?.address ?? '')
  const [manager, setManager] = useState(m.manager ?? '')
  const [province, setProvince] = useState('서울특별시')
  const [region, setRegion] = useState(editing?.region ?? 'EULJIRO_JONGNO')
  const [cuisine, setCuisine] = useState([m.cuisine ?? '한식'])
  const [price, setPrice] = useState(editing?.priceRange ?? 'P20_30')
  const [tables, setTables] = useState(editing?.tablesPerSlot ?? 3)
  const [deadline, setDeadline] = useState(m.deadline ?? '회차 3일 전')
  const [method, setMethod] = useState([m.method ?? '전화'])
  const [days, setDays] = useState<string[]>(m.days ?? ['목'])
  const [memo, setMemo] = useState(m.memo ?? '')
  const [busy, setBusy] = useState(false)
  // 식당 사진: 저장된 사진(삭제 표시 가능) + 새로 고른 사진. 저장할 때 한 번에 반영
  const [removed, setRemoved] = useState<string[]>([])
  const [added, setAdded] = useState<{ file: File; url: string }[]>([])
  const saved = (editing?.photoUrls ?? []).filter((u) => !removed.includes(u))
  const pick = (files: FileList | null) => {
    if (!files) return
    const imgs = [...files].filter((f) => f.type.startsWith('image/'))
    const big = imgs.filter((f) => f.size > 5 * 1024 * 1024)
    if (big.length) toast.show('사진은 한 장에 5MB 까지 올릴 수 있어요')
    const ok = imgs.filter((f) => f.size <= 5 * 1024 * 1024).slice(0, Math.max(0, 10 - saved.length - added.length))
    setAdded((prev) => [...prev, ...ok.map((file) => ({ file, url: URL.createObjectURL(file) }))])
  }

  const save = async () => {
    setBusy(true)
    try {
      const body = { name, region, address, mapUrl: editing?.mapUrl ?? null, phone, priceRange: price, priceGuide: `1인 ${PRICE[price]}`, seatsPerTable: 6, tablesPerSlot: tables, active: true }
      if (!venues.demo) {
        const v = editing ? await put<Venue>(`/api/admin/venues/${editing.id}`, body) : await post<Venue>('/api/admin/venues', body)
        for (const u of removed) await del(`/api/admin/venues/${v.id}/photos/${u.split('/').pop()}`)
        if (added.length) {
          const fd = new FormData()
          added.forEach((a) => fd.append('files', a.file))
          await api(`/api/admin/venues/${v.id}/photos`, { method: 'POST', body: fd })
        }
      }
      saveMeta(name, { cuisine: cuisine[0], manager, method: method[0], days, memo, deadline })
      toast.show(editing ? '식당 정보를 저장했어요' : `${name}을(를) 등록했어요`)
      window.setTimeout(() => navigate('/admin/venues'), 700)
    } catch (e) {
      toast.show(e instanceof Error ? e.message : '저장하지 못했어요')
    } finally {
      setBusy(false)
    }
  }

  return (
    <AdminLayout title={editing ? '식당 수정' : '식당 추가'} onBack={() => navigate(-1)}>
      <Panel className="big muted-labels">
        <h2 className="t-h2">기본 정보</h2>
        <div className="adm-form-row">
          <Field label="식당명">
            <input value={name} onChange={(e) => setName(e.target.value)} placeholder="예: 을지 화로" />
          </Field>
          <Field label="연락처">
            <input value={phone} onChange={(e) => setPhone(e.target.value)} placeholder="02-0000-0000" inputMode="tel" />
          </Field>
        </div>
        <div className="adm-form-row">
          <Field label="주소">
            <input value={address} onChange={(e) => setAddress(e.target.value)} placeholder="도로명 주소 검색" />
          </Field>
          <Field label="담당자">
            <input value={manager} onChange={(e) => setManager(e.target.value)} placeholder="이름/직함" />
          </Field>
        </div>
        <div className="adm-field">
          <span>시/도, 지역</span>
          <label className="adm-select-sm" style={{ alignSelf: 'flex-start' }}>
            <select value={province} onChange={(e) => setProvince(e.target.value)}>
              {PROVINCES.map((p) => (
                <option key={p}>{p}</option>
              ))}
            </select>
          </label>
          <Pills options={REGIONS.map(([, l]) => l)} value={[REGIONS.find(([c]) => c === region)?.[1] ?? '']} onChange={(v) => setRegion(REGIONS.find(([, l]) => l === v[0])?.[0] ?? region)} />
        </div>
        <h2 className="t-h2">메뉴/가격</h2>
        <div className="adm-field">
          <span>음식 종류</span>
          <Pills options={CUISINES} value={cuisine} onChange={setCuisine} />
        </div>
        <div className="adm-field">
          <span>1인 가격대</span>
          <Pills options={Object.values(PRICE)} value={[PRICE[price]]} onChange={(v) => setPrice(Object.entries(PRICE).find(([, l]) => l === v[0])?.[0] ?? price)} />
        </div>
        <h2 className="t-h2">수용/예약</h2>
        <div className="adm-form-row">
          <Field label="단체 테이블 수">
            <input type="number" min={0} value={tables} onChange={(e) => setTables(Number(e.target.value) || 0)} />
          </Field>
          <Field label="테이블당 인원">
            <input readOnly value="4 ~ 6명" />
          </Field>
          <Field label="예약 마감">
            <input value={deadline} onChange={(e) => setDeadline(e.target.value)} />
          </Field>
        </div>
        <div className="adm-field">
          <span>예약 방법</span>
          <Pills options={METHODS} value={method} onChange={setMethod} />
        </div>
        <div className="adm-field">
          <span>가능 요일</span>
          <Pills options={WEEK} value={days} onChange={setDays} multi />
        </div>
        <Field label="메모">
          <textarea value={memo} onChange={(e) => setMemo(e.target.value)} placeholder="주차, 단체석 위치, 알레르기 대응 등" style={{ minHeight: 80 }} />
        </Field>
        <div className="adm-field">
          <span>식당 사진, 첫 사진이 참가자 매칭 확정 화면 대표 사진 (없으면 로고)</span>
          <div className="adm-photos">
            {saved.map((u) => (
              <div key={u} className="adm-photo">
                <img src={u} alt="" />
                <button aria-label="사진 삭제" onClick={() => setRemoved((r) => [...r, u])}>
                  <Icon name="trash" size={14} />
                </button>
              </div>
            ))}
            {added.map((a) => (
              <div key={a.url} className="adm-photo new">
                <img src={a.url} alt="" />
                <button aria-label="사진 빼기" onClick={() => setAdded((list) => list.filter((x) => x.url !== a.url))}>
                  <Icon name="trash" size={14} />
                </button>
              </div>
            ))}
            {saved.length + added.length < 10 && (
              <label className="adm-dropzone adm-photo-add" style={{ cursor: 'pointer' }}>
                <Icon name="plus" />
                <span className="t-label-s muted">사진 추가</span>
                <input
                  type="file"
                  accept="image/*"
                  multiple
                  hidden
                  onChange={(e) => {
                    pick(e.target.files)
                    e.target.value = ''
                  }}
                />
              </label>
            )}
          </div>
          <small className="t-caption muted">장당 5MB, 최대 10장. 저장을 눌러야 반영돼요.</small>
        </div>
        <div className="row" style={{ gap: 10, justifyContent: 'flex-end' }}>
          <Button size="S" variant="secondary" onClick={() => navigate(-1)}>
            취소
          </Button>
          <Button size="S" disabled={busy || !name.trim() || !address.trim()} onClick={save}>
            {editing ? '저장' : '등록'}
          </Button>
        </div>
      </Panel>
      {toast.node}
    </AdminLayout>
  )
}
