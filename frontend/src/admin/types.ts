// 운영자 API 응답 (backend AdminDtos 등)
export type Kpi = {
  totalApplications: number
  paidApplications: number
  paymentConversionRate: number
  confirmedParticipants: number
  matchSuccessRate: number
  confirmedTables: number
  autoConfirmRate: number
  reallocatedAttempts: number
  reallocationSuccessRate: number
  alternativeTransferRate: number
  autoRefundRate: number
  attendanceRate: number
  noShowRate: number
  feedbackResponseRate: number
  revisitRate: number
  operatorInterventionRate: number
}
export type Pending = {
  awaitingResolution: number
  unresolvedConflicts: number
  tablesWithoutVenue: number
  messageFailures: number
  refundFailures: number
  lateCancelOrNoShowCandidates: number
  openReports: number
  openExceptions: number
}
export type SlotSummary = { slotId: number; label: string; status: string; matchAt: string; applicants: number; paid: number; tables: number; confirmed: number; failed: number }
export type Dashboard = { kpi: Kpi; pending: Pending; slots: SlotSummary[]; now: string }

export type ApplicantRow = {
  applicationId: number
  userId: number
  name: string
  phone: string
  instagramId: string | null
  birthYear: number | null
  gender: string | null
  job: string | null
  mbti: string | null
  slotLabels: string[]
  otherRegion: string | null
  product: string | null
  appStatus: string
  paymentStatus: string
  matchStatus: string
  attendanceStatus: string
  tableId: number | null
  tableCode: string | null
  reallocationCount: number
  statusReason: string | null
  pastAttended: number
  noShows: number
  reportsReceived: number
  submittedAt: string
}

export type AdminSlot = {
  id: number
  date: string
  startTime: string
  region: string
  regionLabel: string
  label: string
  capacity: number
  applicants: number
  applyDeadline: string
  matchAt: string
  refundDeadline: string
  status: string
  open: boolean
}

export type Venue = {
  id: number
  name: string
  region: string
  regionLabel: string
  address: string
  mapUrl: string | null
  phone: string | null
  priceRange: string
  priceGuide: string | null
  seatsPerTable: number
  tablesPerSlot: number
  active: boolean
  photoUrls?: string[]
}

export type Policy = {
  ageWeight: number
  genderWeight: number
  mbtiWeight: number
  interestWeight: number
  desireWeight: number
  newnessWeight: number
  minScore: number
  maxAgeGap: number
  minTableSize: number
  maxTableSize: number
  updatedAt: string | null
  updatedBy: string | null
}

export type TableRow = {
  tableId: number
  tableCode: string
  slotId: number
  slotLabel: string
  status: string
  memberCount: number
  totalScore: number
  ageGap: number
  male: number
  female: number
  venueName: string | null
  venueMissing: boolean
  formation: string
  confirmedAt: string | null
}

export type MemberDetail = {
  applicationId: number
  userId: number
  name: string
  phone: string
  age: number
  gender: string
  mbti: string
  job: string
  interests: string[]
  traits: string[]
  desiredTypes: string[]
  priceRanges: string[]
  foodRestrictions: string | null
  slotSatisfied: boolean
  desireSatisfied: boolean
  metBeforeWith: string[]
  reallocationCount: number
  placementReason: string | null
  matchStatus: string
  attendanceStatus: string
  confirmNotification: string | null
}

export type TableDetail = {
  tableId: number
  tableCode: string
  slotId: number
  slotLabel: string
  status: string
  formation: string
  autoConfirmed: boolean
  memberCount: number
  scores: Record<string, number>
  minBirthYear: number
  maxBirthYear: number
  ageGap: number
  male: number
  female: number
  mbtiSummary: string
  commonInterests: string[]
  metBeforePairs: number
  exclusionConflict: boolean
  reallocatedMembers: number
  curation: string | null
  confirmedAt: string | null
  venue: { venueId: number; name: string; address: string; reservationName: string | null; priceRange: string | null } | null
  chatRoomId: number | null
  chatMessages: number
  scheduledPosts: { kind: string; scheduledAt: string; status: string; attempts: number; lastError: string | null }[]
  members: MemberDetail[]
  matchRunId: number | null
}

export type MatchRun = {
  id: number
  slotLabels: string[]
  trigger: string
  algorithmVersion: string
  policySnapshot: string
  candidates: number
  excluded: number
  initialTables: number
  splits: number
  merges: number
  reallocated: number
  dissolved: number
  confirmedTables: number
  confirmedParticipants: number
  failed: number
  startedAt: string
  finishedAt: string | null
  log: string[]
  tables: TableRow[]
  failedApplicants: ApplicantRow[]
}

export type ExceptionCase = {
  id: number
  type: string
  applicationId: number | null
  tableId: number | null
  slotId: number | null
  userId: number | null
  reason: string
  status: 'OPEN' | 'RESOLVED' | 'DISMISSED'
  adminNote: string | null
  resolvedBy: string | null
  createdAt: string
  resolvedAt: string | null
}

export type PaymentRow = {
  id: number
  userId?: number
  userName?: string
  product: string
  productLabel: string
  amount: number
  refundedAmount: number
  status: string
  method: string | null
  pgTxId: string | null
  failureReason: string | null
  createdAt: string
  paidAt: string | null
}

export type RefundRow = {
  refundId: number
  applicationId: number
  userId: number
  name: string
  amount: number
  status: string
  attempts: number
  failureReason: string | null
  reason: string | null
  requestedAt: string
  completedAt: string | null
}

export type AttendanceRow = {
  applicationId: number
  userId: number
  name: string
  phone: string
  slotLabel: string
  tableCode: string | null
  attendanceStatus: string
  checkedInAt: string | null
  canceledAt: string | null
  openReview: boolean
}

export type FeedbackSummary = {
  responses: number
  avgTable: number
  avgConversation: number
  avgVenue: number
  avgRejoin: number
  nps: number
  wantAgainCount: number
  avoidCount: number
  recent: { id: number; applicationId: number; userId: number; userName: string; tableId: number; tableRating: number; conversationRating: number; venueRating: number; rejoinIntent: number; recommendScore: number; comment: string | null; publicReview?: boolean; hidden?: boolean; likes?: number }[]
}

export type ReportDto = {
  id: number
  reporterId: number
  reporterName: string
  reportedId: number
  reportedName: string
  reportedAccountStatus: string
  tableId: number | null
  category: string
  detail: string | null
  evidenceUrl: string | null
  status: string
  action: string | null
  adminNote: string | null
  createdAt: string
  handledAt: string | null
}

export type Template = { id: number; code: string; title: string; body: string; updatedAt: string | null }
export type NotificationLog = { id: number; type: string; title: string; body: string; applicationId: number | null; tableId: number | null; status: string; attempts: number; scheduledAt: string; sentAt: string | null; readAt: string | null }
