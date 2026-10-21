import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Button } from '../components/ui'
import { ApiError, adminSession, post, type LoginResponse } from '../lib/api'
import markPaper from '../assets/brand/mark-paper.svg'
import { Field } from './AdminLayout'
import './admin.css'

/** 운영자 로그인 (POST /api/auth/admin/login). 백엔드가 없으면 시안 데이터로 둘러본다. */
export default function AdminLogin() {
  const navigate = useNavigate()
  const [id, setId] = useState('admin')
  const [pw, setPw] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const login = async () => {
    setBusy(true)
    setError('')
    try {
      const res = await post<LoginResponse>('/api/auth/admin/login', { loginId: id, password: pw })
      adminSession.save(res.token, res.name ?? undefined)
      navigate('/admin', { replace: true })
    } catch (e) {
      if (e instanceof ApiError && e.status < 500) setError(e.message)
      else {
        adminSession.save('demo')
        navigate('/admin', { replace: true })
      }
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="adm-login">
      <form
        className="adm-login-card"
        onSubmit={(e) => {
          e.preventDefault()
          login()
        }}
      >
        <div className="row" style={{ gap: 10 }}>
          <span className="adm-login-mark">
            <img src={markPaper} width={34} height={34} alt="" />
          </span>
          <h1 className="t-h1">운영자 어드민</h1>
        </div>
        <Field label="아이디">
          <input value={id} onChange={(e) => setId(e.target.value)} autoComplete="username" />
        </Field>
        <Field label="비밀번호">
          <input type="password" value={pw} onChange={(e) => setPw(e.target.value)} autoComplete="current-password" autoFocus />
        </Field>
        {error && <p className="t-body-s c-terracotta">{error}</p>}
        <Button type="submit" disabled={busy || !id || !pw}>
          로그인
        </Button>
      </form>
    </div>
  )
}
