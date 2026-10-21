import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { AdminLayout, DemoNotice, Field, Panel, Toggle, useAdminApi } from '../AdminLayout'
import { Badge, Button, useToast } from '../../components/ui'
import { Icon } from '../../components/Icon'
import { get, put } from '../../lib/api'
import type { AdminSlot, Policy as PolicyDto } from '../types'
import { adminDemo } from '../demo'
import { fmt } from '../../lib/useApi'

/** 선호 조건 (PRD 6.4 MAT-04 우선순위 순). 연령은 필수 조건이라 슬라이더 없음 */
const WEIGHTS = [
  ['genderWeight', '성비'],
  ['mbtiWeight', 'MBTI 궁합'],
  ['interestWeight', '공통 관심사'],
  ['desireWeight', '만나고 싶은 성향'],
  ['newnessWeight', '새로운 사람 비율'],
] as const
type WeightKey = (typeof WEIGHTS)[number][0]

const AUTOMATION = [
  ['autoConfirm', '조건 충족 테이블 자동 확정', 'MAT-10'],
  ['waitlist', '취소석 대기자 자동 충원', '응답 기한 3시간 → 다음 대기자'],
  ['reallocate', '확정 후 인원 부족 시 자동 재배치', '§15 확정 후 1명 취소'],
  ['autoPost', '리마인드, 대화 콘텐츠 자동 게시', '행사 1일 전 19:30, 시작 시각'],
] as const

const AUTO_KEY = 'wt.admin.automation'
const VER_KEY = 'wt.admin.policyVersion'
const loadAuto = (): Record<string, boolean> => {
  try {
    return { autoConfirm: true, waitlist: true, reallocate: true, autoPost: true, ...JSON.parse(localStorage.getItem(AUTO_KEY) ?? '{}') }
  } catch {
    return { autoConfirm: true, waitlist: true, reallocate: true, autoPost: true }
  }
}
const loadVer = () => {
  try {
    return Number(localStorage.getItem(VER_KEY) ?? '3')
  } catch {
    return 3
  }
}

// Figma 시안 가중치 (데모)
const DEMO_POLICY: PolicyDto = { ...adminDemo.policy, genderWeight: 85, mbtiWeight: 70, interestWeight: 60, desireWeight: 45, newnessWeight: 30, minScore: 60 }

function WeightRow({ name, value, total, edit, original, onChange }: { name: string; value: number; total: number; edit?: boolean; original?: number; onChange?: (v: number) => void }) {
  const changed = edit && original !== undefined && original !== value
  return (
    <div className="adm-weight">
      <span className="name">{name}</span>
      <span className="track">
        <i style={{ width: `${Math.min(value, 100)}%` }} />
        {edit && (
          <>
            <span className="thumb" style={{ left: `${Math.min(value, 100)}%` }} />
            <input type="range" min={0} max={100} step={5} value={value} onChange={(e) => onChange?.(Number(e.target.value))} aria-label={`${name} 가중치`} />
          </>
        )}
      </span>
      <span className={edit ? `val edit${changed ? ' changed' : ''}` : 'val'}>{value}</span>
      <span className="pct">{total ? Math.round((value / total) * 100) : 0}%</span>
      {edit && <span className="diff">{changed ? `${original}→${value}` : ''}</span>}
    </div>
  )
}

function Stepper({ value, onChange, min, max, suffix = '', disabled, paper, focus }: { value: number; onChange?: (v: number) => void; min: number; max: number; suffix?: string; disabled?: boolean; paper?: boolean; focus?: boolean }) {
  return (
    <span className={`adm-step${paper ? ' paper' : ''}${focus ? ' focus' : ''}`}>
      <button disabled={disabled || value <= min} onClick={() => onChange?.(value - (paper ? 5 : 1))} aria-label="줄이기">
        −
      </button>
      <span>
        {value}
        {suffix}
      </span>
      <button disabled={disabled || value >= max} onClick={() => onChange?.(value + (paper ? 5 : 1))} aria-label="늘리기">
        +
      </button>
    </span>
  )
}

