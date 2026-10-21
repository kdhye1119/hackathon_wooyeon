// 운영자 화면 Figma 시안 데이터 (운영자 로그인 전·백엔드 미연결 시)
import type { AdminSlot, ApplicantRow, AttendanceRow, Dashboard, ExceptionCase, FeedbackSummary, MatchRun, NotificationLog, PaymentRow, Policy, RefundRow, ReportDto, SlotSummary, TableDetail, TableRow, Template, Venue } from './types'
import { fmt } from '../lib/useApi'

/** "10/22 (목)" → "10.22 (목)" */
export const slotDayLabel = (s: string) => {
  const [md, w] = s.split(' ')
  const [m, d] = md.split('/')
  return `${m}.${String(d).padStart(2, '0')}${w ? ` ${w}` : ''}`
}
/** "10/22 (목) 19:30 홍대/합정" → 지역 */
export const slotRegion = (label: string) => label.split(' ').slice(3).join(' ')

const STATUS_LABEL: Record<string, string> = {
  RECRUITING: '모집 중',
  MATCHING: '매칭 중',
  MATCHED: '매칭 완료',
  COMPLETED: '피드백 수집',
  CANCELED: '운영 취소',
}

const slot = (slotId: number, label: string, status: string, matchAt: string, applicants: number, paid: number, tables: number, confirmed: number, failed: number): SlotSummary => ({ slotId, label, status, matchAt, applicants, paid, tables, confirmed, failed })

const dashboard: Dashboard = {
  kpi: {
    totalApplications: 128,
    paidApplications: 112,
    paymentConversionRate: 88,
    confirmedParticipants: 96,
    matchSuccessRate: 86,
    confirmedTables: 19,
    autoConfirmRate: 100,
    reallocatedAttempts: 18,
    reallocationSuccessRate: 78,
    alternativeTransferRate: 40,
    autoRefundRate: 100,
    attendanceRate: 95,
    noShowRate: 3,
    feedbackResponseRate: 72,
    revisitRate: 41,
    operatorInterventionRate: 6,
  },
  pending: { awaitingResolution: 9, unresolvedConflicts: 2, tablesWithoutVenue: 1, messageFailures: 1, refundFailures: 0, lateCancelOrNoShowCandidates: 3, openReports: 1, openExceptions: 10 },
  slots: [
    slot(1, '10/22 (목) 19:30 홍대/합정', 'MATCHED', '2026-10-20T23:59:59', 46, 40, 7, 34, 3),
    slot(2, '10/22 (목) 19:30 을지로/종로', 'MATCHED', '2026-10-20T23:59:59', 38, 34, 6, 30, 3),
    slot(3, '10/22 (목) 19:30 강남/신논현', 'MATCHED', '2026-10-20T23:59:59', 27, 23, 4, 20, 2),
    slot(4, '10/22 (목) 19:30 잠실', 'MATCHED', '2026-10-20T23:59:59', 17, 15, 2, 12, 1),
    slot(5, '11/5 (목) 19:30 홍대/합정', 'RECRUITING', '2026-11-03T23:59:59', 30, 25, 0, 0, 0),
    slot(6, '11/5 (목) 19:30 을지로/종로', 'RECRUITING', '2026-11-03T23:59:59', 22, 18, 0, 0, 0),
    slot(7, '11/5 (목) 19:30 강남/신논현', 'RECRUITING', '2026-11-03T23:59:59', 14, 12, 0, 0, 0),
    slot(8, '11/5 (목) 19:30 잠실', 'RECRUITING', '2026-11-03T23:59:59', 8, 6, 0, 0, 0),
    slot(9, '11/12 (목) 19:30 홍대/합정', 'RECRUITING', '2026-11-10T23:59:59', 22, 17, 0, 0, 0),
    slot(10, '11/12 (목) 19:30 을지로/종로', 'RECRUITING', '2026-11-10T23:59:59', 16, 12, 0, 0, 0),
    slot(11, '10/15 (목) 19:30 홍대/합정', 'COMPLETED', '2026-10-13T23:59:59', 50, 46, 8, 40, 0),
    slot(12, '10/15 (목) 19:30 강남/신논현', 'COMPLETED', '2026-10-13T23:59:59', 38, 34, 5, 30, 0),
  ],
  now: '2026-10-21T09:00:00',
}

