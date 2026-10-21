import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { AppBar, Body, Button, Footer, OtpInput, Screen, TextField, useToast } from '../../components/ui'
import { Icon } from '../../components/Icon'
import { ApiError, post, session, type LoginResponse } from '../../lib/api'
import { flow, markRegistered } from './authFlow'
import './auth.css'

const DEMO_PHONE = '010-1234-5678'
const phoneOrDemo = () => flow.phone || DEMO_PHONE

const digitsOf = (p: string) => p.replace(/\D/g, '')

/** 인증번호 받기. 문자 발송 수단이 없는 해커톤이라 서버가 돌려준 번호를 토스트로 알려준다 */
async function requestOtp(phone: string, purpose: 'SIGNUP' | 'RESET', toast: { show: (m: string) => void }) {
  try {
    const r = await post<{ expiresInSeconds: number; devCode: string | null }>('/api/auth/otp', { phone: digitsOf(phone), purpose })
    toast.show(r.devCode ? `인증번호 ${r.devCode} (시연용, 문자 대신 표시)` : '인증번호를 보냈어요')
  } catch (e) {
    // 백엔드가 없으면 시안 흐름 (아무 6자리나 통과)
    if (e instanceof ApiError && e.status < 500) toast.show(e.message)
  }
}

/** 로그인·가입 API 호출 후 다음 화면으로. 백엔드에 닿지 않으면 시안 세션 */
function useLogin() {
  const navigate = useNavigate()
  const toast = useToast()
  const [busy, setBusy] = useState(false)
  const login = async (phone: string, call: () => Promise<LoginResponse> = () => post<LoginResponse>('/api/auth/login', { phone: digitsOf(phone) })) => {
    setBusy(true)
    try {
      const res = await call()
      session.save(res)
      markRegistered(phone)
      navigate(res.profileComplete ? '/' : '/onboarding', { replace: true })
    } catch (e) {
      if (!(e instanceof ApiError) || e.status >= 500) {
        // 백엔드에 닿지 않으면 시안 데이터로 둘러보는 데모 세션
        session.save({ token: 'demo', role: 'USER', id: 0, name: null, profileComplete: false, consented: false, homePath: '/' })
        markRegistered(phone)
        navigate('/onboarding', { replace: true })
        return
      }
      toast.show(e.message)
    } finally {
      setBusy(false)
    }
  }
  return { login, busy, toast }
}

/** 1-5 비밀번호 입력 (가입된 번호) */
export function PasswordLogin() {
  const navigate = useNavigate()
  const [pw, setPw] = useState('')
  const { login, busy, toast } = useLogin()
  return (
    <Screen>
      <AppBar />
      <Body className="auth-body">
        <h2 className="t-display-m">로그인</h2>
        <TextField
          label="휴대폰 번호"
          value={phoneOrDemo()}
          readOnly
          action={
            <button type="button" className="field-action" onClick={() => navigate('/start')}>
              변경
            </button>
          }
        />
        <TextField label="비밀번호" type="password" value={pw} onChange={(e) => setPw(e.target.value)} autoFocus autoComplete="current-password" />
        <button className="auth-link" onClick={() => navigate('/login/reset')}>
          비밀번호 찾기
        </button>
      </Body>
      <Footer>
        <Button disabled={!pw || busy} onClick={() => login(phoneOrDemo(), () => post<LoginResponse>('/api/auth/login', { phone: digitsOf(phoneOrDemo()), password: pw }))}>
          로그인
        </Button>
      </Footer>
      {toast.node}
    </Screen>
  )
}

function useCountdown(seconds: number) {
  const [left, setLeft] = useState(seconds)
  useEffect(() => {
    if (left <= 0) return
    const t = window.setTimeout(() => setLeft(left - 1), 1000)
    return () => window.clearTimeout(t)
  }, [left])
  return { left, label: `${Math.floor(left / 60)}:${String(left % 60).padStart(2, '0')}`, reset: () => setLeft(seconds) }
}

