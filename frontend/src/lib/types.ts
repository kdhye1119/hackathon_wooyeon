// 백엔드 DTO (ApplicationView, SlotService, PaymentService …)
export type SlotItem = {
  id: number
  label: string
  region: string
  startAt: string
  matchAt: string
  slotStatus: string
  assigned: boolean
}

export type TableInfo = {
  tableId: number
  tableCode: string
  roomId: number | null
  slotLabel: string
  startAt: string
  memberCount: number
  venueName: string
  address: string
  mapUrl: string | null
  reservationName: string | null
  priceGuide: string | null
  curation: string | null
  canCheckIn: boolean
}

export type ResolutionInfo = {
  reason: string
  alternatives: SlotItem[]
  choice: string | null
  status: string
  newApplicationId: number | null
  refundAmount: number | null
  refundStatus: string | null
}

export type NextAction = 'PAY' | 'CANCEL' | 'VIEW_ROOM' | 'CHECK_IN' | 'FEEDBACK' | 'CHOOSE_RESOLUTION' | 'REAPPLY' | 'VIEW_NEW_APPLICATION'

export type ApplicationView = {
  id: number
  appStatus: 'DRAFT' | 'SUBMITTED' | 'CANCELED'
  paymentStatus: 'PENDING' | 'PAID' | 'FAILED' | 'REFUND_AVAILABLE' | 'REFUND_REQUESTED' | 'REFUND_PROCESSING' | 'REFUNDED' | 'REFUND_FAILED'
  matchStatus: 'WAITING' | 'MATCHING' | 'REALLOCATING' | 'CONFIRMED' | 'ALTERNATIVE_OFFERED' | 'TRANSFERRED' | 'NO_MATCH' | 'EXCEPTION'
  attendanceStatus: 'SCHEDULED' | 'ATTENDED' | 'CANCELED_EARLY' | 'CANCELED_LATE' | 'NO_SHOW'
  statusTitle: string
  statusMessage: string
  nextActions: NextAction[]
  slots: SlotItem[]
  otherRegion: string | null
  payment: { paymentId: number; product: string; productLabel: string; amount: number; ticketId: number | null } | null
  matchAnnounceAt: string | null
  refundDeadline: string | null
  cancelWillRefund: boolean
  reallocationCount: number
  statusReason: string | null
  table: TableInfo | null
  resolution: ResolutionInfo | null
  transferredToId: number | null
  transferredFromId: number | null
  feedbackSubmitted: boolean
  submittedAt: string
}

export type SlotDto = {
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

export type OpenSlotsResponse = {
  dates: { date: string; label: string; applyDeadline: string; matchAt: string }[]
  regions: { code: string; label: string }[]
  slots: SlotDto[]
}

export type SubmitResponse = { application: ApplicationView; paymentId: number | null; amount: number; paymentRequired: boolean }

export type PaymentDto = {
  id: number
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

export type TicketDto = {
  id: number
  product: string
  productLabel: string
  totalCount: number
  remainingCount: number
  expiresAt: string
  usable: boolean
}

export type TicketOption = { code: string; label: string; price: number; count: number; validDays: number }
