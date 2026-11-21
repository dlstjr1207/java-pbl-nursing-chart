# 진행 현황

기준일: 2026-11-16 · **1차 초안 완성본: 2026-11-30** · 최종 제출: 2026-12-14 (프로그램·시연 영상·설명 PPT)

## 현재 상태

| 항목 | 상태 |
|---|---|
| 기획 | 관찰 기록·투약 기록·SBAR, PC-01 한 명으로 범위 정리 |
| 문서 | 기능·화면·시나리오·작업 목록의 연결 정리. 임상 기준값과 점검 세부 조건은 확정 전 |
| 디자인 | Figma MVP 4화면, 로그인 프레임, 공통 가이드·컬러차트 확인. 환자 데이터와 입력·결과 화면 보완 필요 |
| 환자 데이터 | PC-01 초안. 화면 간 데이터 차이는 환자케이스의 C-01~C-10에서 관리 |
| 코드 | 기본 Swing 창. 실습 기능과 환자 JSON은 아직 없음 |
| 빌드·테스트 | 이전 기록에 Main.java 단독 컴파일 확인이 있음. Maven·CI·애플리케이션 검증은 미완료 |

## 다음 작업

1. PC-01의 환자·처방·시간별 데이터를 확정한다.
2. 정답 키와 모범 기록, 점검 적용 조건을 맞춘다.
3. 입력·결과·계정 화면과 최종 스타일을 확정한다.
4. Maven 환경과 기반 코드를 준비한다.

작업별 상태는 [작업 목록](docs/tasks.md)에 있다.

## 변경 기록