/** 1-6 휴대폰 인증 (새 번호) */
export function PhoneVerify() {
  const navigate = useNavigate()
  const [code, setCode] = useState('')
  const timer = useCountdown(180)
  const toast = useToast()
  useEffect(() => {
    requestOtp(phoneOrDemo(), 'SIGNUP', toast)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])
  return (
    <Screen>
      <AppBar />
      <Body className="auth-body">
        <span className="step-pill">회원가입 1 / 2</span>
        <h2 className="t-display-m">휴대폰 인증</h2>
        <p className="t-body-s muted">{phoneOrDemo()}</p>
        <OtpInput value={code} onChange={setCode} />
        <div className="row">
          <span className="t-label-m c-terracotta">{timer.label}</span>
          <span className="spacer" />
          <button
            className="t-body-s muted underline"
            onClick={() => {
              timer.reset()
              setCode('')
              requestOtp(phoneOrDemo(), 'SIGNUP', toast)
            }}
          >
            재전송
          </button>
        </div>
      </Body>
      <Footer>
        {/* 인증번호는 가입 요청 때 서버가 확인한다 */}
        <Button
          disabled={code.length < 6 || timer.left === 0}
          onClick={() => {
            flow.code = code
            navigate('/signup/password')
          }}
        >
          확인
        </Button>
      </Footer>
      {toast.node}
    </Screen>
  )
}

function PwRules({ pw, confirm }: { pw: string; confirm: string }) {
  const rules = [
    ['8자 이상', pw.length >= 8],
    ['영문 + 숫자 포함', /[a-zA-Z]/.test(pw) && /\d/.test(pw)],
    ['두 비밀번호 일치', pw.length > 0 && pw === confirm],
  ] as const
  return (
    <ul className="pw-rules">
      {rules.map(([label, ok]) => (
        <li key={label} className={ok ? 'ok' : undefined}>
          <Icon name="check" size={16} />
          {label}
        </li>
      ))}
    </ul>
  )
}
const pwValid = (pw: string, c: string) => pw.length >= 8 && /[a-zA-Z]/.test(pw) && /\d/.test(pw) && pw === c

/** 1-7 비밀번호 설정 → 02 온보딩 */
export function PasswordSetup() {
  const [pw, setPw] = useState('')
  const [confirm, setConfirm] = useState('')
  const { login, busy, toast } = useLogin()
  return (
    <Screen>
      <AppBar />
      <Body className="auth-body">
        <span className="step-pill">회원가입 2 / 2</span>
        <h2 className="t-display-m">비밀번호 설정</h2>
        <TextField label="비밀번호" type="password" value={pw} onChange={(e) => setPw(e.target.value)} autoFocus autoComplete="new-password" />
        <TextField label="비밀번호 확인" type="password" value={confirm} onChange={(e) => setConfirm(e.target.value)} autoComplete="new-password" />
        <PwRules pw={pw} confirm={confirm} />
      </Body>
      <Footer>
        <Button
          disabled={!pwValid(pw, confirm) || busy}
          onClick={() => login(phoneOrDemo(), () => post<LoginResponse>('/api/auth/signup', { phone: digitsOf(phoneOrDemo()), code: flow.code, password: pw }))}
        >
          다음
        </Button>
      </Footer>
      {toast.node}
    </Screen>
  )
}

/** 1-8 비밀번호 찾기 (재설정) */
export function PasswordReset() {
  const navigate = useNavigate()
  const [phone] = useState(phoneOrDemo())
  const [code, setCode] = useState('')
  const [pw, setPw] = useState('')
  const [confirm, setConfirm] = useState('')
  const [busy, setBusy] = useState(false)
  const toast = useToast()
  useEffect(() => {
    requestOtp(phone, 'RESET', toast)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])
  const submit = async () => {
    setBusy(true)
    try {
      await post('/api/auth/password/reset', { phone: digitsOf(phone), code, password: pw })
    } catch (e) {
      if (e instanceof ApiError && e.status < 500) {
        toast.show(e.message)
        setBusy(false)
        return
      }
      // 백엔드가 없으면 시안 흐름
    }
    toast.show('비밀번호를 변경했어요')
    window.setTimeout(() => navigate('/login/password', { replace: true }), 900)
  }
  return (
    <Screen>
      <AppBar />
      <Body className="auth-body" style={{ gap: 18, padding: '12px 20px 24px' }}>
        <h2 className="t-display-m">비밀번호 재설정</h2>
        <TextField label="휴대폰 번호" value={phone} readOnly height={52} className="gap-6" />
        <div className="field gap-6">
          <span className="field-label">인증번호</span>
          <OtpInput value={code} onChange={setCode} cellWidth={44} height={52} />
        </div>
        <TextField label="새 비밀번호" type="password" value={pw} onChange={(e) => setPw(e.target.value)} height={52} className="gap-6" />
        <TextField label="새 비밀번호 확인" type="password" value={confirm} onChange={(e) => setConfirm(e.target.value)} height={52} className="gap-6" />
      </Body>
      <Footer>
        <Button
          disabled={code.length < 6 || !pwValid(pw, confirm) || busy}
          onClick={submit}
        >
          비밀번호 변경
        </Button>
      </Footer>
      {toast.node}
    </Screen>
  )
}