const applicants: ApplicantRow[] = [
  ['김서윤', '010-1234-5678', 1999, 'FEMALE', 'UX 디자이너', 'ENFP', ['10/22 (목) 19:30 을지로/종로'], 'SINGLE', 'PAID', 'CONFIRMED', 'SCHEDULED', 'E-1022-3', 0, 3, 0],
  ['이지민', '010-2345-6789', 1996, 'FEMALE', '마케터', 'INFJ', ['10/22 (목) 19:30 을지로/종로'], 'SINGLE', 'PAID', 'CONFIRMED', 'SCHEDULED', 'E-1022-3', 0, 1, 0],
  ['박도현', '010-3456-7890', 1997, 'MALE', '개발자', 'ESTP', ['10/22 (목) 19:30 을지로/종로'], 'TRIPLE', 'PAID', 'CONFIRMED', 'SCHEDULED', 'E-1022-3', 1, 2, 0],
  ['최하은', '010-4567-8901', 1995, 'FEMALE', '교사', 'ISFP', ['10/22 (목) 19:30 홍대/합정'], 'SINGLE', 'PAID', 'NO_MATCH', 'SCHEDULED', null, 2, 0, 0],
  ['정민재', '010-5678-9012', 1994, 'MALE', '회계사', 'ENTJ', ['10/22 (목) 19:30 강남/신논현'], 'SINGLE', 'PAID', 'REALLOCATING', 'SCHEDULED', null, 1, 4, 1],
  ['한유진', '010-6789-0123', 1998, 'FEMALE', '연구원', 'INTP', ['11/5 (목) 19:30 홍대/합정', '11/5 (목) 19:30 을지로/종로'], 'SINGLE', 'PENDING', 'WAITING', 'SCHEDULED', null, 0, 0, 0],
  ['오태오', '010-7890-1234', 1993, 'MALE', 'PD', 'ESFJ', ['11/5 (목) 19:30 잠실'], 'TRIPLE', 'PAID', 'WAITING', 'SCHEDULED', null, 0, 5, 0],
  ['윤소연', '010-8901-2345', 2000, 'FEMALE', '대학원생', 'ISTJ', ['10/22 (목) 19:30 홍대/합정'], 'SINGLE', 'REFUNDED', 'NO_MATCH', 'CANCELED_EARLY', null, 0, 0, 0],
].map(([name, phone, birthYear, gender, job, mbti, slotLabels, product, paymentStatus, matchStatus, attendanceStatus, tableCode, realloc, past, reports], i) => ({
  applicationId: 200 + i,
  userId: 100 + i,
  name: name as string,
  phone: phone as string,
  instagramId: null,
  birthYear: birthYear as number,
  gender: gender as string,
  job: job as string,
  mbti: mbti as string,
  slotLabels: slotLabels as string[],
  otherRegion: null,
  product: product as string,
  appStatus: paymentStatus === 'REFUNDED' ? 'CANCELED' : 'SUBMITTED',
  paymentStatus: paymentStatus as string,
  matchStatus: matchStatus as string,
  attendanceStatus: attendanceStatus as string,
  tableId: tableCode ? 7 : null,
  tableCode: tableCode as string | null,
  reallocationCount: realloc as number,
  statusReason: matchStatus === 'NO_MATCH' ? '나이 차이 조건을 만족하는 테이블 없음' : null,
  pastAttended: past as number,
  noShows: 0,
  reportsReceived: reports as number,
  submittedAt: `2026-10-${String(14 + (i % 6)).padStart(2, '0')}T1${i % 9}:00:00`,
}))

