import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { motion, useReducedMotion } from 'framer-motion'
import { PhoneInput, SocialButton, useToast } from '../../components/ui'
import { flow, isRegistered } from './authFlow'
import { get } from '../../lib/api'
import plateOuterPaper from '../../assets/intro/plate-outer-paper.svg'
import plateInnerPaper from '../../assets/intro/plate-inner-paper.svg'
import forkPaper from '../../assets/intro/fork-paper.svg'
import spoonPaper from '../../assets/intro/spoon-paper.svg'
import laurelPaper from '../../assets/intro/laurel-paper.svg'
import tapRing from '../../assets/intro/tap-ring.svg'
import plateOuterInk from '../../assets/intro/plate-outer-ink.svg'
import plateInnerInk from '../../assets/intro/plate-inner-ink.svg'
import forkInk from '../../assets/intro/fork-ink.svg'
import spoonInk from '../../assets/intro/spoon-ink.svg'
import './intro.css'

const INTRO_SEEN = 'wt.introSeen'
const seen = () => {
  try {
    return localStorage.getItem(INTRO_SEEN) === '1'
  } catch {
    return false
  }
}

// 390 기준 x 좌표를 화면 가운데 기준으로 환산
const L = (x: number) => `calc(50% + ${x - 195}px)`

type Phase = 'idle' | 'tap' | 'expand' | 'login'

