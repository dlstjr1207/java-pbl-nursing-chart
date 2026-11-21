# CLAUDE.md

간호학생이 가상 정신과 환자의 EMR 화면을 보고 관찰 기록·투약 기록·SBAR 인계를 작성하면, 규칙 기반 점검으로 피드백하고 AI가 해설을 보충하는 Java 데스크톱 프로그램이다 (JAVA PBL, 4인 팀). 1차 초안 완성본은 2026-11-30, 최종 제출은 2026-12-14다.

## 꼭 지킬 규칙

1. API 키·토큰·비밀번호를 채팅으로 묻지 않고, 화면에 출력하지 않고, 절대 커밋하지 않는다. 키는 사람이 직접 `config/ai.properties`에 넣는다.
2. 비밀번호 입력(sudo, Homebrew 설치)이나 브라우저 로그인(`gh auth login`) 같은 대화형 명령은 직접 실행하지 않는다. 사용자가 맥 "터미널" 앱에 붙여 넣을 정확한 명령을 코드 블록으로 알려 주고, 끝났다고 할 때까지 기다린다.
3. 설치 전에 이미 설치되어 있는지 확인하고, 없을 때만 설치한다.
4. `main` 브랜치에 직접 커밋하거나 푸시하지 않는다. 작업마다 브랜치를 만들고 PR을 올린다. 병합은 사용자가 한다.
5. 문서와 다르게 만들어야 하거나 범위를 바꿔야 하면 먼저 묻는다. 약 용량 같은 임상 값은 지어내지 않는다. 모르면 문서의 `[확인필요]`/`[검증필요]`를 그대로 둔다.
6. 테스트가 통과해야 완료로 본다. 확인하지 못한 것은 확인하지 못했다고 말한다.
7. 반복 실행(랄프 루프)은 사용자가 지시하기 전까지 만들거나 돌리지 않는다.

## 핵심 설계 원칙

- 채점은 케이스의 **사실 데이터**와 학생 기록을 규칙(R1·R2·R4~R9)으로 비교한다. AI는 해설(과 12월의 케이스 생성)만 하고 점수에 반영하지 않는다.
- AI 키가 없어도 PC-01과 규칙 점검으로 전부 동작해야 한다. 테스트는 실제 AI를 호출하지 않고 가짜 응답을 쓴다.
- 저장은 MySQL 8(JDBC)이다(D21). 계정·실습 기록·점검 결과는 DB, 환자 케이스 원본은 `src/main/resources/cases/`의 JSON. 저장소는 인터페이스(`UserRepository`, `AttemptRepository`) 뒤에 두고, 단위 테스트는 메모리 구현, 저장 통합 테스트는 실제 MySQL을 쓴다. DB 접속 정보는 `config/db.properties`.
- 11/30은 고정 케이스 PC-01로 실습 한 바퀴를 완성한다. AI 가상 환자·사건 생성(D20, F-14)은 12월에 추가한다.
- 약물 공공데이터 API의 사용 방식은 아직 문서로 정해지지 않았다. 구현 전에 사용자에게 확인한다.

## 구현 규칙 (수업 요구사항, D22)

- GUI는 Swing이다. 메인 창은 `JFrame` 하나로 두고 화면은 `JPanel`을 바꿔 전환한다. 강의에서 배운 배치관리자(`BorderLayout`, `GridLayout`, `FlowLayout`)와 컴포넌트(`JTextField`, `JTextArea`, `JButton`, `JCheckBox`, `JRadioButton`, `JComboBox`, `JList`, `JScrollPane`, `JMenuBar`), 이벤트 리스너(`ActionListener`, `ItemListener`, `KeyListener`, `MouseListener`)를 우선 쓴다. 대응표는 `docs/기획안.md` '수업 요구사항 대응'.
- DB 접근은 JDBC(`DriverManager`, `Connection`, `PreparedStatement`, `ResultSet`)로 직접 SQL을 쓴다. ORM을 쓰지 않고, 쿼리 문자열을 이어 붙이지 않는다. 문자셋은 `utf8mb4`.
- 파일 입출력은 `java.io`/`java.nio`로 케이스·설정을 읽고, 실습 결과 내보내기(T-26)를 구현한다.
- 팀원이 발표에서 설명할 수 있도록 클래스와 주요 메소드에 한국어 주석으로 역할을 짧게 적는다.

## 기준 문서

| 문서 | 역할 |
|---|---|
| `docs/기획안.md` | 기능(F-xx)·점검 기준(R-xx)·데이터 구조의 기준 문서 |
| `docs/화면명세.md` | 화면별 조회·입력·검증·이동 |
| `docs/시나리오.md` | 사용자 흐름과 기대 결과(S1~S9, 변형 V1~V7). 통합 테스트 기준 |
| `docs/환자케이스.md` | PC-01 데이터와 확인 항목(C-01~C-10), AI 생성 케이스 기준 |
| `docs/tasks.md` | 작업 ID(D-xx, T-xx, V-xx)와 완료 기준 |
| `docs/결정기록.md` | 범위 결정(D1~D20). 최신 결정이 우선 |
| `docs/근거표.md` · `docs/자료조사.md` | 임상 출처 후보와 검증 상태 |
| `progress.md` | 진행 현황과 변경 기록 |

## 빌드·테스트·실행

JDK 21이 필요하다. Maven을 따로 설치하지 않아도 Wrapper(`./mvnw`)가 Maven 3.9.16을 내려받는다.

```bash
./mvnw test                 # 테스트 (CI와 동일, 키 불필요)
./mvnw compile exec:java    # 프로그램 실행 (app.Main)
./mvnw package              # target/ 에 JAR 생성 (실행용 JAR 구성은 T-00 남은 작업)
```

macOS에서 JDK가 여러 개면 `export JAVA_HOME=$(/usr/libexec/java_home -v 21)`로 21을 지정한다. CI는 `.github/workflows/ci.yml`에서 키 없이 `./mvnw -B test`를 실행한다.

## 키 파일 규칙

- `config/ai.properties.example`을 `config/ai.properties`로 복사해 사람이 직접 값을 넣는다.
- `config/` 안에서는 `*.example`만 Git에 올라간다(`.gitignore`). 편집기 백업 파일도 함께 제외된다.
- 코드와 로그에서 키 값을 출력하지 않는다. 키 파일을 읽어 내용을 보여 주지 않는다. 항목 이름은 example 기준으로만 안내한다.
- 키가 없거나 비어 있으면 AI 기능을 끄고 규칙 점검만으로 동작한다.
- `config/db.properties`(DB 접속 정보)도 같은 규칙을 따른다. DB 비밀번호를 출력하거나 커밋하지 않는다. DB 계정 생성처럼 root 비밀번호가 필요한 작업은 사용자가 터미널에서 직접 실행하게 안내한다.
