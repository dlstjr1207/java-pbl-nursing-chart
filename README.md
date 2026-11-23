# 간호 차팅 연습 프로그램

간호학생과 신규 간호사가 가상 정신과 환자의 EMR 화면을 보고 관찰 기록·투약 기록·SBAR 인계를 작성하는 Java 데스크톱 프로그램입니다. 작성 결과는 규칙 기반으로 점검해 점수와 항목별 피드백을 보여 주고, AI 해설로 보충합니다.

1차 초안 완성본은 2026년 11월 30일, 최종 제출은 12월 14일입니다. 환자 데이터는 모두 가상이며 임상 검수 전의 교육용 예시입니다.

## 다른 팀이 실행해 보기

준비물은 **Java 21** 하나입니다. DB와 AI 키가 없어도 실습 한 바퀴가 전부 동작합니다.

1. **Java 21 확인.** 터미널(Windows는 명령 프롬프트)에서 아래를 실행해 `21`이 보이는지 확인합니다. 없으면 [Temurin 21](https://adoptium.net/temurin/releases/?version=21)을 설치합니다.
   ```bash
   java -version
   ```
2. **JAR 받기와 실행.** [Releases의 v0.1.0-1130](https://github.com/dlstjr1207/java-pbl-nursing-chart/releases/tag/v0.1.0-1130)에서 `nursing-chart-trainer.jar`를 내려받아, 그 파일이 있는 폴더에서 실행합니다(파일을 두 번 눌러 열 수도 있지만, `config/` 설정을 쓰려면 이 명령으로 실행합니다).
   ```bash
   java -jar nursing-chart-trainer.jar
   ```
   소스에서 직접 만들려면 저장소 폴더에서 `./mvnw package`(Windows는 `mvnw.cmd package`)를 실행하면 `target/nursing-chart-trainer.jar`가 생깁니다.
3. **DB가 없으면 파일 저장 모드.** MySQL 설정이 없으면 로그인 화면에 "DB 없이 파일 저장 모드입니다"가 보이고, 계정과 실습 기록은 이 PC의 사용자 폴더 `~/.nursing-chart-trainer/`에 저장됩니다. 그대로 회원가입 → 로그인하면 됩니다.
4. **AI 키가 없으면 PC-01로 실습.** 개인 메인에서 "실습 시작"을 누르면 기본 케이스 PC-01(김하늘, 28세, 조현정동장애)로 실습합니다. AI 해설은 "없음"으로 표시되고, 점수와 점검 결과는 그대로 나옵니다. AI 케이스 생성 버튼은 꺼지고 이유가 표시됩니다.

실습 흐름: 회원가입 → 로그인 → 개인 메인 → 실습 시작 → 환자 정보(상황 브리핑) → 관찰 기록 → 투약 점검(처방을 보고 용량·경로·시각을 직접 입력) → SBAR 인계 제출 → 결과 화면에서 점수·항목별 점수·고칠 점·모범 기록 확인. 개인 메인의 과거 기록을 누르면 같은 결과 화면이 열립니다. 근거 번호와 검토 상태는 메뉴 **파일 → 근거·출처 보기**에서 봅니다. 기록은 계정마다 따로 보이고, "선택한 기록 내보내기"로 텍스트 파일(UTF-8)로 저장할 수 있습니다.

## 배포용 JAR 올리기 (GitHub Release)

개발자가 main에서 JAR을 만들어 Release에 올립니다. 팀원은 Releases 페이지에서 내려받기만 하면 됩니다.

```bash
git checkout main && git pull
./mvnw clean package
gh release create v0.1.0-1130 target/nursing-chart-trainer.jar --target main --title "v0.1.0-1130 (1차 초안)" --notes "Java 21에서 java -jar nursing-chart-trainer.jar 로 실행. DB 없으면 파일 저장 모드, AI 키 없으면 PC-01로 실습."
```

이미 만든 릴리스의 JAR만 바꿀 때는 `gh release upload v0.1.0-1130 target/nursing-chart-trainer.jar --clobber`를 씁니다.

## 선택 설정 (`config/` 폴더)

JAR을 실행하는 폴더 아래 `config/`에 설정 파일을 두면 읽습니다. 키·비밀번호가 든 파일은 Git에 올리지 않습니다(`*.example`만 추적).

| 파일 | 넣으면 | 예시 |
|---|---|---|
| `config/db.properties` | MySQL 8 이상에 계정·기록 저장. 없거나 연결 실패면 파일 저장 모드 | [`db.properties.example`](config/db.properties.example) |
| `config/ai.properties` | AI 해설(SBAR 제출 후)과 AI 가상 환자 생성. OpenAI 호환 chat completions 형식 | [`ai.properties.example`](config/ai.properties.example) |

AI는 점수에 관여하지 않습니다. 생성된 케이스는 허용 약물·용량 범위(`cases/allowed-drugs.json`)와 정답 키 검사를 통과한 것만 쓰고, 실패하면 PC-01로 돌아갑니다.

## 약 정보 (식약처 허가정보)

투약 점검 화면에서 처방을 고르면 "약 정보(식약처 허가정보)"에 제품명·효능효과·용법용량이 보입니다. 프로그램은 저장된 `src/main/resources/drugs/drug-info.json`만 읽고 실행 중에 API를 부르지 않습니다. 참고용이며 채점 기준값은 근거표에서 확인한 값입니다(D31).

파일 갱신은 개발자가 따로 실행합니다. 공공데이터포털에서 「식품의약품안전처_의약품 제품 허가정보」(15095677) 활용신청 후 `config/ai.properties`의 `drug.apiKey`에 키를 넣고:

```bash
./mvnw -q compile exec:java -Dmain.class=app.drugs.DrugInfoUpdater
```

## 개발

Java 21, Maven Wrapper(`./mvnw`, Maven 3.9.16), Swing + FlatLaf, Gson, JUnit 5, MySQL 8 이상(JDBC). 이클립스는 **File → Import → Maven → Existing Maven Projects**로 엽니다.

```bash
./mvnw test                 # 테스트 (키·DB 없이 통과, CI와 동일)
./mvnw compile exec:java    # 소스에서 바로 실행
./mvnw package              # 실행 JAR 만들기 → target/nursing-chart-trainer.jar
```

```text
src/main/java/app/            애플리케이션 코드 (ui, domain, cases, scoring, storage, ai, drugs, export)
src/main/resources/cases/     환자 케이스 PC-01·DEMO, 허용 약물
src/main/resources/drugs/     식약처 허가정보 약 정보
src/main/resources/db/        MySQL 스키마 (schema.sql)
config/*.example              AI·DB 설정 예시
docs/                         기획·설계·검증 문서
```

개발 규칙과 명령은 [CLAUDE.md](CLAUDE.md)에 있습니다.

## 문서

| 문서 | 내용 |
|---|---|
| [팀 가이드](docs/00-팀가이드.md) | 처음 참여할 때 읽을 안내 |
| [기획안](docs/기획안.md) | 기능 범위, 데이터, 점검 기준과 배점 |
| [사용자 시나리오](docs/시나리오.md) | 실습 흐름과 확인할 결과 (S1~S9, V1~V7) |
| [화면명세](docs/화면명세.md) | 화면별 조회·입력·이동 |
| [환자 케이스](docs/환자케이스.md) | PC-01 데이터, 정답 키, 모범 기록 |
| [디자인](docs/design/README.md) | Figma 화면과 구현 화면 비교 |
| [자료조사](docs/자료조사.md) · [근거표](docs/근거표.md) | 출처와 검증 상태 |
| [작업 목록](docs/tasks.md) · [팀 분담](docs/팀분담.md) | 남은 작업과 역할 |
| [결정기록](docs/결정기록.md) · [진행 현황](progress.md) | 현재 결정과 변경 이력 |