const adminSlots: AdminSlot[] = [
  ['2026-11-05', 'HONGDAE_HAPJEONG', '홍대/합정', 30, '2026-11-03T23:59:59', 'RECRUITING'],
  ['2026-11-05', 'EULJIRO_JONGNO', '을지로/종로', 22, '2026-11-03T23:59:59', 'RECRUITING'],
  ['2026-11-12', 'HONGDAE_HAPJEONG', '홍대/합정', 22, '2026-11-10T23:59:59', 'RECRUITING'],
  ['2026-11-12', 'EULJIRO_JONGNO', '을지로/종로', 16, '2026-11-10T23:59:59', 'RECRUITING'],
  ['2026-11-19', 'GANGNAM', '강남/신논현', 9, '2026-11-17T23:59:59', 'RECRUITING'],
  ['2026-11-19', 'JAMSIL', '잠실', 4, '2026-11-17T23:59:59', 'RECRUITING'],
  ['2026-10-22', 'HONGDAE_HAPJEONG', '홍대/합정', 46, '2026-10-20T23:59:59', 'MATCHED'],
  ['2026-10-22', 'EULJIRO_JONGNO', '을지로/종로', 38, '2026-10-20T23:59:59', 'MATCHED'],
].map(([date, region, regionLabel, applicants, matchAt, status], i) => {
  const d = new Date(date as string)
  return {
    id: i + 1,
    date: date as string,
    startTime: '19:30',
    region: region as string,
    regionLabel: regionLabel as string,
    label: `${d.getMonth() + 1}/${d.getDate()} (${'일월화수목금토'[d.getDay()]}) 19:30 ${regionLabel}`,
    capacity: 36,
    applicants: applicants as number,
    applyDeadline: matchAt as string,
    matchAt: matchAt as string,
    refundDeadline: matchAt as string,
    status: status as string,
    open: status === 'RECRUITING',
  }
})

const venues: Venue[] = [
  [1, '연남 비스트로', 'HONGDAE_HAPJEONG', '홍대/합정', '서울 마포구 연남로 23, 1층', 'P20_30', '1인 2~3만원', 6, 3, true],
  [2, '합정 소반', 'HONGDAE_HAPJEONG', '홍대/합정', '서울 마포구 양화로 45', 'P10_20', '1인 1~2만원', 6, 2, true],
  [3, '을지 화로', 'EULJIRO_JONGNO', '을지로/종로', '서울 중구 을지로 157, 2층', 'P20_30', '1인 2~3만원', 6, 4, true],
  [4, '종로 담', 'EULJIRO_JONGNO', '을지로/종로', '서울 종로구 종로 33', 'P30_PLUS', '1인 3만원 이상', 5, 2, true],
  [5, '신논현 키친', 'GANGNAM', '강남/신논현', '서울 강남구 봉은사로 102', 'P20_30', '1인 2~3만원', 6, 3, true],
  [6, '모래정원', 'JAMSIL', '잠실', '서울 송파구 백제고분로 41', 'P20_30', '1인 2~3만원', 6, 2, false],
].map(([id, name, region, regionLabel, address, priceRange, priceGuide, seatsPerTable, tablesPerSlot, active]) => ({
  id: id as number,
  name: name as string,
  region: region as string,
  regionLabel: regionLabel as string,
  address: address as string,
  mapUrl: 'https://map.naver.com',
  phone: '02-123-4567',
  priceRange: priceRange as string,
  priceGuide: priceGuide as string,
  seatsPerTable: seatsPerTable as number,
  tablesPerSlot: tablesPerSlot as number,
  active: active as boolean,
}))

const policy: Policy = { ageWeight: 25, genderWeight: 20, mbtiWeight: 15, interestWeight: 15, desireWeight: 10, newnessWeight: 15, minScore: 60, maxAgeGap: 8, minTableSize: 4, maxTableSize: 6, updatedAt: '2026-10-18T14:20:00', updatedBy: '운영자 김와썹' }

