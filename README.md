# java-pbl-nursing-chart

간호학부 학생 및 1년차 간호사를 위한 **차팅 연습 프로그램** (JAVA PBL).
가상 정신과 환자 시나리오를 읽고 SOAPIE로 차팅하면 규칙 기반 채점과 AI 코멘트로 피드백을 받는 Java Swing 데스크톱 프로그램입니다.

> 상태: 초기 세팅. 시나리오·루브릭은 간호 전문가 검수 전입니다.

## 실행 환경
- Java 17 이상 (개발 PC는 Java 21)
- Swing (+ FlatLaf 예정), Gson, `java.net.http.HttpClient`

## 폴더 구조
```
src/main/java/app/            애플리케이션 코드 (ui, domain, scenario, scoring, ai, storage 패키지 예정)
src/main/resources/scenarios/ 시나리오 JSON (가상 환자만 사용)
config/ai.properties.example  AI API 설정 예시 (실제 키는 config/ai.properties, 커밋 금지)
```

## 규칙
- **API 키, 토큰은 절대 커밋하지 않습니다.** `config/ai.properties`는 `.gitignore`에 포함되어 있습니다.
- 시나리오는 **전부 가상 환자**입니다. 실제 환자 기록을 넣지 않습니다.
- 브랜치: `main`에서 기능별 브랜치(`feature/...`)를 만들고 PR로 병합합니다.

## 빌드·실행 (Maven)
IntelliJ IDEA 또는 Eclipse에서 `pom.xml`을 프로젝트로 열고 `app.Main`을 실행합니다.
(`pom.xml`은 아직 IDE에서 검증하지 않았습니다. 문제가 있으면 이슈로 남겨 주세요.)
