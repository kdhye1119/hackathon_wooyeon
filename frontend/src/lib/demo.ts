// Figma 시안의 예시 데이터. 백엔드가 없을 때(또는 비로그인) 화면을 그대로 보여주기 위해 쓴다.
import type { ApplicationView, OpenSlotsResponse, SlotItem, TableInfo, TicketOption } from './types'

const slot = (id: number, date: string, region: string, assigned = false): SlotItem => ({
  id,
  label: `${date} ${region}`,
  region,
  startAt: `${date}T19:30:00`,
  matchAt: `${date}T00:00:00`,
  slotStatus: 'OPEN',
  assigned,
})

export const demoTable: TableInfo = {
  tableId: 7,
  tableCode: 'E-1022-3',
  roomId: 1,
  slotLabel: '10.22 (목) 19:30 을지로/종로',
  startAt: '2026-10-22T19:30:00',
  memberCount: 5,
  venueName: '을지 화로',
  address: '서울 중구 을지로 OO길 12, 2층',
  mapUrl: 'https://map.naver.com',
  reservationName: '우연한 식탁',
  priceGuide: '1인 2~3만원',
  curation: '숯불에 굽는 제철 생선과 솥밥',
  canCheckIn: false,
}

const base = {
  otherRegion: null,
  payment: { paymentId: 1, product: 'SINGLE', productLabel: '1회권', amount: 9900, ticketId: null },
  refundDeadline: '2026-10-27T23:59:59',
  cancelWillRefund: true,
  reallocationCount: 0,
  statusReason: null,
  resolution: null,
  transferredToId: null,
  transferredFromId: null,
  feedbackSubmitted: false,
  submittedAt: '2026-10-15T14:02:00',
} as const

export const demoApplications: ApplicationView[] = [
  {
    ...base,
    id: 101,
    appStatus: 'SUBMITTED',
    paymentStatus: 'PAID',
    matchStatus: 'CONFIRMED',
    attendanceStatus: 'SCHEDULED',
    statusTitle: '매칭 완료',
    statusMessage: '10.22 (목) 을지 화로에서 만나요',
    nextActions: ['VIEW_ROOM', 'CANCEL'],
    slots: [slot(11, '2026-10-22', 'EULJIRO_JONGNO', true)],
    matchAnnounceAt: '2026-10-20T00:00:00',
    table: demoTable,
  },
  {
    ...base,
    id: 102,
    appStatus: 'SUBMITTED',
    paymentStatus: 'PAID',
    matchStatus: 'WAITING',
    attendanceStatus: 'SCHEDULED',
    statusTitle: '매칭 대기',
    statusMessage: '매칭 결과는 10.27 (화) 24:00에 알려드려요',
    nextActions: ['CANCEL'],
    slots: [slot(21, '2026-10-29', 'HONGDAE_HAPJEONG'), slot(22, '2026-10-29', 'EULJIRO_JONGNO')],
    matchAnnounceAt: '2026-10-27T23:59:59',
    table: null,
  },
  {
    ...base,
    id: 100,
    appStatus: 'SUBMITTED',
    paymentStatus: 'PAID',
    matchStatus: 'CONFIRMED',
    attendanceStatus: 'ATTENDED',
    statusTitle: '참석 완료',
    statusMessage: '후기를 남겨주세요',
    nextActions: ['FEEDBACK'],
    slots: [slot(1, '2026-10-08', 'HONGDAE_HAPJEONG', true)],
    matchAnnounceAt: '2026-10-06T00:00:00',
    table: { ...demoTable, tableId: 3, roomId: 3, venueName: '연남 비스트로', startAt: '2026-10-08T19:30:00', slotLabel: '10.08 (목) 19:30 홍대/합정' },
  },
]

const date = (d: string, deadline: string) => ({ date: d, label: d, applyDeadline: `${deadline}T23:59:59`, matchAt: `${deadline}T23:59:59` })

export const demoSlots: OpenSlotsResponse = {
  dates: [date('2026-10-29', '2026-10-27'), date('2026-11-05', '2026-11-03'), date('2026-11-12', '2026-11-10'), date('2026-11-19', '2026-11-17')],
  regions: [
    { code: 'HONGDAE_HAPJEONG', label: '홍대/합정' },
    { code: 'EULJIRO_JONGNO', label: '을지로/종로' },
    { code: 'GANGNAM', label: '강남/신논현' },
    { code: 'JAMSIL', label: '잠실' },
  ],
  slots: [],
}

export const demoReviews = [
  { id: 1, text: '처음 본 사람들인데 두 시간이 순식간에 지나갔어요. 대화 주제 덕분에 어색할 틈이 없었어요.', likes: 23, likedByMe: true, nps: 10 },
  { id: 2, text: '혼자 신청해서 걱정했는데, 다들 비슷한 분위기라 편했어요.', likes: 12, likedByMe: false, nps: 9 },
  { id: 3, text: '식당 분위기도 좋고, 다음 주에 또 신청했어요.', likes: 8, likedByMe: false, nps: 8 },
]

/** 3-4 이용권 (Figma 시안) */
export const demoTickets: (TicketOption & { sub: string; tag?: string })[] = [
  { code: 'SINGLE', label: '1회권', price: 9900, count: 1, validDays: 60, sub: '1회 참여' },
  { code: 'MEMBERSHIP_1M', label: '멤버십 1개월', price: 35000, count: 0, validDays: 30, sub: '무제한 참여' },
  { code: 'MEMBERSHIP_3M', label: '멤버십 3개월', price: 75000, count: 0, validDays: 90, sub: '월 25,000원꼴', tag: '추천' },
  { code: 'MEMBERSHIP_6M', label: '멤버십 6개월', price: 120000, count: 0, validDays: 180, sub: '월 20,000원꼴', tag: '최대 할인' },
]

export const demoNotifications = [
  { id: 1, type: 'MATCH_CONFIRMED', title: '매칭이 확정됐어요', body: '10.22 (목) 을지 화로, 5명', at: '2026-10-20T23:59:00', read: false },
  { id: 2, type: 'REMINDER', title: '내일 저녁 7:30에 만나요', body: '을지 화로 입구 19:20', at: '2026-10-21T19:30:00', read: false },
  { id: 3, type: 'FEEDBACK_REQUEST', title: '10.08 모임 후기를 남겨주세요', body: '연남 비스트로', at: '2026-10-09T10:00:00', read: true },
  { id: 4, type: 'PAYMENT', title: '결제가 완료됐어요', body: '10.29 1회권 9,900원', at: '2026-10-19T14:02:00', read: true },
  { id: 5, type: 'OPEN', title: '11월 모집이 열렸어요', body: '11.05, 11.12 목요일', at: '2026-10-15T09:00:00', read: true },
]
