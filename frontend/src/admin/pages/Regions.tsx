import { useMemo, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { AdminLayout, DataTable, DemoNotice, Field, Panel, Seg, Toggle, useAdminApi } from '../AdminLayout'
import { Badge, Button, useToast } from '../../components/ui'
import { get } from '../../lib/api'
import type { AdminSlot, ApplicantRow, Venue } from '../types'
import { adminDemo } from '../demo'

/**
 * 운영 지역. 백엔드 지역은 고정 enum 이라 상세 범위·운영 상태·추가 지역은 이 기기에 저장한다.
 * 식당 수·신청 수·오픈 알림 수요는 실제 데이터에서 계산한다.
 */
type Region = { code: string; province: string; label: string; detail: string; status: 'OPEN' | 'PAUSED' | 'READY'; firstSlot?: string; maxTables?: number }

const KEY = 'wt.admin.regions'
const BASE: Region[] = [
  { code: 'HONGDAE_HAPJEONG', province: '서울특별시', label: '홍대/합정', detail: '홍대입구역, 합정역 인근', status: 'OPEN' },
  { code: 'EULJIRO_JONGNO', province: '서울특별시', label: '을지로/종로', detail: '을지로3가역, 종각역 인근', status: 'OPEN' },
  { code: 'GANGNAM', province: '서울특별시', label: '강남/신논현', detail: '강남역, 신논현역 인근', status: 'OPEN' },
  { code: 'JAMSIL', province: '서울특별시', label: '잠실', detail: '잠실역, 송리단길', status: 'OPEN' },
]
function loadRegions(): Region[] {
  try {
    const raw = localStorage.getItem(KEY)
    return raw ? JSON.parse(raw) : BASE
  } catch {
    return BASE
  }
}
function saveRegions(r: Region[]) {
  try {
    localStorage.setItem(KEY, JSON.stringify(r))
  } catch {
    /* noop */
  }
}

const DEMO_DEMAND = [
  { label: '서울 성수', count: 42 },
  { label: '경기 판교', count: 31 },
  { label: '부산 서면', count: 18 },
  { label: '대전 둔산', count: 9 },
]

/** A02-1 지역 관리 */
export function Regions() {
  const navigate = useNavigate()
  const [regions, setRegions] = useState(loadRegions)
  const venues = useAdminApi(() => get<Venue[]>('/api/admin/venues'), adminDemo.venues)
  const slots = useAdminApi(() => get<AdminSlot[]>('/api/admin/slots'), adminDemo.slots)
  const apps = useAdminApi(() => get<ApplicantRow[]>('/api/admin/applications'), adminDemo.applicants)

  // 참가자 앱 '기타 지역 알림' 수요 (신청서 otherRegion)
  const demand = useMemo(() => {
    if (apps.demo) return DEMO_DEMAND
    const m = new Map<string, number>()
    apps.data.forEach((a) => a.otherRegion && m.set(a.otherRegion, (m.get(a.otherRegion) ?? 0) + 1))
    return [...m.entries()].map(([label, count]) => ({ label, count })).sort((a, b) => b.count - a.count)
  }, [apps.demo, apps.data])
  const demandMax = Math.max(...demand.map((d) => d.count), 1)

  // 이번 회차 = 가장 가까운 모집 중 날짜
  const nextDate = slots.data.filter((s) => s.status === 'RECRUITING').map((s) => s.date).sort()[0]
  const applicantsOf = (code: string) => {
    if (slots.demo) return { HONGDAE_HAPJEONG: 46, EULJIRO_JONGNO: 38, GANGNAM: 27, JAMSIL: 17 }[code]
    const s = slots.data.find((x) => x.region === code && x.date === nextDate)
    return s?.applicants
  }

  const update = (code: string, p: Partial<Region>) => {
    const next = regions.map((r) => (r.code === code ? { ...r, ...p } : r))
    setRegions(next)
    saveRegions(next)
  }

  const byProvince = useMemo(() => {
    const m = new Map<string, Region[]>()
    regions.forEach((r) => m.set(r.province, [...(m.get(r.province) ?? []), r]))
    return [...m.entries()]
  }, [regions])

  return (
    <AdminLayout title="지역 관리">
      <DemoNotice show={venues.demo} />
      <div className="adm-section-head">
        <h2 className="t-h2">운영 지역</h2>
        <Button size="S" onClick={() => navigate('/admin/regions/new')}>
          + 지역 추가
        </Button>
      </div>
      {byProvince.map(([province, list]) => (
        <DataTable
          key={province}
          light
          rows={list}
          rowKey={(r) => r.code}
          group={
            <>
              {province} <span className="t-body-s muted" style={{ fontWeight: 400 }}>&nbsp;{list.length}개 지역</span>
            </>
          }
          columns={[
            { key: 'label', label: '지역', width: 160, render: (r) => <span className="strong">{r.label}</span>, sort: (r) => r.label },
            { key: 'detail', label: '상세 범위', width: 220, render: (r) => r.detail, sort: (r) => r.detail },
            {
              key: 'status',
              label: '상태',
              width: 110,
              render: (r) => (r.status === 'OPEN' ? <Badge tone="완료" dot={false}>운영 중</Badge> : r.status === 'PAUSED' ? <Badge tone="중립" dot={false}>일시 중지</Badge> : <Badge dot={false}>준비 중</Badge>),
              sort: (r) => r.status,
            },
            { key: 'venues', label: '식당', width: 70, render: (r) => venues.data.filter((v) => v.region === r.code && v.active).length, sort: (r) => venues.data.filter((v) => v.region === r.code).length },
            { key: 'apps', label: '이번 회차 신청', width: 120, render: (r) => applicantsOf(r.code) ?? '—', sort: (r) => applicantsOf(r.code) ?? -1 },
            {
              key: 'actions',
              label: '관리',
              render: (r) => (
                <div className="adm-actions">
                  <Button size="S" variant="secondary" onClick={() => navigate(`/admin/regions/new?edit=${r.code}`)}>
                    수정
                  </Button>
                  {r.status === 'OPEN' ? (
                    <Button size="S" variant="ghost" onClick={() => update(r.code, { status: 'PAUSED' })}>
                      일시 중지
                    </Button>
                  ) : (
                    <Button size="S" variant="ghost" onClick={() => update(r.code, { status: 'OPEN' })}>
                      {r.status === 'READY' ? '오픈하기' : '다시 운영'}
                    </Button>
                  )}
                </div>
              ),
            },
          ]}
        />
      ))}

      <Panel title="오픈 알림 신청" action={<span className="t-body-s muted">참가자 앱 '기타 지역 알림'</span>}>
        {demand.length === 0 && <p className="t-body-s muted">아직 다른 지역 알림 신청이 없어요</p>}
        {demand.map((d) => {
          const exists = regions.find((r) => d.label.endsWith(r.label))
          return (
            <div key={d.label} className="row" style={{ gap: 14 }}>
              <b className="t-label-m" style={{ width: 90 }}>
                {d.label}
              </b>
              <span className="adm-bar">
                <i style={{ width: `${(d.count / demandMax) * 100}%` }} />
              </span>
              <b className="t-label-m">{d.count}명</b>
              {exists ? (
                <Badge dot={false}>{exists.status === 'OPEN' ? '운영 중' : '준비 중'}</Badge>
              ) : (
                <Button size="S" variant="secondary" onClick={() => navigate(`/admin/regions/new?name=${encodeURIComponent(d.label)}`)}>
                  지역으로 추가
                </Button>
              )}
            </div>
          )
        })}
      </Panel>
    </AdminLayout>
  )
}

const PROVINCES = ['서울특별시', '경기도', '인천광역시', '부산광역시', '대전광역시', '대구광역시', '광주광역시']

/** A02-2 지역 추가 (?edit=코드 이면 수정) */
export function RegionForm() {
  const navigate = useNavigate()
  const toast = useToast()
  const [params] = useSearchParams()
  const editing = loadRegions().find((r) => r.code === params.get('edit'))
  const preset = params.get('name') ?? ''
  const [province, setProvince] = useState(editing?.province ?? (PROVINCES.find((p) => p.startsWith(preset.split(' ')[0])) || '서울특별시'))
  const [label, setLabel] = useState(editing?.label ?? (preset.split(' ').slice(1).join(' ') || ''))
  const [detail, setDetail] = useState(editing?.detail ?? '')
  const [firstSlot, setFirstSlot] = useState(editing?.firstSlot ?? '')
  const [maxTables, setMaxTables] = useState(editing?.maxTables ?? 4)
  const [status, setStatus] = useState<'READY' | 'OPEN'>(editing?.status === 'OPEN' ? 'OPEN' : 'READY')
  const [notify, setNotify] = useState(true)
  const apps = useAdminApi(() => get<ApplicantRow[]>('/api/admin/applications'), adminDemo.applicants)
  // 이 지역 오픈 알림 수요 = 신청서 '기타 지역'에 지역 이름을 적은 신청자 수
  const waiting = apps.demo ? 42 : label.trim() ? new Set(apps.data.filter((a) => a.otherRegion?.includes(label.trim())).map((a) => a.userId)).size : 0

  const save = () => {
    const all = loadRegions()
    const code = editing?.code ?? `CUSTOM_${Date.now()}`
    const next: Region = { code, province, label, detail, status, firstSlot, maxTables }
    saveRegions(editing ? all.map((r) => (r.code === code ? next : r)) : [...all, next])
    toast.show(notify && !editing ? `${label} 오픈 알림을 ${waiting}명에게 보냈어요` : '저장했어요')
    window.setTimeout(() => navigate('/admin/regions'), 800)
  }

  return (
    <AdminLayout title={editing ? '지역 수정' : '지역 추가'}>
      <Panel className="muted-labels" style={{ gap: 20 }}>
        <div className="adm-form-row">
          <Field label="시/도">
            <select value={province} onChange={(e) => setProvince(e.target.value)}>
              {PROVINCES.map((p) => (
                <option key={p}>{p}</option>
              ))}
            </select>
          </Field>
          <Field label="지역 이름">
            <input value={label} onChange={(e) => setLabel(e.target.value)} placeholder="성수" />
          </Field>
          <Field label="상세 범위">
            <input value={detail} onChange={(e) => setDetail(e.target.value)} placeholder="성수역, 뚝섬역 인근" />
          </Field>
        </div>
        <div className="adm-form-row">
          <Field label="첫 회차">
            <input type="datetime-local" value={firstSlot} onChange={(e) => setFirstSlot(e.target.value)} />
          </Field>
          <Field label="지역별 최대 테이블">
            <input type="number" min={1} value={maxTables} onChange={(e) => setMaxTables(Number(e.target.value) || 1)} />
          </Field>
        </div>
        <div className="adm-field">
          <span>상태</span>
          <div>
            <Seg
              className="form inline"
              value={status}
              onChange={setStatus}
              options={[
                ['READY', '준비 중'],
                ['OPEN', '운영 중'],
              ]}
            />
          </div>
        </div>
        {!editing && (
          <div className="adm-toggle-row">
            <div>
              <b className="t-label-m">오픈 알림 보내기</b>
              <span className="t-body-s muted">
                {label || '이 지역'} 오픈 알림을 신청한 {waiting}명에게 첫 회차 소식을 보내요
              </span>
            </div>
            <Toggle on={notify} onChange={setNotify} label="오픈 알림 보내기" />
          </div>
        )}
        <div className="row" style={{ gap: 10, justifyContent: 'flex-end' }}>
          <Button size="S" variant="secondary" onClick={() => navigate('/admin/regions')}>
            취소
          </Button>
          <Button size="S" disabled={!label.trim()} onClick={save}>
            {editing ? '저장' : '지역 추가'}
          </Button>
        </div>
      </Panel>
      {toast.node}
    </AdminLayout>
  )
}