const tables: TableRow[] = [
  [7, 'E-1022-3', '10/22 (목) 19:30 을지로/종로', 5, 86, 6, 2, 3, '을지 화로', false, 'INITIAL'],
  [8, 'E-1022-1', '10/22 (목) 19:30 을지로/종로', 6, 82, 5, 3, 3, '종로 담', false, 'INITIAL'],
  [9, 'H-1022-2', '10/22 (목) 19:30 홍대/합정', 5, 74, 7, 2, 3, '연남 비스트로', false, 'MERGED'],
  [10, 'H-1022-4', '10/22 (목) 19:30 홍대/합정', 4, 63, 8, 1, 3, null, true, 'REALLOCATED'],
  [11, 'G-1022-1', '10/22 (목) 19:30 강남/신논현', 6, 79, 4, 3, 3, '신논현 키친', false, 'SPLIT'],
].map(([tableId, tableCode, slotLabel, memberCount, totalScore, ageGap, male, female, venueName, venueMissing, formation]) => ({
  tableId: tableId as number,
  tableCode: tableCode as string,
  slotId: 2,
  slotLabel: slotLabel as string,
  status: 'CONFIRMED',
  memberCount: memberCount as number,
  totalScore: totalScore as number,
  ageGap: ageGap as number,
  male: male as number,
  female: female as number,
  venueName: venueName as string | null,
  venueMissing: venueMissing as boolean,
  formation: formation as string,
  confirmedAt: '2026-10-21T00:00:12',
}))

const member = (applicationId: number, name: string, age: number, gender: string, mbti: string, job: string, interests: string[], realloc = 0, reason: string | null = null) => ({
  applicationId,
  userId: applicationId - 100,
  name,
  phone: '010-0000-0000',
  age,
  gender,
  mbti,
  job,
  interests,
  traits: ['깊은 대화를 좋아함'],
  desiredTypes: ['새로운 자극'],
  priceRanges: ['2~3만원'],
  foodRestrictions: null,
  slotSatisfied: true,
  desireSatisfied: true,
  metBeforeWith: [],
  reallocationCount: realloc,
  placementReason: reason,
  matchStatus: 'CONFIRMED',
  attendanceStatus: 'SCHEDULED',
  confirmNotification: 'SENT',
})

const tableDetail: TableDetail = {
  ...tables[0],
  formation: 'INITIAL',
  autoConfirmed: true,
  scores: { age: 22, gender: 20, mbti: 12, interest: 14, desire: 8, newness: 10 },
  minBirthYear: 1994,
  maxBirthYear: 2000,
  ageGap: 6,
  mbtiSummary: 'E 3 · I 2, N 3 · S 2',
  commonInterests: ['전시/공연/아트', '여행', '맛집/카페 탐방'],
  metBeforePairs: 0,
  exclusionConflict: false,
  reallocatedMembers: 1,
  curation: '전시, 여행, 맛집을 좋아하는 5명이고, 모두 처음 만나는 분들이에요',
  venue: { venueId: 3, name: '을지 화로', address: '서울 중구 을지로 157, 2층', reservationName: '우연한 식탁 E-1022-3', priceRange: '1인 2~3만원' },
  chatRoomId: 1,
  chatMessages: 14,
  scheduledPosts: [
    { kind: '확정 안내', scheduledAt: '2026-10-21T00:01:00', status: 'SENT', attempts: 1, lastError: null },
    { kind: '리마인드', scheduledAt: '2026-10-21T19:30:00', status: 'SCHEDULED', attempts: 0, lastError: null },
    { kind: '대화 주제', scheduledAt: '2026-10-22T19:30:00', status: 'SCHEDULED', attempts: 0, lastError: null },
  ],
  members: [
    member(200, '김서윤', 27, 'FEMALE', 'ENFP', 'UX 디자이너', ['전시/공연/아트', '여행']),
    member(201, '이지민', 30, 'FEMALE', 'INFJ', '마케터', ['여행', '맛집/카페 탐방']),
    member(202, '박도현', 29, 'MALE', 'ESTP', '개발자', ['맛집/카페 탐방'], 1, '11.05 회차에서 재배치'),
    member(203, '최하은', 31, 'FEMALE', 'ISFP', '교사', ['전시/공연/아트']),
    member(204, '정민재', 32, 'MALE', 'ENTJ', '회계사', ['여행', '전시/공연/아트']),
  ],
  matchRunId: 3,
}

