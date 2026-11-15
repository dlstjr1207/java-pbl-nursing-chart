# java-pbl-nursing-chart

간호학부 학생과 1년차 간호사를 위한 **차팅 연습 프로그램** (JAVA PBL, Java Swing).
가상 정신과 환자의 정보와 면담 대화록을 읽고 SOAPIE로 차팅하면, 규칙 기반 점검과 AI 해설로 피드백을 받습니다.

> **상태: 문서·디자인 단계.** 코드는 아직 빈 창뿐이고, 구현은 문서와 디자인이 확정된 뒤 시작합니다. 마감 2026-12-14.
> 임상 내용은 **전문가 검수 없이** 공개 자료로 작성했고 교육용 예시입니다.

## 팀원은 여기부터

👉 **[docs/00-팀가이드.md](docs/00-팀가이드.md)** — 지금 상태, 해야 할 일, 역할, GitHub 사용법이 한 문서에 있습니다.

## 문서 지도

| 파일 | 내용 |
|---|---|
| [docs/00-팀가이드.md](docs/00-팀가이드.md) | 시작 안내와 해야 할 일 |
| [docs/기획안.md](docs/기획안.md) | 범위, 기능, 점검 기준, 일정 |
| [docs/시나리오.md](docs/시나리오.md) | 가상 환자 시나리오 3개 |
| [docs/근거표.md](docs/근거표.md) | 임상 내용의 출처 |
| [docs/화면명세.md](docs/화면명세.md) | 화면별 명세 |
| [docs/design/](docs/design/README.md) | Figma 화면 PNG |
| [docs/tasks.md](docs/tasks.md) | 작업 목록 |
| [docs/결정기록.md](docs/결정기록.md) | 결정과 이유 |
| [progress.md](progress.md) | 진행 기록 |

## 개발 환경 (구현 단계에서 사용)

- Java 17 이상, Maven, Swing(+FlatLaf), Gson, JUnit 5
- 폴더: `src/main/java/app/`(코드), `src/main/resources/scenarios/`(시나리오 JSON), `config/ai.properties.example`(AI 설정 예시)
- `pom.xml`은 아직 검증하지 않았습니다.

## 규칙

- **API 키·토큰·비밀번호는 절대 커밋하지 않습니다.** `config/ai.properties`는 `.gitignore`에 포함되어 있습니다.
- 시나리오는 **전부 가상 환자**입니다. 실제 환자 기록을 넣지 않습니다.
- 임상 주장은 [근거표](docs/근거표.md)의 출처를 달아 씁니다. 근거가 없으면 `[검증필요]`로 둡니다.