/** 1-1 인트로 → 1-2 접시 탭 → 1-3 원형 확장 → 1-4 로그인 */
export default function IntroLogin() {
  const reduce = useReducedMotion()
  // 재방문/로그인 사용자는 인트로 생략
  const [phase, setPhase] = useState<Phase>(() => (seen() ? 'login' : 'idle'))
  const [skipAnim] = useState(() => seen())
  const [phone, setPhone] = useState('')
  const navigate = useNavigate()
  const toast = useToast()

  const start = () => {
    if (phase !== 'idle') return
    try {
      localStorage.setItem(INTRO_SEEN, '1')
    } catch {
      /* noop */
    }
    if (reduce) {
      setPhase('login')
      return
    }
    setPhase('tap')
    window.setTimeout(() => setPhase('expand'), 250)
    window.setTimeout(() => setPhase('login'), 700)
  }

  const submitPhone = async () => {
    const digits = phone.replace(/\D/g, '')
    if (!/^01[0-9]{8,9}$/.test(digits)) {
      toast.show('휴대폰 번호를 정확히 입력해 주세요')
      return
    }
    flow.phone = phone
    // 가입 여부는 서버에 묻고, 서버가 꺼져 있으면 이 기기 기록·시연 번호로 판단
    const registered = await get<{ registered: boolean }>(`/api/auth/registered?phone=${digits}`)
      .then((r) => r.registered)
      .catch(() => isRegistered(phone))
    navigate(registered ? '/login/password' : '/signup/verify')
  }

  const tapped = phase !== 'idle'
  const revealed = phase === 'expand' || phase === 'login'
  const arrived = phase === 'login'
  const d = skipAnim ? 0 : 1 // 재방문 시 지연 없이

  return (
    <div className="intro">
      {/* ── 잉크 배경 인트로 레이어 ── */}
      <div className="intro-dark" aria-hidden={arrived}>
        <motion.div className="intro-brand" animate={{ opacity: tapped ? 0 : 1 }} transition={{ duration: 0.25, ease: 'easeIn' }}>
          <Divider top={206} width={132} seg={56} color="var(--paper)" />
          <p className="intro-title" style={{ top: 222 }}>
            우연한 식탁
          </p>
          <p className="intro-tagline" style={{ top: 290 }}>
            처음 보는 사람들과 좋은 저녁 한 끼
          </p>
          <Divider top={322} width={240} seg={110} color="var(--paper)" />
          <img className="abs" src={laurelPaper} style={{ left: L(150), top: 420, width: 90, height: 31 }} alt="" />
          <p className="intro-by" style={{ top: 460 }}>
            by Whatsup House
          </p>
          <p className="intro-hint" style={{ top: 730 }}>
            접시를 눌러 시작하기
          </p>
        </motion.div>

        {/* 대기: 링이 1.6초 주기로 숨쉬듯 */}
        {!tapped && (
          <motion.img
            className="abs"
            src={tapRing}
            alt=""
            style={{ left: L(162.2), top: 347.2, width: 65.6, height: 65.6 }}
            animate={reduce ? undefined : { scale: [1, 1.35], opacity: [1, 0] }}
            transition={{ duration: 1.6, repeat: Infinity, ease: 'easeOut' }}
          />
        )}

        <motion.div
          className="abs plate-group"
          style={{ left: L(161.4), top: 354.4, width: 67.2, height: 51.2 }}
          animate={{ scale: tapped ? 1.25 : 1 }}
          transition={{ duration: 0.25, ease: 'easeIn' }}
        >
          <img className="abs" src={plateOuterPaper} style={{ left: 12.8, top: 4.8, width: 41.6, height: 41.6 }} alt="" />
          <img className="abs" src={plateInnerPaper} style={{ left: 19.2, top: 11.2, width: 28.8, height: 28.8 }} alt="" />
          <motion.img
            className="abs"
            src={forkPaper}
            style={{ left: 0, top: 0, width: 11.2, height: 51.2 }}
            animate={{ x: tapped ? -60 : 0, opacity: tapped ? 0 : 1 }}
            transition={{ duration: 0.25, ease: 'easeIn' }}
            alt=""
          />
          <motion.img
            className="abs"
            src={spoonPaper}
            style={{ left: 56, top: 0, width: 11.2, height: 51.2 }}
            animate={{ x: tapped ? 60 : 0, opacity: tapped ? 0 : 1 }}
            transition={{ duration: 0.25, ease: 'easeIn' }}
            alt=""
          />
        </motion.div>

        <button className="abs plate-tap" style={{ left: L(135), top: 320, width: 120, height: 120 }} aria-label="접시를 눌러 시작하기" onClick={start} />
      </div>

      {/* ── 크림색 원이 접시에서 화면 전체로 (clip-path circle) ── */}
      <motion.div
        className="intro-login"
        initial={false}
        animate={{ clipPath: revealed ? 'circle(150% at 50% 380px)' : 'circle(0% at 50% 380px)' }}
        transition={{ duration: skipAnim ? 0 : reduce ? 0.2 : 0.45, ease: 'easeInOut' }}
        style={{ pointerEvents: arrived ? 'auto' : 'none' }}
      >
        {/* 엠블럼: 잉크색으로 페이드인, 수저 제자리로 */}
        <motion.div
          className="abs"
          style={{ left: L(161.4), top: 174.4, width: 67.2, height: 51.2 }}
          initial={false}
          animate={{ opacity: arrived ? 1 : 0 }}
          transition={{ duration: 0.4 * d, ease: 'easeOut' }}
        >
          <img className="abs" src={plateOuterInk} style={{ left: 12.8, top: 4.8, width: 41.6, height: 41.6 }} alt="" />
          <img className="abs" src={plateInnerInk} style={{ left: 19.2, top: 11.2, width: 28.8, height: 28.8 }} alt="" />
          <motion.img
            className="abs"
            src={forkInk}
            style={{ left: 0, top: 0, width: 11.2, height: 51.2 }}
            initial={false}
            animate={{ x: arrived ? 0 : -60 }}
            transition={{ duration: 0.4 * d, ease: 'easeOut' }}
            alt=""
          />
          <motion.img
            className="abs"
            src={spoonInk}
            style={{ left: 56, top: 0, width: 11.2, height: 51.2 }}
            initial={false}
            animate={{ x: arrived ? 0 : 60 }}
            transition={{ duration: 0.4 * d, ease: 'easeOut' }}
            alt=""
          />
        </motion.div>
        <motion.h1
          className="login-title"
          style={{ top: 246 }}
          initial={false}
          animate={{ opacity: arrived ? 1 : 0 }}
          transition={{ duration: 0.4 * d, ease: 'easeOut' }}
        >
          우연한 식탁
        </motion.h1>

        {/* 버튼: 40px 아래에서 60ms 간격으로 순차 등장 */}
        {[
          <div key="phone" className="abs" style={{ left: 20, right: 20, top: 394 }}>
            <PhoneInput value={phone} onChange={setPhone} onSubmit={submitPhone} />
          </div>,
          <div key="or" className="abs or-divider" style={{ left: 20, right: 20, top: 468 }}>
            <span />
            <em>또는</em>
            <span />
          </div>,
          <div key="social" className="abs social-row" style={{ top: 504 }}>
            <SocialButton provider="kakao" onClick={() => toast.show('카카오 로그인은 준비 중이에요')} />
            <SocialButton provider="naver" onClick={() => toast.show('네이버 로그인은 준비 중이에요')} />
          </div>,
          <button key="guest" className="abs guest-link" style={{ top: 584 }} onClick={() => navigate('/?guest=1')}>
            비회원으로 둘러보기
          </button>,
        ].map((el, i) => (
          <motion.div
            key={i}
            initial={false}
            animate={arrived ? { y: 0, opacity: 1 } : { y: 40, opacity: 0 }}
            transition={{ duration: 0.4 * d, ease: 'easeOut', delay: arrived ? i * 0.06 * d : 0 }}
            style={{ position: 'absolute', inset: 0, pointerEvents: 'none' }}
          >
            <div style={{ pointerEvents: 'auto' }}>{el}</div>
          </motion.div>
        ))}
      </motion.div>
      {toast.node}
    </div>
  )
}

function Divider({ top, width, seg, color }: { top: number; width: number; seg: number; color: string }) {
  return (
    <div className="abs intro-divider" style={{ top, left: `calc(50% - ${width / 2}px)`, width, ['--c' as string]: color }}>
      <span style={{ width: seg }} />
      <i />
      <span style={{ width: seg }} />
    </div>
  )
}
