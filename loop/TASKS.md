# 루프 작업 목록

위에서부터 순서대로 한다. 루프는 체크되지 않은 첫 작업 1개만 한다.
완료 기준은 `docs/tasks.md`에서 옮겨 적었다. 내용이 다르면 `docs/tasks.md`가 기준이다.
작업을 추가하거나 순서를 바꾸는 것은 사람이 한다.

- [x] T-19 DB 기반: `schema.sql`, `config/db.properties` 읽기, 연결 관리 (2026-11-22, PR #3)
  - 완료 기준: 빈 DB에 스키마 생성, 접속 실패 시 원인 안내(비밀번호 미출력)
  - 남은 것: CI의 MySQL 컨테이너 통합 테스트 (루프 대상 아님)

- [ ] T-01 PatientCase·Patient·Medication·Attempt 모델
  - 완료 기준: JSON 왕복 시 필수 데이터 보존
  - 참고: `docs/기획안.md` '데이터와 저장'의 항목, `docs/환자케이스.md` '데이터 변환'. 테스트는 합성 값으로

- [ ] T-02 CaseLoader
  - 완료 기준: 누락·잘못된 형식을 원인과 함께 거부
  - 참고: 실제 `PC-01.json`은 아직 없다(T-03). 테스트용 합성 케이스 JSON을 `src/test/resources/`에 둔다

- [ ] T-14 UserRepository (MySQL)·비밀번호 해시
  - 완료 기준: 평문 미저장, 중복 가입 거부, 비밀번호 검증
  - 참고: PBKDF2 + 사용자별 솔트(기획안). 인터페이스 뒤에 MySQL 구현과 메모리 구현. 가입 항목·비밀번호 정책(D-14)이 미정이면 멈춘다

- [ ] T-15 AuthService·사용자별 데이터
  - 완료 기준: 로그인·로그아웃·기록 분리
  - 참고: 오류 문구는 아이디·비밀번호 중 어느 쪽이 틀렸는지 구분하지 않는다(시나리오 S1)

- [ ] T-12 AttemptRepository (MySQL)
  - 완료 기준: 사용자별 저장·조회, 제출 당시 결과 보존
  - 참고: `schema.sql`의 attempts·observation_records·medication_records·sbar_*·check_results 테이블. 다른 사용자의 기록은 조회되지 않음(S8)

- [ ] T-04 R8 필수 입력 (T-04 중 R8만. R7 값 일치는 PC-01 확정 후)
  - 완료 기준: 필수값 누락 검증
  - 참고: 관찰(시각·근무·태그 1개 이상·서술), 투약(처방·용량·단위·경로·시각·결과·처방 근거, PRN·거부·보류는 사유), SBAR(S·B·A, R의 시각·중재). `docs/화면명세.md` '입력과 검증'