const matchRuns: MatchRun[] = [
  {
    id: 3,
    slotLabels: dashboard.slots.slice(0, 4).map((s) => s.label),
    trigger: 'SCHEDULED',
    algorithmVersion: 'v1.2',
    policySnapshot: '',
    candidates: 112,
    excluded: 4,
    initialTables: 20,
    splits: 2,
    merges: 3,
    reallocated: 18,
    dissolved: 1,
    confirmedTables: 19,
    confirmedParticipants: 96,
    failed: 9,
    startedAt: '2026-10-21T00:00:02',
    finishedAt: '2026-10-21T00:00:12',
    log: ['후보 112명, 필수 조건 미충족 4명 제외', '초기 테이블 20개 생성', '6명 초과 테이블 2개 분리', '4명 미만 테이블 3개 병합', '18명 재배치 시도 → 14명 성공', '테이블 19개 자동 확정 (최소 점수 60)', '9명 매칭 실패 → 대체 일정/보관/환불 선택 요청'],
    tables,
    failedApplicants: applicants.filter((a) => a.matchStatus === 'NO_MATCH'),
  },
]

const exceptions: ExceptionCase[] = [
  ['UNRESOLVED_CONFLICT', '정민재: 나이 차이 8살 조건을 만족하는 테이블 없음', 204, 10],
  ['UNRESOLVED_CONFLICT', '최하은: 비선호 관계 회피 시 4명 미만', 203, 9],
  ['VENUE_SHORTAGE', '강남/신논현 11.05 식당 수용 테이블 부족 (필요 4, 보유 3)', null, 10],
  ['NOTIFICATION_FAILED', '리마인드 3회 발송 실패 (010-****-1234)', 205, null],
  ['NO_SHOW_REVIEW', '10.15 회차 체크인 기록 없음', 206, 5],
  ['NO_SHOW_REVIEW', '10.15 회차 체크인 기록 없음', 207, 5],
  ['NO_SHOW_REVIEW', '10.15 회차 당일 취소', 201, 5],
  ['SAFETY_REPORT', '불쾌한 언행 신고 접수 (18시간 경과)', 202, 7],
  ['LATE_CANCEL', '10.22 회차 당일 취소 요청', 200, 7],
  ['PAYMENT_ERROR', '결제 승인 후 신청 미생성', 206, null],
].map(([type, reason, applicationId, tableId], i) => ({
  id: i + 1,
  type: type as string,
  applicationId: applicationId as number | null,
  tableId: tableId as number | null,
  slotId: 2,
  userId: applicationId ? (applicationId as number) - 100 : null,
  reason: reason as string,
  status: 'OPEN' as const,
  adminNote: null,
  resolvedBy: null,
  createdAt: `2026-10-2${i % 2}T0${i % 9}:30:00`,
  resolvedAt: null,
}))

const payments: PaymentRow[] = applicants.map((a, i) => ({
  id: 300 + i,
  userId: a.userId,
  userName: a.name,
  product: a.product ?? 'SINGLE',
  productLabel: a.product === 'TRIPLE' ? '멤버십 1개월' : '1회권',
  amount: a.product === 'TRIPLE' ? 35000 : 9900,
  refundedAmount: a.paymentStatus === 'REFUNDED' ? 9900 : 0,
  status: a.paymentStatus,
  method: 'CARD',
  pgTxId: `PG-${20261014 + i}`,
  failureReason: null,
  createdAt: a.submittedAt,
  paidAt: a.paymentStatus === 'PENDING' ? null : a.submittedAt,
}))

const refunds: RefundRow[] = [
  { refundId: 1, applicationId: 207, userId: 107, name: '윤소연', amount: 9900, status: 'COMPLETED', attempts: 1, failureReason: null, reason: '매칭 실패 환불 선택', requestedAt: '2026-10-21T08:00:00', completedAt: '2026-10-21T08:00:03' },
  { refundId: 2, applicationId: 203, userId: 103, name: '최하은', amount: 9900, status: 'FAILED', attempts: 2, failureReason: 'PG 응답 시간 초과', reason: '매칭 실패 환불 선택', requestedAt: '2026-10-21T08:10:00', completedAt: null },
]