| 날짜 | 변경 | 확인 범위 |
|---|---|---|
| 2026-11-22 | T-12 실습 저장소(`app.storage`): `AttemptRepository`(save·findById·findByUser, 모든 조회에 userId) + `MySqlAttemptRepository`(PreparedStatement, 한 실습을 한 트랜잭션으로 저장: attempts·관찰·투약·SBAR·R 행·점검 결과) + `InMemoryAttemptRepository`. 정한 것: attemptId가 있으면 같은 사용자의 기록을 덮어쓰고 다른 사용자 것이면 IllegalArgumentException, NOT NULL 칸의 null은 빈 문자열로 저장, AI 해설은 JSON(status는 별도 칸), 목록은 시작 시각 최신순, DATETIME은 초 단위 | 공통 계약 테스트를 메모리·MySQL이 상속(저장·재조회 일치, 미제출 null 보존, 타 사용자 조회 불가, 덮어쓰기, 최신순). `./mvnw test` 72개 통과(MySQL 테스트 실제 실행) |
| 2026-11-22 | T-21a 개인 메인 화면: `HomePanel`(실습 시작 버튼, AI 생성 버튼 비활성+이유 문구, 기록 영역 자리), 로직은 Swing 없는 `HomeController`(기본 케이스 PC-01, 없으면 DEMO, 읽기 실패 시 오류 문구·화면 유지), `Main`에 연결. 정한 것: AI 생성 가능 여부는 T-31 전까지 항상 false. 새 실습 ID 생성은 T-12(AttemptRepository)에서 함 | `./mvnw test` 통과(HomeControllerTest 5개). 화면은 띄워 보지 못함 |
| | 직접 확인할 것: 로그인 후 개인 메인에 실습 시작 버튼, 비활성 AI 생성 버튼과 이유 문구가 보이는지, 실습 시작을 누르면 환자 정보 화면으로 가고 상단에 `DEMO · 실제 케이스 아님`이 표시되는지, 로그아웃이 되는지 | |
| 2026-11-22 | T-16 로그인·회원가입 화면: `LoginPanel`·`SignupPanel`(Swing, 오류는 칸 옆 표시), 검증·이동은 Swing 없는 `AuthFormController`, 시작 시 DB 설정·연결 확인 `StartupCheck`(실패 시 원인 안내 패널, 비밀번호 미출력), `Main`에 연결, 로그아웃 버튼은 `AuthService.logout` 호출. 정한 것: 비밀번호 확인 불일치는 가입 전에 검사해 계정을 만들지 않음, 로그인 후 비밀번호 칸은 항상 비움 | `./mvnw test` 통과(AuthFormControllerTest 7개, 메모리 저장소). 화면 자체는 띄워 보지 못함 |
직접 확인할 것: `./mvnw compile exec:java`로 로그인 화면 표시, 빈 칸·틀린 비밀번호 문구, 회원가입 칸별 오류, 가입 후 로그인 화면 안내와 개인 메인 이동, `config/db.properties` 없을 때 안내 화면, 로그아웃 후 로그인 화면 복귀
| 2026-11-22 | T-15 `AuthService`(`app.auth`): 가입(검증→솔트·해시→저장, 중복은 loginId 칸 오류)·로그인(실패 문구 하나로 통일)·로그아웃(사용자와 `AppState` 케이스·화면 상태 제거), `currentUser()`로 기록 조회의 사용자 id 제공. 정한 것: 가입은 자동 로그인하지 않음, 실패한 로그인은 기존 로그인 상태를 유지, 기록 분리 자체(사용자별 조회)는 T-12에서 이 id로 구현 | `./mvnw test` 통과(AuthServiceTest 8개, 메모리 저장소) |
| 2026-11-22 | T-14 계정 저장(`app.auth`·`app.storage`): `UserRepository` 인터페이스 + `MySqlUserRepository`(PreparedStatement) + `InMemoryUserRepository`, `PasswordHasher`(PBKDF2-HMAC-SHA256 120000회·사용자별 16바이트 솔트, 시간 일정 비교), `SignupValidator`(D23, 칸별 오류), `DuplicateLoginIdException`. 정한 것: 해시 반복 횟수는 코드 상수, `User.toString`은 해시·솔트를 출력하지 않음, 두 구현이 같은 계약 테스트를 공유 | `./mvnw test` 통과(메모리 3개·MySQL 통합 3개(이 PC의 테스트 DB에서 실행됨)·해시/가입 검증 3개) |
| 2026-11-22 | T-20 공통 틀(`app.ui`): `MainFrame`(JFrame 하나·JMenuBar·왼쪽 메뉴·CardLayout·JScrollPane·FlatLaf 라이트), `AppState`(로그인·케이스·화면 이동 규칙, 상태줄 문구, `교육용 예시 · 임상 검수 전`·DEMO·AI 문구), `Screen`. `app.Main`이 이 창을 띄움. 정한 것: 각 화면은 자리 표시 패널이며 이후 작업이 `register()`로 교체, 개인 메인으로 돌아가면 실습 종료, 로그인 상태에서 로그인 화면 이동 불가. 직접 확인할 것: `./mvnw compile exec:java`로 창이 뜨는지, 상단 상태줄·왼쪽 메뉴(로그인 전에는 비활성)·교육용 문구가 보이는지, 창 크기를 줄이면 스크롤이 생기는지 | `./mvnw test` 통과(AppStateTest 5개). 창 표시는 확인하지 못함(화면 없는 환경) |
| 2026-11-22 | T-03 DEMO 케이스 `cases/demo.json`: 김하늘·28세·여·SIM-P01·501-1호·조현정동장애, Penicillin 알레르기, 처방 5건(Olanzapine 15mg, Aripiprazole 10mg, Lithium 300mg, Lorazepam 정규 0.5mg·PRN 0.5mg)은 환자케이스 Figma 예시 값만 사용. 정한 것: 경로·횟수·PRN 적응·입원 정보·위험 지표·난이도는 `[확인필요]`, 활력징후·관찰 로그·정답 키·모범 기록은 비움(Figma에 값이 없거나 불일치), source·reviewStatus는 DEMO | `./mvnw test` 통과(CaseLoaderTest에 DEMO 읽기 1개 추가) |
| 2026-11-22 | T-02 CaseLoader(`app.cases`): 클래스패스 `cases/<id>.json` 읽기, 파일 없음·깨진 JSON·잘못된 형식·필수 항목(id, title, source, patient.name, 처방의 orderId·type·drugName) 누락을 원인 목록과 함께 CaseLoadException으로 거부. 정한 것: 임상 값(용량 등)은 검사하지 않고 [확인필요]도 통과, 알 수 없는 enum 값은 누락과 같이 거부, `exists()`로 PC-01 유무 확인(없으면 DEMO 대체용) | `./mvnw test` 통과(CaseLoaderTest 7개, 합성 케이스 `src/test/resources/cases/TEST-01.json`) |
| 2026-11-22 | T-01 도메인 모델(`app.domain`): PatientCase(환자케이스 '데이터 변환' 항목)·Patient·Adr·Medication(OrderType 정규/PRN/과거)·VitalSign·ObservationEntry·SafetyRound·AnswerKey·SbarElements·ModelAnswer, 실습 Attempt·MedicationRecord(MedicationResult 투여/거부/보류)·SbarReport·SbarRecommendation·CheckResult·AiFeedback, CaseSource(FIXED/AI_GENERATED/DEMO), JSON 변환 DomainJson(Gson). 정한 것: 모델은 record·목록은 null→빈 목록 변경 불가 복사, 케이스 안 시각은 `HH:mm` 문자열(schema의 *_time과 같음), 실습 시작·제출 시각은 LocalDateTime ISO-8601 문자열, 용량은 `[확인필요]`를 둘 수 있게 문자열, 기존 관찰 로그와 학생 관찰은 같은 ObservationEntry를 쓰고 담긴 목록으로 출처 구분 | `./mvnw test` 18개 통과(DomainJsonTest 6개: 케이스·실습 JSON 왕복, 한글 보존, 빈 목록 처리). 합성 값만 사용. MySQL 테스트는 이 PC 설정 기준으로 실행됨 |
| 2026-11-18 | 저장 방식을 MySQL로 변경(D21): 계정·실습 기록·점검 결과는 MySQL, 케이스 원본은 JSON. 기획안·결정기록·tasks(T-19)·CLAUDE.md 반영 | 문서만. 다른 팀 PC의 MySQL 설치 방식은 확인 필요 |
| 2026-11-16 | AI 케이스 생성 설계 추가(D20, F-14, S9, T-30·31): AI가 사건 유형 안에서 환자·사건을 만들고, 채점은 생성된 사실 데이터 기준. 11/30은 PC-01로 완성 | 문서 설계만. 사건 유형·허용 약물 범위는 팀 결정 전 |
| 2026-11-16 | 수업 일정 반영(D19): 11/30 1차 초안, 12/14 최종, 시연 영상·설명 PPT, 다른 팀 사용 | 팀원이 전달한 수업 안내 기준. 11/30 제출 형식은 확인 필요 |
| 2026-11-16 | 문서 구조·문체 정리, 최신 MVP 범위 통일, 이전 케이스 보관, Figma 검토 추가 | 원본 디자인의 정적 화면과 문서 내용 확인. 임상 근거·프로토타입 동작·실행 검증은 포함하지 않음 |
| 2026-11-16 | D13~D18 반영: PC-01, 관찰·투약·SBAR, 활력징후 추이, 개인 메인 결과 | 초기 문서·SVG 와이어프레임 작성 |
| 2026-11-16 | 저장소와 Java 뼈대, 기획·근거·케이스 초안 구성 | 출처는 검색 요약 중심으로 수집; 원문 검증 미완료 |

## 팀 확인 사항

담당자 배정, 11/30 제출 형식, AI 도구 사용 규정, API 예산, 다른 팀의 사용 방식이 남아 있다. 이전 기록에 신청서의 GitHub 토큰 노출이 언급되어 있으며, 폐기 여부는 아직 확인 기록이 없다.
