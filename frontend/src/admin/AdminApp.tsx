import type { ReactNode } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import { adminSession } from '../lib/api'
import AdminLogin from './AdminLogin'
import Dashboard from './pages/Dashboard'
import Slots from './pages/Slots'
import { RegionForm, Regions } from './pages/Regions'
import { VenueDetail, VenueForm, Venues } from './pages/Venues'
import Policy from './pages/Policy'
import Matching from './pages/Matching'
import TableDetail from './pages/TableDetail'
import Payments from './pages/Payments'
import Applicants from './pages/Applicants'
import Exceptions from './pages/Exceptions'
import Messages from './pages/Messages'
import Attendance from './pages/Attendance'
import Feedback from './pages/Feedback'

function RequireAdmin({ children }: { children: ReactNode }) {
  if (!adminSession.token) return <Navigate to="/admin/login" replace />
  return <>{children}</>
}

/** 운영자 웹 어드민 (/admin/*) */
export default function AdminApp() {
  return (
    <Routes>
      <Route path="login" element={<AdminLogin />} />
      <Route index element={<RequireAdmin><Dashboard /></RequireAdmin>} />
      <Route path="slots" element={<RequireAdmin><Slots /></RequireAdmin>} />
      <Route path="regions" element={<RequireAdmin><Regions /></RequireAdmin>} />
      <Route path="regions/new" element={<RequireAdmin><RegionForm /></RequireAdmin>} />
      <Route path="venues" element={<RequireAdmin><Venues /></RequireAdmin>} />
      <Route path="venues/new" element={<RequireAdmin><VenueForm /></RequireAdmin>} />
      <Route path="venues/:id" element={<RequireAdmin><VenueDetail /></RequireAdmin>} />
      <Route path="policy" element={<RequireAdmin><Policy /></RequireAdmin>} />
      <Route path="policy/edit" element={<RequireAdmin><Policy edit /></RequireAdmin>} />
      <Route path="matching" element={<RequireAdmin><Matching /></RequireAdmin>} />
      <Route path="tables/:id" element={<RequireAdmin><TableDetail /></RequireAdmin>} />
      <Route path="payments" element={<RequireAdmin><Payments /></RequireAdmin>} />
      <Route path="applicants" element={<RequireAdmin><Applicants /></RequireAdmin>} />
      <Route path="exceptions" element={<RequireAdmin><Exceptions /></RequireAdmin>} />
      <Route path="messages" element={<RequireAdmin><Messages /></RequireAdmin>} />
      <Route path="attendance" element={<RequireAdmin><Attendance /></RequireAdmin>} />
      <Route path="feedback" element={<RequireAdmin><Feedback /></RequireAdmin>} />
      <Route path="*" element={<Navigate to="/admin" replace />} />
    </Routes>
  )
}
