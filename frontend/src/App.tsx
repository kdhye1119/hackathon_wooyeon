import type { ReactNode } from 'react'
import { BrowserRouter, Navigate, Route, Routes, useLocation } from 'react-router-dom'
import { session } from './lib/api'
import IntroLogin from './screens/auth/IntroLogin'
import { PasswordLogin, PasswordReset, PasswordSetup, PhoneVerify } from './screens/auth/AuthScreens'
import Onboarding from './screens/onboarding/Onboarding'
import Home, { GuestHome, ReviewsPage } from './screens/home/Home'
import OAuthCallback from './screens/auth/OAuthCallback'
import Notifications from './screens/home/Notifications'
import { ApplyDone, ApplyPayment, ApplyPreference, ApplySchedule } from './screens/apply/Apply'
import { MatchConfirmed, MatchFailed, MatchMoved, MatchRefunded } from './screens/match/Match'
import { ChatRoom, RoomList } from './screens/rooms/Rooms'
import { CalendarScreen, ReviewScreen } from './screens/records/Records'
import { HistoryPage, MyPage, TicketPage } from './screens/my/My'
import AdminApp from './admin/AdminApp'
import DemoPanel from './components/DemoPanel'

/** 로그인 전이면 인트로로 (비회원 둘러보기는 /guest 후기 화면만) */
function RequireSession({ children }: { children: ReactNode }) {
  if (!session.token) return <Navigate to="/start" replace />
  return <>{children}</>
}

function ParticipantApp() {
  return (
    <div className="app-shell">
      {/* 심사용 시연 도구: 넓은 화면의 빈 공간에만 보인다 */}
      <DemoPanel />
      <div className="app-frame">
        <Routes>
          <Route path="/start" element={<IntroLogin />} />
          <Route path="/guest" element={<GuestHome />} />
          <Route path="/oauth/:provider" element={<OAuthCallback />} />
          <Route path="/guest/reviews" element={<ReviewsPage guest />} />
          <Route path="/reviews" element={<RequireSession><ReviewsPage /></RequireSession>} />
          <Route path="/login/password" element={<PasswordLogin />} />
          <Route path="/login/reset" element={<PasswordReset />} />
          <Route path="/signup/verify" element={<PhoneVerify />} />
          <Route path="/signup/password" element={<PasswordSetup />} />
          <Route path="/onboarding" element={<Onboarding />} />

          <Route path="/" element={<RequireSession><Home /></RequireSession>} />
          <Route path="/notifications" element={<RequireSession><Notifications /></RequireSession>} />
          <Route path="/apply" element={<RequireSession><ApplyPreference /></RequireSession>} />
          <Route path="/apply/schedule" element={<RequireSession><ApplySchedule /></RequireSession>} />
          <Route path="/apply/pay" element={<RequireSession><ApplyPayment /></RequireSession>} />
          <Route path="/apply/done" element={<RequireSession><ApplyDone /></RequireSession>} />
          <Route path="/applications/:id" element={<RequireSession><ApplicationDetailRedirect /></RequireSession>} />
          <Route path="/today/:id" element={<RequireSession><Home /></RequireSession>} />
          <Route path="/match/moved" element={<RequireSession><MatchMoved /></RequireSession>} />
          <Route path="/match/refunded" element={<RequireSession><MatchRefunded /></RequireSession>} />
          <Route path="/match/:id" element={<RequireSession><MatchConfirmed /></RequireSession>} />
          <Route path="/match/:id/failed" element={<RequireSession><MatchFailed /></RequireSession>} />
          <Route path="/rooms" element={<RequireSession><RoomList /></RequireSession>} />
          <Route path="/rooms/:roomId" element={<RequireSession><ChatRoom /></RequireSession>} />
          <Route path="/review/:id" element={<RequireSession><ReviewScreen /></RequireSession>} />
          <Route path="/calendar" element={<RequireSession><CalendarScreen /></RequireSession>} />
          <Route path="/my" element={<RequireSession><MyPage /></RequireSession>} />
          <Route path="/my/history" element={<RequireSession><HistoryPage /></RequireSession>} />
          <Route path="/my/tickets" element={<RequireSession><TicketPage /></RequireSession>} />

          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </div>
    </div>
  )
}

/** 신청 상세는 3-5 신청 완료 화면을 재사용 */
function ApplicationDetailRedirect() {
  const id = useLocation().pathname.split('/').pop()
  return <Navigate to={`/apply/done?app=${id}`} replace />
}

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/admin/*" element={<AdminApp />} />
        <Route path="*" element={<ParticipantApp />} />
      </Routes>
    </BrowserRouter>
  )
}
