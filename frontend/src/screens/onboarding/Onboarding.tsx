import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Body, Button, Chip, Screen, TextField, useToast } from '../../components/ui'
import { Icon } from '../../components/Icon'
import { INTERESTS, TRAITS, loadDraft, saveDraft, toggle, type ProfileDraft } from '../../lib/options'
import { get, put, session } from '../../lib/api'

type ServerProfile = {
  name: string | null
  birthYear: number | null
  gender: 'FEMALE' | 'MALE' | null
  job: string | null
  instagramId: string | null
  mbti: string | null
  interests: string[]
  traits: string[]
  desiredTypes: string[]
  priceRanges: string[]
  foodRestrictions: string | null
}
const live = () => !!session.token && session.token !== 'demo'
import './onboarding.css'

const MBTI_PAIRS = [
  [['E', '외향'], ['I', '내향']],
  [['N', '직관'], ['S', '감각']],
  [['F', '감정'], ['T', '사고']],
  [['P', '인식'], ['J', '판단']],
] as const

/** 2-1 ~ 2-4 온보딩 (가입 직후 1회) */
export default function Onboarding() {
  const navigate = useNavigate()
  const [step, setStep] = useState(0)
  const [d, setD] = useState<ProfileDraft>(loadDraft)
  const set = (p: Partial<ProfileDraft>) => setD((prev) => ({ ...prev, ...p }))
  // MBTI 는 축별로 고른다. 잘 모르겠어요 → ''
  const [mbti, setMbti] = useState<string[]>(() => (d.mbti.length === 4 ? d.mbti.split('') : ['E', 'N', 'F', 'P']))
  const [mbtiUnknown, setMbtiUnknown] = useState(false)
  const [server, setServer] = useState<ServerProfile | null>(null)

  // ACC-02/06 기존 프로필 불러오기 (프로필 수정·재가입)
  useEffect(() => {
    if (!live()) return
    get<ServerProfile>('/api/me/profile')
      .then((p) => {
        setServer(p)
        setD((prev) => ({
          ...prev,
          name: p.name ?? prev.name,
          birthYear: p.birthYear ? String(p.birthYear) : prev.birthYear,
          gender: p.gender ?? prev.gender,
          job: p.job ?? prev.job,
          mbti: p.mbti ?? prev.mbti,
          interests: p.interests.length ? p.interests : prev.interests,
          traits: p.traits.length ? p.traits : prev.traits,
          instagramId: p.instagramId ?? prev.instagramId,
        }))
        if (p.mbti?.length === 4) setMbti(p.mbti.split(''))
      })
      .catch(() => undefined)
  }, [])

  const valid = [
    d.name.trim() && /^\d{4}$/.test(d.birthYear) && d.job.trim() && d.gender,
    true,
    d.interests.length > 0,
    d.traits.length > 0,
  ][step]

  const toast = useToast()
  const next = async () => {
    const draft = { ...d, mbti: mbtiUnknown ? '' : mbti.join('') }
    saveDraft(draft)
    if (step < 3) return setStep(step + 1)
    // 로그인한 계정은 바로 서버에 저장 (매칭 선호는 비어 있어도 되고, 신청할 때 고른다)
    if (live()) {
      try {
        await put('/api/me/profile', {
        name: draft.name,
        birthYear: Number(draft.birthYear),
        gender: draft.gender,
        job: draft.job,
        instagramId: draft.instagramId || server?.instagramId || null,
        mbti: draft.mbti || null,
        interests: draft.interests,
        traits: draft.traits,
        desiredTypes: server?.desiredTypes ?? null,
        priceRanges: server?.priceRanges ?? null,
        foodRestrictions: server?.foodRestrictions ?? null,
        })
      } catch (e) {
        // 저장 실패를 조용히 넘기지 않는다: 이 화면에 머물며 이유를 보여준다
        toast.show(e instanceof Error ? `프로필을 저장하지 못했어요: ${e.message}` : '프로필을 저장하지 못했어요')
        return
      }
    }
    session.patch({ name: draft.name })
    navigate('/', { replace: true })
  }

  return (
    <Screen>
      <div className="ob-top">
        <button className="icon-btn" aria-label="뒤로" onClick={() => (step ? setStep(step - 1) : navigate(-1))}>
          <Icon name="chevron-left" />
        </button>
        <div className="ob-progress" aria-hidden>
          {[0, 1, 2, 3].map((i) => (
            <span key={i} className={i <= step ? 'on' : undefined} />
          ))}
        </div>
        <span className="t-label-s muted">{step + 1}/4</span>
      </div>

      <Body className="ob-body">
        {step === 0 && (
          <>
            <h2 className="t-display-m">기본 정보</h2>
            <TextField className="field-form" label="이름" placeholder="김서윤" value={d.name} onChange={(e) => set({ name: e.target.value })} />
            <div className="ob-row">
              <TextField
                className="field-form"
                label="출생연도"
                placeholder="1999"
                inputMode="numeric"
                maxLength={4}
                value={d.birthYear}
                onChange={(e) => set({ birthYear: e.target.value.replace(/\D/g, '') })}
              />
              <TextField className="field-form" label="직업" placeholder="UX 디자이너" value={d.job} onChange={(e) => set({ job: e.target.value })} />
            </div>
            <div className="field">
              <span className="field-label">성별</span>
              <div className="chips">
                <Chip selected={d.gender === 'FEMALE'} onClick={() => set({ gender: 'FEMALE' })}>
                  여성
                </Chip>
                <Chip selected={d.gender === 'MALE'} onClick={() => set({ gender: 'MALE' })}>
                  남성
                </Chip>
              </div>
            </div>
          </>
        )}

        {step === 1 && (
          <>
            <h2 className="t-display-m">MBTI</h2>
            <div className="mbti-grid">
              {MBTI_PAIRS.map((pair, axis) => (
                <div key={axis} className="mbti-row">
                  {pair.map(([letter, label]) => (
                    <button
                      key={letter}
                      className="mbti-cell"
                      aria-pressed={!mbtiUnknown && mbti[axis] === letter}
                      onClick={() => {
                        setMbtiUnknown(false)
                        setMbti((m) => m.map((v, i) => (i === axis ? letter : v)))
                      }}
                    >
                      <b>{letter}</b>
                      <span>{label}</span>
                    </button>
                  ))}
                </div>
              ))}
              <button className="t-body-s muted underline" aria-pressed={mbtiUnknown} onClick={() => setMbtiUnknown(true)}>
                {mbtiUnknown ? '✓ 잘 모르겠어요' : '잘 모르겠어요'}
              </button>
            </div>
          </>
        )}

        {step === 2 && (
          <>
            <h2 className="t-display-m">관심사</h2>
            <p className="t-label-s muted">복수 선택</p>
            <div className="chips">
              {INTERESTS.map(([code, label]) => (
                <Chip key={code} selected={d.interests.includes(code)} onClick={() => set({ interests: toggle(d.interests, code) })}>
                  {label}
                </Chip>
              ))}
            </div>
          </>
        )}

        {step === 3 && (
          <>
            <h2 className="t-display-m">나의 성향</h2>
            <p className="t-label-s muted">복수 선택</p>
            <div className="chips">
              {TRAITS.map(([code, label]) => (
                <Chip key={code} selected={d.traits.includes(code)} onClick={() => set({ traits: toggle(d.traits, code) })}>
                  {label}
                </Chip>
              ))}
            </div>
          </>
        )}
      </Body>

      <div className="ob-footer">
        <Button disabled={!valid} onClick={next}>
          {step === 3 ? '완료' : '다음'}
        </Button>
      </div>
      {toast.node}
    </Screen>
  )
}
