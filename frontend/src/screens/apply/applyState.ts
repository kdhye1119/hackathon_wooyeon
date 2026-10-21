// 신청 3단계(매칭 선호 → 일정/지역 → 이용권/결제) 사이에서 유지하는 입력값
export type ApplyState = {
  desiredTypes: string[]
  priceRanges: string[]
  cuisines: string[]
  allergies: string[]
  avoidFood: string
  agreePrivacy: boolean
  agreePolicy: boolean
  agreeMarketing: boolean
  regions: string[]
  dates: string[]
  otherRegion: string
  product: string
  method: string
  policyChecked: boolean
}

const KEY = 'wt.apply'
export const initialApply: ApplyState = {
  desiredTypes: [],
  priceRanges: [],
  cuisines: [],
  allergies: [],
  avoidFood: '',
  // 개인정보·정책 동의는 사용자가 직접 체크해야 한다 (ACC-05)
  agreePrivacy: false,
  agreePolicy: false,
  agreeMarketing: false,
  regions: [],
  dates: [],
  otherRegion: '',
  product: 'SINGLE',
  method: 'CARD',
  policyChecked: false,
}

/** 데모(시안) 모드에서만 Figma 예시 선택값을 채운다 */
export const demoApplyDefaults: Partial<ApplyState> = {
  desiredTypes: ['DIFFERENT', 'DEEP_TALK'],
  priceRanges: ['P20_30'],
  cuisines: ['한식', '일식'],
  allergies: ['갑각류'],
  agreePrivacy: true,
  agreePolicy: true,
  policyChecked: true,
  regions: ['HONGDAE_HAPJEONG', 'EULJIRO_JONGNO'],
  dates: ['2026-10-29', '2026-11-05'],
}

export function loadApply(): ApplyState {
  try {
    const raw = sessionStorage.getItem(KEY)
    return raw ? { ...initialApply, ...JSON.parse(raw) } : { ...initialApply }
  } catch {
    return { ...initialApply }
  }
}

export function saveApply(s: ApplyState) {
  try {
    sessionStorage.setItem(KEY, JSON.stringify(s))
  } catch {
    /* noop */
  }
}

/** 지난 신청 내용이 있는지 (3-2 '지난 신청 내용을 불러왔어요') */
export function hasPrevious() {
  try {
    return localStorage.getItem('wt.applyLast') !== null
  } catch {
    return false
  }
}
export function rememberLast(s: ApplyState) {
  try {
    localStorage.setItem('wt.applyLast', JSON.stringify(s))
  } catch {
    /* noop */
  }
}
export function loadLast(): Partial<ApplyState> | null {
  try {
    const raw = localStorage.getItem('wt.applyLast')
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}
