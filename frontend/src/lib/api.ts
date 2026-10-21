// 백엔드(Spring Boot) REST 클라이언트. Vite dev 서버가 /api 를 :8080 으로 프록시한다.
const TOKEN_KEY = 'wt.token'
const USER_KEY = 'wt.user'
const ADMIN_TOKEN_KEY = 'wt.adminToken'

export type LoginResponse = {
  token: string
  role: 'USER' | 'ADMIN'
  id: number
  name: string | null
  profileComplete: boolean
  consented: boolean
  homePath: string
}

export class ApiError extends Error {
  status: number
  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

function safeGet(key: string) {
  try {
    return localStorage.getItem(key)
  } catch {
    return null
  }
}
function safeSet(key: string, v: string | null) {
  try {
    if (v === null) localStorage.removeItem(key)
    else localStorage.setItem(key, v)
  } catch {
    /* 저장소 차단 시 세션 유지만 포기 */
  }
}

/** 이 기기에 저장하는 참가자별 데이터(일기·지난 신청 등)는 계정마다 따로: 다른 계정 내역이 섞이지 않게 */
export function userKey(base: string) {
  const id = session.user?.id
  return id ? `${base}.${id}` : base
}

export const session = {
  get token() {
    return safeGet(TOKEN_KEY)
  },
  get user(): LoginResponse | null {
    const raw = safeGet(USER_KEY)
    return raw ? (JSON.parse(raw) as LoginResponse) : null
  },
  save(res: LoginResponse) {
    safeSet(TOKEN_KEY, res.token)
    safeSet(USER_KEY, JSON.stringify(res))
  },
  patch(p: Partial<LoginResponse>) {
    const u = session.user
    if (u) safeSet(USER_KEY, JSON.stringify({ ...u, ...p }))
  },
  clear() {
    safeSet(TOKEN_KEY, null)
    safeSet(USER_KEY, null)
  },
}

/** 운영자 세션은 참가자 세션과 따로 보관한다 (/api/admin/** 은 ADMIN 토큰) */
export const adminSession = {
  get token() {
    return safeGet(ADMIN_TOKEN_KEY)
  },
  /** 로그인한 운영자 이름 (로그인 응답) */
  get name() {
    return safeGet(ADMIN_TOKEN_KEY + '.name')
  },
  save(token: string, name?: string) {
    safeSet(ADMIN_TOKEN_KEY, token)
    safeSet(ADMIN_TOKEN_KEY + '.name', name ?? null)
  },
  clear() {
    safeSet(ADMIN_TOKEN_KEY, null)
    safeSet(ADMIN_TOKEN_KEY + '.name', null)
  },
}

export async function api<T>(path: string, init: RequestInit & { json?: unknown } = {}): Promise<T> {
  const headers = new Headers(init.headers)
  const token = path.startsWith('/api/admin') ? adminSession.token : session.token
  if (token) headers.set('Authorization', `Bearer ${token}`)
  let body = init.body
  if (init.json !== undefined) {
    headers.set('Content-Type', 'application/json')
    body = JSON.stringify(init.json)
  }
  const res = await fetch(path, { ...init, headers, body })
  if (!res.ok) {
    let message = `요청에 실패했어요 (${res.status})`
    try {
      const data = await res.json()
      message = data.message ?? data.detail ?? message
    } catch {
      /* 본문 없음 */
    }
    throw new ApiError(res.status, message)
  }
  if (res.status === 204) return undefined as T
  const text = await res.text()
  return (text ? JSON.parse(text) : undefined) as T
}

export const get = <T,>(path: string) => api<T>(path)
export const post = <T,>(path: string, json?: unknown) => api<T>(path, { method: 'POST', json: json ?? {} })
export const put = <T,>(path: string, json?: unknown) => api<T>(path, { method: 'PUT', json: json ?? {} })
export const del = <T,>(path: string) => api<T>(path, { method: 'DELETE' })

/** 인증이 필요한 파일(CSV 등) 내려받기: 토큰을 붙여 받아서 저장 */
export async function download(path: string, filename: string) {
  const token = path.startsWith('/api/admin') ? adminSession.token : session.token
  const res = await fetch(path, { headers: token ? { Authorization: `Bearer ${token}` } : {} })
  if (!res.ok) throw new ApiError(res.status, `내려받지 못했어요 (${res.status})`)
  const url = URL.createObjectURL(await res.blob())
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  URL.revokeObjectURL(url)
}
