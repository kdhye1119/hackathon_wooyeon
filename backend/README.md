# 우연한 식탁 Backend

Spring Boot 4.1 · Java 21 · MySQL 8 · STOMP WebSocket · Swagger(springdoc)

## 실행

```bash
# 1) DB 비밀번호 설정 (git 에 올라가지 않음)
cp config/local.example.yml config/local.yml   # password 수정

# 2) 실행 (dinner DB 는 자동 생성)
./gradlew bootRun

# 테스트 (H2 MySQL 모드, DB 불필요)
./gradlew test
```

- Swagger: http://localhost:8080/swagger-ui.html (참가자 / 운영자 그룹 분리)
- 운영자 로그인: `admin` / `admin1234` → `POST /api/auth/admin/login`
- 참가자 로그인: 휴대폰 번호 테스트 로그인 → `POST /api/auth/login` (없으면 자동 가입)
- 최초 기동 시 다가오는 목요일 4주 × 4개 지역 회차, 파트너 식당 8곳, 알림 템플릿이 생성됩니다.

## 화면 분기

| 구분 | API prefix | 토큰 | 로그인 응답 `homePath` |
|---|---|---|---|
| 참가자 앱 | `/api/**` | `role=USER` | `/` |
| 운영자 어드민 | `/api/admin/**` | `role=ADMIN` (참가자 토큰은 403) | `/admin` |

## 주요 API

**참가자**
| 흐름 | API |
|---|---|
| 신청서 선택지 | `GET /api/meta/options` |
| 프로필·동의 | `GET/PUT /api/me/profile`, `POST /api/me/consents` |
| 회차 | `GET /api/slots` |
| 신청 → 결제 | `POST /api/applications` → `POST /api/payments/{id}/confirm` |
| 홈 상태 카드 | `GET /api/applications` (상태 문구 + `nextActions`) |
| 취소 / 매칭 실패 해결 | `POST /api/applications/{id}/cancel`, `POST /api/applications/{id}/resolution` |
| 단체방 | `GET /api/chat/rooms`, `GET /api/chat/rooms/{id}`, `GET /api/chat/rooms/{id}/messages` |
| 체크인·피드백·신고 | `POST /api/applications/{id}/check-in`, `.../feedback`, `POST /api/reports` |
| 이력·추천 | `GET /api/me/history`, `GET /api/me/recommendations` |

**운영자**: 대시보드 `/api/admin/dashboard`, 회차·식당, 신청자(CSV), 자동 매칭 모니터 `/api/admin/match-runs`,
테이블 상세 `/api/admin/tables/{id}`, 예외 처리함 `/api/admin/exceptions`, 결제·환불, 참석, 피드백·신고, 알림·템플릿, 매칭 정책.

## WebSocket (STOMP)

```
CONNECT  ws://localhost:8080/ws        header  Authorization: Bearer {token}
         (SockJS 필요 시 /ws-sockjs)
SUBSCRIBE /topic/rooms/{roomId}        단체방 메시지 (확정 참가자·운영자만 허용)
SUBSCRIBE /user/queue/notifications    개인 알림(푸시 대체)
SUBSCRIBE /user/queue/errors           전송 오류
SEND      /app/rooms/{roomId}/send     {"content": "..."}
```
재접속 시 누락분은 `GET /api/chat/rooms/{id}/messages?afterId=` 로 동기화합니다.

## 자동화

`AutomationScheduler` 가 10초마다 실행합니다.

1. 매칭 시각이 지난 회차를 자동 매칭·재배치·확정합니다. 회차를 먼저 선점하기 때문에 같은 회차가 두 번 매칭되지 않습니다.
2. 행사 1일 전 리마인드와 행사 시작 시각 대화 콘텐츠를 단체방에 게시합니다.
3. 행사가 끝나면 체크인하지 않은 참가자를 노쇼 후보로 정리하고 피드백 요청을 보냅니다.
4. 알림을 발송하고 실패하면 재시도합니다. 반복 실패한 건만 예외 처리함으로 넘어갑니다.

## 데모 시나리오 (Swagger, 운영자 토큰)

1. `POST /api/admin/demo/scenario`: 참가자 19명을 신청·결제까지 진행합니다. 분리·재배치·매칭 실패가 모두 나오도록 구성되어 있습니다.
2. `POST /api/admin/match-runs {"slotIds":[...]}`로 즉시 매칭합니다. 또는 `POST /api/admin/demo/clock`으로 시간을 이동한 뒤 `/tick`을 호출합니다.
3. `GET /api/admin/match-runs/{id}`에서 생성·분리·병합·재배치 로그를 확인합니다.
4. 응답의 `participants[].token`으로 참가자 화면(홈 카드·단체방)을 확인합니다.
5. clock `{"plusDays":2}` 다음 `/tick`을 호출하면 리마인드·대화 콘텐츠·노쇼·피드백 요청이 진행됩니다.
6. 장애 시연은 `POST /api/admin/demo/settings`로 합니다. 예: `{"failNextRefund":true}`를 설정하면 다음 환불이 실패하고 예외 처리함에 올라갑니다.

## 가상 데이터 넣기 (신청 폼 응답 엑셀)

서버를 켠 상태에서 저장소 루트에서 실행합니다. `openpyxl`이 필요합니다(`pip install openpyxl`).

```bash
python backend/scripts/import_virtual_data.py 우연한식탁_가상데이터.xlsx --capacity 48
```

- 참가자 앱과 같은 흐름(로그인 → 프로필 → 동의 → 신청 → 모의 결제)으로 넣습니다.
- 응답 날짜는 이미 지났기 때문에, 모집 중인 가장 가까운 목요일 회차 3개로 날짜별로 나눠 옮깁니다.
- 연락처는 `0107…` 가짜 번호를 씁니다. 다시 실행하면 같은 사람으로 로그인하고, 이미 신청한 회차는 건너뜁니다.
- `--capacity`는 대상 회차의 정원을 늘립니다. 기본 정원 24명으로는 일부 응답자가 정원 마감으로 빠집니다.

## 매칭 로직 요약

- **필수 조건(절대 완화 안 함)**: 날짜·지역 일치, 4~6명, 나이 차이 8살 이내, 차단·신고·비선호 관계 없음
- **선호 점수(가중합 0~100)**: 연령대 25 · 성비 20 · MBTI 15 · 관심사 15 · 만나고 싶은 사람 10 · 새로운 사람 15. 가중치는 운영자가 조정할 수 있습니다.
- 최소 점수 이상인 테이블은 운영자 승인 없이 자동 확정합니다.
- 재배치 순서(PRD 7.4): 같은 날짜·지역 → 같은 날짜·다른 지역 → 다른 날짜·같은 지역 → 다른 날짜·다른 지역
- 학습: '다시 만나고 싶은 사람'들의 성향을 개인 선호에 더합니다. 피드백이 쌓일수록 비중이 최대 50%까지 커집니다.
