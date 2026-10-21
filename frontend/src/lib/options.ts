import { userKey } from './api'
// 백엔드 Options enum 코드 ↔ 화면 라벨 (Figma 표기)
export const INTERESTS = [
  ['MOVIE_DRAMA', '영화/드라마'],
  ['MUSIC', '음악'],
  ['READING_WRITING', '독서/글쓰기'],
  ['WORKOUT', '운동 (헬스, 러닝, 클라이밍 등)'],
  ['FOOD_CAFE', '맛집/카페 탐방'],
  ['TRAVEL', '여행'],
  ['PHOTO_VIDEO', '사진/영상'],
  ['EXHIBITION_ART', '전시/공연/아트'],
  ['FASHION_BEAUTY', '패션/뷰티'],
  ['SELF_DEVELOPMENT', '자기계발/공부'],
] as const

export const TRAITS = [
  ['CALM', '잔잔하고 조용한 편'],
  ['ENERGETIC', '활기차고 에너지 있는 편'],
  ['DEEP_TALK', '깊은 대화를 좋아함'],
  ['HUMOR', '가볍고 유머 있는 대화를 좋아함'],
  ['LISTENER', '경청을 잘 함'],
  ['LEADER', '이야기를 이끄는 편'],
] as const

export const DESIRED_TYPES = [
  ['SIMILAR', '비슷한 사람'],
  ['DIFFERENT', '새로운 자극'],
  ['DEEP_TALK', '깊은 대화'],
  ['HUMOR', '편한 유머'],
  ['PASSIONATE', '열정적인'],
  ['CALM_STABLE', '안정적인'],
] as const

export const PRICE_RANGES = [
  ['P10_20', '1~2만원'],
  ['P20_30', '2~3만원'],
  ['P30_PLUS', '3만원+'],
] as const

export const REGIONS = [
  ['HONGDAE_HAPJEONG', '홍대/합정'],
  ['EULJIRO_JONGNO', '을지로/종로'],
  ['GANGNAM', '강남/신논현'],
  ['JAMSIL', '잠실'],
] as const

export const CUISINES = ['한식', '중식', '일식', '양식']
export const ALLERGIES = ['없음', '갑각류', '견과류', '유제품', '밀가루', '기타']

export const labelOf = (list: readonly (readonly [string, string])[], code: string) => list.find(([c]) => c === code)?.[1] ?? code

/** 온보딩·신청 단계에서 모으는 프로필 초안. 신청 직전에 PUT /api/me/profile 로 한 번에 저장한다. */
export type ProfileDraft = {
  name: string
  birthYear: string
  job: string
  gender: 'FEMALE' | 'MALE' | ''
  mbti: string // 'ENFP' 또는 '' (잘 모르겠어요)
  interests: string[]
  traits: string[]
  desiredTypes: string[]
  priceRanges: string[]
  instagramId: string
  foodRestrictions: string
}

const KEY = () => userKey('wt.profileDraft')
export const emptyDraft: ProfileDraft = {
  name: '',
  birthYear: '',
  job: '',
  gender: '',
  mbti: '',
  interests: [],
  traits: [],
  desiredTypes: [],
  priceRanges: [],
  instagramId: '',
  foodRestrictions: '',
}

export function loadDraft(): ProfileDraft {
  try {
    return { ...emptyDraft, ...JSON.parse(localStorage.getItem(KEY()) ?? '{}') }
  } catch {
    return { ...emptyDraft }
  }
}
export function saveDraft(d: ProfileDraft) {
  try {
    localStorage.setItem(KEY(), JSON.stringify(d))
  } catch {
    /* noop */
  }
}

export const toggle = (list: string[], v: string) => (list.includes(v) ? list.filter((x) => x !== v) : [...list, v])
