import { userKey } from './api'
// 5-2 일기: "나만 볼 수 있는 기록". 서버에 올리지 않고 이 기기에만 저장한다.
export type Mood = 'flutter' | 'calm' | 'joy' | 'sad'
export type Diary = {
  appId: number
  date: string // ISO
  venue: string
  region: string
  photos: string[] // data URL (긴 변 720px 로 줄여서 저장)
  cover: number
  caption: string
  moods: Mood[]
  text: string
  remember: string[] // 다시 만나고 싶은 사람 이름
  people: string[]
}

const KEY = () => userKey('wt.diaries')

export function loadDiaries(): Record<number, Diary> {
  try {
    return JSON.parse(localStorage.getItem(KEY()) ?? '{}')
  } catch {
    return {}
  }
}

export function saveDiary(d: Diary) {
  const all = loadDiaries()
  all[d.appId] = d
  try {
    localStorage.setItem(KEY(), JSON.stringify(all))
    return true
  } catch {
    return false // 저장 공간 부족 (사진이 너무 많을 때)
  }
}

export function deleteDiary(appId: number) {
  const all = loadDiaries()
  delete all[appId]
  try {
    localStorage.setItem(KEY(), JSON.stringify(all))
  } catch {
    /* noop */
  }
}

/** 사진을 긴 변 720px JPEG 로 줄인다 */
export function shrinkImage(file: File, max = 720): Promise<string> {
  return new Promise((resolve, reject) => {
    const url = URL.createObjectURL(file)
    const img = new Image()
    img.onload = () => {
      const scale = Math.min(1, max / Math.max(img.width, img.height))
      const canvas = document.createElement('canvas')
      canvas.width = Math.round(img.width * scale)
      canvas.height = Math.round(img.height * scale)
      canvas.getContext('2d')!.drawImage(img, 0, 0, canvas.width, canvas.height)
      URL.revokeObjectURL(url)
      resolve(canvas.toDataURL('image/jpeg', 0.82))
    }
    img.onerror = () => {
      URL.revokeObjectURL(url)
      reject(new Error('사진을 불러오지 못했어요'))
    }
    img.src = url
  })
}

/** Figma 예시 일기 (데모) */
export const demoDiary: Diary = {
  appId: 99,
  date: '2026-10-15T19:30:00',
  venue: '을지 화로',
  region: '을지로/종로',
  photos: [],
  cover: 0,
  caption: '우연히, 다섯 사람',
  moods: ['flutter', 'joy'],
  text: '처음 본 사람들인데 전시 얘기로 두 시간이 금방 갔다. 서진 님이 추천한 전시, 다음 주엔 꼭 가보기. 돌아오는 길이 이상하게 따뜻했다.',
  remember: ['서진', '윤아'],
  people: ['서진', '가람', '윤아', '현우'],
}