/** A04 매칭 규칙 (보기) / A04-1 수정 */
export default function Policy({ edit = false }: { edit?: boolean }) {
  const navigate = useNavigate()
  const toast = useToast()
  const policy = useAdminApi(() => get<PolicyDto>('/api/admin/policy'), DEMO_POLICY)
  const slots = useAdminApi(() => get<AdminSlot[]>('/api/admin/slots'), adminDemo.slots)
  const [draft, setDraft] = useState<PolicyDto>(policy.data)
  const [auto, setAuto] = useState(loadAuto)
  const [memo, setMemo] = useState('')
  const [busy, setBusy] = useState(false)
  const ver = loadVer()
  useEffect(() => setDraft(policy.data), [policy.data])

  const p = edit ? draft : policy.data
  const total = WEIGHTS.reduce((n, [k]) => n + p[k], 0)
  const nextRun = slots.data.filter((s) => s.status === 'RECRUITING').map((s) => s.matchAt).sort()[0]
  const nextRunLabel = nextRun ? `${fmt.dl(nextRun).md} ${fmt.dl(nextRun).time}` : '다음 회차'
  const changes = [...WEIGHTS.filter(([k]) => draft[k] !== policy.data[k]).map(([k, n]) => `${n} ${policy.data[k]}→${draft[k]}`), ...(draft.minScore !== policy.data.minScore ? [`최소 점수 ${policy.data.minScore}→${draft.minScore}`] : []),
    ...(draft.minTableSize !== policy.data.minTableSize || draft.maxTableSize !== policy.data.maxTableSize
      ? [`테이블 인원 ${policy.data.minTableSize}~${policy.data.maxTableSize}→${draft.minTableSize}~${draft.maxTableSize}명`]
      : []),
    ...(draft.maxAgeGap !== policy.data.maxAgeGap ? [`최대 나이 차 ${policy.data.maxAgeGap}→${draft.maxAgeGap}살`] : [])]

  const save = async () => {
    setBusy(true)
    try {
      if (!policy.demo)
        await put('/api/admin/policy', {
          ageWeight: draft.ageWeight,
          genderWeight: draft.genderWeight,
          mbtiWeight: draft.mbtiWeight,
          interestWeight: draft.interestWeight,
          desireWeight: draft.desireWeight,
          newnessWeight: draft.newnessWeight,
          minScore: draft.minScore,
          maxAgeGap: draft.maxAgeGap,
          minTableSize: draft.minTableSize,
          maxTableSize: draft.maxTableSize,
        })
      try {
        localStorage.setItem(AUTO_KEY, JSON.stringify(auto))
        localStorage.setItem(VER_KEY, String(ver + 1))
      } catch {
        /* noop */
      }
      toast.show(policy.demo ? `v0.${ver + 1} 규칙을 배포했어요${memo ? ` (${memo})` : ''}` : `규칙을 저장했어요${memo ? ` (${memo})` : ''}`)
      window.setTimeout(() => navigate('/admin/policy'), 800)
    } catch (e) {
      toast.show(e instanceof Error ? e.message : '저장하지 못했어요')
    } finally {
      setBusy(false)
    }
  }

  return (
    <AdminLayout title={edit ? '매칭 규칙 수정' : '매칭 규칙'} onBack={edit ? () => navigate('/admin/policy') : undefined}>
      <DemoNotice show={policy.demo} />
      <Panel className="policy">
        <h2 className="t-h2">{edit ? (policy.demo ? `자동 매칭 규칙, v0.${ver} → v0.${ver + 1}` : '자동 매칭 규칙 수정') : '자동 매칭 규칙'}</h2>
        {!policy.demo && policy.data.updatedAt && (
          <p className="t-body-s muted">
            마지막 수정 {policy.data.updatedAt.slice(0, 16).replace('T', ' ')}
            {policy.data.updatedBy ? `, ${policy.data.updatedBy}` : ''}
          </p>
        )}

        <div className="stack" style={{ gap: 8 }}>
          <p className="adm-req-title">
            <Icon name="shield" size={16} />
            필수 조건
          </p>
          {edit ? (
            <div className="stack" style={{ gap: 10 }}>
              <div className="row" style={{ gap: 10 }}>
                <span className="t-body-m" style={{ width: 120 }}>
                  테이블 인원
                </span>
                <Stepper value={draft.minTableSize} min={2} max={draft.maxTableSize} onChange={(v) => setDraft({ ...draft, minTableSize: v })} />
                <span className="t-body-m">~</span>
                <Stepper value={draft.maxTableSize} min={draft.minTableSize} max={10} onChange={(v) => setDraft({ ...draft, maxTableSize: v })} />
                <span className="t-body-m">명</span>
              </div>
              <div className="row" style={{ gap: 10 }}>
                <span className="t-body-m" style={{ width: 120 }}>
                  최대 나이 차
                </span>
                <Stepper value={draft.maxAgeGap} min={1} max={30} onChange={(v) => setDraft({ ...draft, maxAgeGap: v })} />
                <span className="t-body-m">살</span>
                <span className="t-caption muted">자동 매칭·재배치는 이 값을 넘는 조합을 만들지 않아요</span>
              </div>
              <div className="row" style={{ gap: 8 }}>
                <span className="t-body-m muted" style={{ width: 120 }}>
                  고정
                </span>
                {['결제 완료', '날짜/지역 일치', '차단/신고/비선호 제외'].map((t) => (
                  <span key={t} className="adm-fixed">
                    <Icon name="shield" size={13} />
                    <span>{t}</span>
                  </span>
                ))}
              </div>
            </div>
          ) : (
            <div className="row" style={{ gap: 6, flexWrap: 'wrap' }}>
              {['결제 완료', '날짜/지역 일치', `${p.minTableSize}~${p.maxTableSize}명`, `나이 차 ${p.maxAgeGap}살 이내`, '차단/신고/비선호 없음'].map((t) => (
                <Badge key={t} dot={false}>
                  {t}
                </Badge>
              ))}
            </div>
          )}
        </div>

        <div className="stack" style={{ gap: 10 }}>
          <p className="t-label-m">선호 조건 가중치</p>
          <div className="adm-weight">
            <span className="name">연령 차이</span>
            <Badge tone="중립" dot={false}>
              필수 조건, {p.maxAgeGap}살 이내
            </Badge>
          </div>
          {WEIGHTS.map(([k, name]) => (
            <WeightRow
              key={k}
              name={name}
              value={p[k]}
              total={total}
              edit={edit}
              original={policy.data[k]}
              onChange={(v) => setDraft({ ...draft, [k as WeightKey]: v })}
            />
          ))}
        </div>

        <div className="adm-minscore">
          <span className="label">최소 매칭 점수</span>
          {edit ? <Stepper paper value={draft.minScore} min={0} max={100} suffix="점" onChange={(v) => setDraft({ ...draft, minScore: v })} /> : <b className="t-h2">{p.minScore}점</b>}
        </div>

        <div className="stack" style={{ gap: 12 }}>
          <p className="t-label-m">자동화</p>
          {!policy.demo && <p className="t-body-s muted">자동화는 서버에서 항상 켜져 있어요</p>}
          {AUTOMATION.map(([k, title, sub]) => (
            <div key={k} className="adm-auto">
              <div>
                <b>{title}</b>
                <small>{sub}</small>
              </div>
              {/* 서버 자동화는 항상 켜져 있어 실데이터에서는 바꿀 수 없다 (시안에서만 조작) */}
              <Toggle on={policy.demo ? auto[k] : true} onChange={(v) => edit && policy.demo && setAuto({ ...auto, [k]: v })} label={title} />
            </div>
          ))}
        </div>

        {edit ? (
          <>
            <div className="adm-preview" style={{ padding: '14px 16px', borderRadius: 12, gap: 6 }}>
              <b className="t-label-m">바뀌는 내용</b>
              <span className="t-body-s muted">{changes.length ? changes.join(', ') : '아직 바뀐 값이 없어요'}</span>
            </div>
            <Field label="변경 메모" style={{ flex: 'none' }}>
              <input value={memo} onChange={(e) => setMemo(e.target.value)} placeholder="관심사 비중 강화, 최소 점수 상향" />
            </Field>
            <div className="row" style={{ gap: 10 }}>
              <Button variant="secondary" onClick={() => navigate('/admin/policy')}>
                취소
              </Button>
              <Button disabled={busy || changes.length === 0} onClick={save}>
                {policy.demo ? `저장, v0.${ver + 1} 배포` : '저장'}
              </Button>
            </div>
            <p className="t-body-s muted">다음 자동 매칭({nextRunLabel})부터 적용돼요</p>
          </>
        ) : (
          <Button variant="secondary" onClick={() => navigate('/admin/policy/edit')}>
            규칙 수정
          </Button>
        )}
      </Panel>
      {toast.node}
    </AdminLayout>
  )
}
