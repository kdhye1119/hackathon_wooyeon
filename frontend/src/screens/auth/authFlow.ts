// 로그인/가입 단계 사이에서 공유하는 입력값 (새로고침 시 처음부터)
export const flow: { phone: string; code: string } = { phone: '', code: '' }

const KEY = 'wt.registeredPhones'
const DEMO_REGISTERED = ['01012345678'] // 프로토타입 시연용 가입된 번호

const digits = (p: string) => p.replace(/\D/g, '')

function load(): string[] {
  try {
    return JSON.parse(localStorage.getItem(KEY) ?? '[]')
  } catch {
    return []
  }
}

export function isRegistered(phone: string) {
  const d = digits(phone)
  return DEMO_REGISTERED.includes(d) || load().includes(d)
}

export function markRegistered(phone: string) {
  const list = load()
  const d = digits(phone)
  if (!list.includes(d)) {
    try {
      localStorage.setItem(KEY, JSON.stringify([...list, d]))
    } catch {
      /* noop */
    }
  }
}
