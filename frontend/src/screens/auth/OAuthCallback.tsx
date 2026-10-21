import { useEffect, useRef, useState } from 'react'
import { useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { Body, Button, Screen } from '../../components/ui'
import { get, post, session } from '../../lib/api'

type LoginResponse = Parameters<typeof session.save>[0]
const STATE_KEY = 'wt.oauthState'

/** 소셜 로그인 시작: 키가 설정돼 있으면 카카오/네이버 로그인 화면으로 이동 */
export async function startOAuth(provider: 'kakao' | 'naver', toast: { show: (m: string) => void }) {
  const state = Math.random().toString(36).slice(2)
  try {
    sessionStorage.setItem(STATE_KEY, state)
  } catch {
    /* noop */
  }
  try {
    const r = await get<{ configured: boolean; url: string | null }>(`/api/auth/oauth/${provider}/url?state=${state}`)
    if (r.configured && r.url) window.location.assign(r.url)
    else toast.show(`${provider === 'kakao' ? '카카오' : '네이버'} 로그인 앱 키가 설정되면 사용할 수 있어요. 지금은 휴대폰 번호로 로그인해 주세요`)
  } catch {
    toast.show('소셜 로그인을 시작하지 못했어요')
  }
}

/** /oauth/:provider 콜백: 인가 코드로 로그인(처음이면 가입) */
export default function OAuthCallback() {
  const { provider } = useParams()
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const [error, setError] = useState<string | null>(null)
  const done = useRef(false)

  useEffect(() => {
    if (done.current) return
    done.current = true
    const code = params.get('code')
    const state = params.get('state')
    let saved: string | null = null
    try {
      saved = sessionStorage.getItem(STATE_KEY)
    } catch {
      /* noop */
    }
    if (!code || (saved && state !== saved)) {
      setError('로그인 요청이 올바르지 않아요. 다시 시도해 주세요')
      return
    }
    post<LoginResponse>(`/api/auth/oauth/${provider}`, { code, state })
      .then((r) => {
        session.save(r)
        navigate(r.profileComplete ? '/' : '/onboarding', { replace: true })
      })
      .catch((e) => setError(e instanceof Error ? e.message : '소셜 로그인에 실패했어요'))
  }, [params, provider, navigate])

  return (
    <Screen>
      <Body style={{ alignItems: 'center', justifyContent: 'center', gap: 16 }}>
        <p className="t-body-m">{error ?? '로그인하는 중이에요…'}</p>
        {error && <Button onClick={() => navigate('/start', { replace: true })}>처음으로</Button>}
      </Body>
    </Screen>
  )
}
