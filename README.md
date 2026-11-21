# 간호 차팅 연습 프로그램

간호학생과 신규 간호사가 가상 정신과 환자의 정보를 읽고, 관찰 기록·투약 기록·SBAR 인계를 작성하는 Java 데스크톱 프로그램입니다. 작성 결과는 규칙 기반으로 점검하고, AI 해설로 보충합니다.

현재는 기획과 화면 설계를 정리하는 단계입니다. 코드는 기본 Swing 창까지 작성되어 있으며, 기능 구현과 Maven 빌드 검증은 아직 진행하지 않았습니다. 1차 초안 완성본은 2026년 11월 30일, 최종 제출은 12월 14일입니다. 제출물은 프로그램과 시연 영상·설명 PPT이며, 다른 팀이 프로그램을 직접 사용해 봅니다.

## 첫 버전의 범위

- 가상 환자 PC-01 한 명을 대상으로 실습
- 로컬 회원가입·로그인과 사용자별 실습 기록
- 환자 정보, 활력징후 추이, 기존 관찰 기록과 처방 조회
- 관찰 기록·투약 기록·SBAR 작성 및 제출
- 항목별 점검 결과, 과거 기록 조회, 선택 기능인 AI 해설

AI 연결이 없어도 기본 실습과 결과 조회가 가능하도록 설계합니다. SOAPIE 입력, 별도 MSE·위험사정 입력, 추가 환자 케이스는 후속 범위입니다.

## 문서

| 문서 | 내용 |
|---|---|
| [팀 가이드](docs/00-팀가이드.md) | 처음 참여할 때 읽을 안내 |
| [기획안](docs/기획안.md) | 기능 범위, 데이터, 점검 기준 |
| [사용자 시나리오](docs/시나리오.md) | 실습 흐름과 확인할 결과 |
| [화면명세](docs/화면명세.md) | 화면별 조회·입력·이동 |
| [환자 케이스](docs/환자케이스.md) | PC-01 초안과 데이터 확인 항목 |
| [디자인](docs/design/README.md) | Figma 링크와 화면 검토 |
| [자료조사](docs/자료조사.md) · [근거표](docs/근거표.md) | 출처 후보와 검증 상태 |
| [작업 목록](docs/tasks.md) · [팀 분담](docs/팀분담.md) | 남은 작업과 역할 |
| [결정기록](docs/결정기록.md) · [진행 현황](progress.md) | 현재 결정과 변경 이력 |

## 개발 환경

이클립스에서 열 때는 **File → Import → Maven → Existing Maven Projects**로 저장소 폴더를 선택합니다. JDBC 드라이버는 Maven이 자동으로 받으므로 따로 추가하지 않습니다.

Java 21, Maven(Wrapper 포함), Swing, FlatLaf, Gson, JUnit 5, **MySQL 8 이상(JDBC)** 을 사용합니다. 계정과 실습 기록은 MySQL에 저장합니다.

```bash
./mvnw test                 # 테스트
./mvnw compile exec:java    # 실행
```

```text
src/main/java/app/            애플리케이션 코드
src/main/resources/cases/    환자 케이스 JSON 예정 위치
config/ai.properties.example  AI 설정 예시
config/db.properties.example  DB 접속 설정 예시
src/main/resources/db/       MySQL 스키마 (schema.sql)
docs/                        기획·설계·검증 문서
```

실행용 JAR 구성은 아직 없습니다. 개발 규칙과 명령은 [CLAUDE.md](CLAUDE.md)에 있습니다.

환자 데이터는 모두 가상이며 임상 검수 전의 교육용 예시입니다. 실제 환자 기록이나 API 키·비밀번호는 저장소에 올리지 않습니다. AI 설정 파일 `config/ai.properties`는 Git 추적 대상에서 제외되어 있습니다.