const attendance: AttendanceRow[] = [
  ['김서윤', 'CHECKED_IN', '2026-10-15T19:24:00', null, false],
  ['이지민', 'CANCELED_LATE', null, '2026-10-15T13:00:00', true],
  ['박도현', 'NO_SHOW', null, null, true],
  ['최하은', 'ATTENDED', '2026-10-15T19:18:00', null, false],
  ['정민재', 'NO_SHOW', null, null, true],
  ['한유진', 'CANCELED_EARLY', null, '2026-10-12T10:00:00', false],
].map(([name, st, inAt, canceledAt, openReview], i) => ({
  applicationId: 200 + i,
  userId: 100 + i,
  name: name as string,
  phone: `010-${1234 + i}-5678`,
  slotLabel: '10/15 (목) 19:30 홍대/합정',
  tableCode: `H-1015-${(i % 3) + 1}`,
  attendanceStatus: st === 'CHECKED_IN' ? 'ATTENDED' : (st as string),
  checkedInAt: inAt as string | null,
  canceledAt: canceledAt as string | null,
  openReview: openReview as boolean,
}))

const feedback: FeedbackSummary = {
  responses: 64,
  avgTable: 4.6,
  avgConversation: 4.4,
  avgVenue: 4.2,
  avgRejoin: 4.3,
  nps: 62,
  wantAgainCount: 38,
  avoidCount: 3,
  recent: [
    { id: 1, applicationId: 200, userId: 100, userName: '김서윤', tableId: 7, tableRating: 5, conversationRating: 5, venueRating: 4, rejoinIntent: 5, recommendScore: 10, comment: '처음 본 사람들인데 두 시간이 순식간에 지나갔어요.' },
    { id: 2, applicationId: 201, userId: 101, userName: '이지민', tableId: 7, tableRating: 4, conversationRating: 4, venueRating: 4, rejoinIntent: 3, recommendScore: 8, comment: '식당이 조금 시끄러웠어요.' },
    { id: 3, applicationId: 202, userId: 102, userName: '박도현', tableId: 8, tableRating: 5, conversationRating: 4, venueRating: 5, rejoinIntent: 5, recommendScore: 9, comment: '' },
  ],
}

const reports: ReportDto[] = [
  { id: 1, reporterId: 101, reporterName: '이지민', reportedId: 104, reportedName: '정민재', reportedAccountStatus: 'ACTIVE', tableId: 9, category: '불쾌한 언행', detail: '외모 관련 발언을 반복했어요', evidenceUrl: null, status: 'OPEN', action: null, adminNote: null, createdAt: '2026-10-20T15:00:00', handledAt: null },
  { id: 2, reporterId: 103, reporterName: '최하은', reportedId: 106, reportedName: '오태오', reportedAccountStatus: 'WARNED', tableId: 5, category: '연락 강요', detail: '개인 연락처를 계속 물어봤어요', evidenceUrl: null, status: 'HANDLED', action: 'WARNING', adminNote: '1차 경고', createdAt: '2026-10-16T10:00:00', handledAt: '2026-10-16T13:00:00' },
]

const templates: Template[] = [
  { id: 1, code: 'MATCH_CONFIRMED', title: '매칭이 확정됐어요', body: '{date} {venue}에서 {count}명이 함께해요. 단체방에서 인사 나눠보세요.', updatedAt: '2026-10-10T10:00:00' },
  { id: 2, code: 'REMINDER', title: '내일 저녁 {time}에 만나요', body: '{venue} 입구에서 {gatherTime}에 모여요. 예약명은 {reservation}이에요.', updatedAt: '2026-10-10T10:00:00' },
  { id: 3, code: 'MATCH_FAILED', title: '이번 회차는 매칭되지 않았어요', body: '다른 일정으로 옮기거나, 이용권을 보관하거나, 환불받을 수 있어요. {deadline}까지 선택해 주세요.', updatedAt: '2026-10-10T10:00:00' },
  { id: 4, code: 'FEEDBACK_REQUEST', title: '{date} 모임 후기를 남겨주세요', body: '별점과 사람별 선호는 공개되지 않고 다음 매칭에만 활용돼요.', updatedAt: '2026-10-10T10:00:00' },
]

