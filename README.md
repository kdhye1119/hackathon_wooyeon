# hackathon_wooyeon
우연한식탁 해커톤 레포입니다

| 폴더 / 파일 | 내용 |
|---|---|
| `backend/` | Spring Boot 4.1 · Java 21 · MySQL 8 API 서버 (자동 매칭·단체방·결제·알림 자동화) — 실행법은 `backend/README.md` |
| `frontend/` | Vite · React 19 · TypeScript 참가자 앱(`/`) + 운영자 어드민(`/admin`) |
| `우연한식탁PRD.md` | 제품 요구사항 |
| `faq.md` | 참가자 FAQ (환불·멤버십·노쇼 정책) |
| `mbti.md` | MBTI 궁합표 — `backend/.../matching/engine/MbtiCompatibility.java` 에 그대로 반영 |
| `우연한식탁_가상데이터.xlsx` | 시연용 가상 참가자 데이터 (`backend/scripts/import_virtual_data.py` 로 적재) |

## 실행

```bash
# 백엔드 (MySQL 필요)
cd backend
cp config/local.example.yml config/local.yml   # DB 비밀번호 입력 (git 에 올라가지 않음)
./gradlew bootRun                              # http://localhost:8080, Swagger: /swagger-ui.html

# 프론트엔드
cd frontend
npm install
npm run dev                                    # http://localhost:5173 (API 는 8080 으로 프록시)
```

## 테스트 계정

- 참가자: `01000000001` / `test1234`
- 운영자: `admin` / `admin1234`

## 시연 도구

데스크탑 너비(1100px 이상)에서 참가자 앱 왼쪽에 **시연 도구** 패널이 보입니다.

- **시나리오 만들기**: 매칭 성공 · 인원 부족 실패 · 나이 차이 실패 · 멤버십 실패 · 2지망 자동 재배치 · 결제 대기
  — 새 회차에 로그인한 참가자를 신청시키고 매칭 시각으로 서버 시각을 옮겨 결과 화면으로 바로 이동합니다.
- **시간 건너뛰기**: 신청 마감 → 자동 매칭, 1일 전 리마인드, 체크인, 대화 주제 게시, 행사 종료 정산