const notifications: NotificationLog[] = [
  ['MATCH_CONFIRMED', '매칭이 확정됐어요', 'SENT', 1, '2026-10-21T00:01:00'],
  ['MATCH_FAILED', '이번 회차는 매칭되지 않았어요', 'SENT', 1, '2026-10-21T00:01:00'],
  ['REMINDER', '내일 저녁 7:30에 만나요', 'FAILED', 3, '2026-10-21T19:30:00'],
  ['FEEDBACK_REQUEST', '10.15 모임 후기를 남겨주세요', 'SENT', 1, '2026-10-16T10:00:00'],
].map(([type, title, status, attempts, at], i) => ({ id: i + 1, type: type as string, title: title as string, body: '', applicationId: 200 + i, tableId: 7, status: status as string, attempts: attempts as number, scheduledAt: at as string, sentAt: status === 'SENT' ? (at as string) : null, readAt: null }))

export const adminDemo = {
  dashboard,
  applicants,
  slots: adminSlots,
  venues,
  policy,
  tables,
  tableDetail,
  matchRuns,
  exceptions,
  payments,
  refunds,
  attendance,
  feedback,
  reports,
  templates,
  notifications,
  trend: {
    day: [9, 14, 11, 22, 18, 12, 25, 17].map((value, i) => ({ label: `10.${13 + i}`, value })),
    week: [42, 51, 48, 63, 70, 88, 104, 128].map((value, i) => ({ label: ['9.03', '9.10', '9.17', '9.24', '10.01', '10.08', '10.15', '10.15'][i], value })),
    month: [0, 74, 92, 77, 298, 412].map((value, i) => ({ label: `${5 + i}월`, value })),
  },
  kpiHead: { day: 17, week: 128, month: 412 },
  kpiDiff: { day: '4', week: '23%', month: '38%' },
  regions: [
    { label: '홍대/합정', value: 46 },
    { label: '을지로/종로', value: 38 },
    { label: '강남/신논현', value: 27 },
    { label: '잠실', value: 17 },
  ],
  resolutionDeadline: '기한 10.22 12:00',
  statusLabel: (s: string) => STATUS_LABEL[s] ?? s,
  runLabel: (s: SlotSummary) => {
    const x = fmt.dl(s.matchAt)
    const at = `${x.md} ${x.time}`
    if (s.status === 'COMPLETED') return '종료'
    if (s.status === 'RECRUITING') return `${at} 예정`
    return `${at} 실행 완료`
  },
}

/** 이용권 코드 → 이름·가격 (FAQ 기준). 신청 API 는 라벨, 결제 API 는 코드를 준다 */
const PRODUCTS: Record<string, [string, number]> = {
  SINGLE: ['1회권', 9900],
  MEMBERSHIP_1M: ['멤버십 1개월', 35000],
  MEMBERSHIP_3M: ['멤버십 3개월', 75000],
  MEMBERSHIP_6M: ['멤버십 6개월', 120000],
  TRIPLE: ['3회권', 15000],
}
export const productShort = (p: string | null) => (!p ? '—' : (PRODUCTS[p]?.[0] ?? p.split(' (')[0]))
export const productPrice = (p: string | null) => {
  const name = productShort(p)
  return Object.values(PRODUCTS).find(([n]) => n === name)?.[1] ?? 0
}
/** 성별은 코드(FEMALE/MALE) 또는 라벨(여/남) */
export const genderShort = (g: string | null) => (g === 'FEMALE' || g === '여' ? '여' : g === 'MALE' || g === '남' ? '남' : '—')

const REGION_ABBR: Record<string, string> = { '홍대/합정': 'HD', '을지로/종로': 'EJ', '강남/신논현': 'GN', 잠실: 'JS' }
/**
 * 테이블 표시 코드. 서버 코드는 회차마다 A, B, C… 로 반복되므로
 * Figma 표기(T-1022-EJ-3)처럼 날짜·지역을 붙인다. 이미 그 형식이면 그대로.
 */
export const tableLabel = (code: string | null | undefined, slotLabel?: string | null) => {
  if (!code) return '—'
  if (code.includes('-') || !slotLabel) return code
  const [md] = slotLabel.split(' ')
  const [m, d] = md.split('/')
  const region = slotRegion(slotLabel)
  return `T-${m.padStart(2, '0')}${d.padStart(2, '0')}-${REGION_ABBR[region] ?? 'XX'}-${code}`
}
