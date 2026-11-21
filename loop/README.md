# 랄프 루프 사용법

Claude가 `loop/TASKS.md`의 작업을 **위에서부터 하나씩** 구현하고 커밋해 주는 반복 실행 도구입니다.
Claude는 **커밋까지만** 합니다. push와 PR, 병합은 사람이 합니다.

## 파일

| 파일 | 역할 |
|---|---|
| `PROMPT.md` | Claude가 매번 읽는 지시 (작업 1개만, 테스트 통과, docs 수정 금지 등) |
| `TASKS.md` | 할 일 목록. 체크(`[x]`)된 것은 건너뜀. 순서와 추가는 사람이 정함 |
| `loop.sh` | 실행 스크립트 |
| `logs/` | 실행 기록 (Git에 올라가지 않음) |
| `BLOCKED.md` | Claude가 막혔을 때 이유를 적는 파일 (막혔을 때만 생김) |

## 쓰는 순서

**0. 처음 한 번: Claude CLI 로그인.** 터미널에서 `claude`를 실행하고 `/login`으로 로그인한 뒤 `/exit`로 나옵니다.

**1. main을 최신으로 받고 루프용 브랜치를 만듭니다.** 브랜치 이름은 꼭 `loop/`로 시작해야 합니다.

```bash
git switch main && git pull
```

```bash
git switch -c loop/t01-models
```

**2. 루프를 실행합니다.** 숫자는 반복 횟수입니다. 처음에는 1회로 동작을 확인하고, 그다음 하루 동안 돌릴 때는 크게 줍니다.

```bash
./loop/loop.sh 1
```

```bash
./loop/loop.sh 60
```

- 한 회차는 작업 1개(또는 그 일부)를 합니다. 테스트가 실패하면 원인을 `progress.md`에 적고 `wip:` 커밋을 남겨 다음 회차가 이어서 고칩니다.
- 맥이 잠자기에 들어가지 않게 해 두세요.

**3. 결과를 확인합니다.**

```bash
git log --oneline -5
```

```bash
git show --stat
```

- `loop/TASKS.md`에 체크가 생겼는지, `progress.md`에 한 줄이 추가됐는지 봅니다.
- 화면 작업은 `progress.md`의 **직접 확인할 것**을 `./mvnw compile exec:java`로 실행해 눈으로 확인합니다.
- `wip:` 커밋이 마지막이면 아직 테스트가 실패하는 상태입니다.
- 실행 기록은 `loop/logs/`에 있습니다.
- 마음에 들지 않으면 push하지 말고 커밋을 되돌리거나 고칩니다.

**4. push하고 PR을 만듭니다.**

```bash
git push -u origin HEAD
```

```bash
gh pr create --base main
```

**5. GitHub에서 리뷰하고 병합합니다.**

## 멈추는 경우

| 화면에 나온 말 | 뜻 | 할 일 |
|---|---|---|
| `main` 브랜치에서는 실행하지 않습니다 | 브랜치가 틀림 | 1번처럼 `loop/` 브랜치를 만든다 |
| 커밋하지 않은 변경이 있습니다 | 시작할 때 작업 폴더가 깨끗하지 않음 | `git status`로 확인하고 정리한다 (회차 사이에 남은 변경은 다음 회차가 이어서 함) |
| status: blocked | 임상 값·키·비밀번호가 필요하거나 문서 범위를 벗어나야 함 | `loop/BLOCKED.md`를 읽고 팀이 정한 뒤 문서를 고치고, `BLOCKED.md`를 지운다 |
| status: done | 할 일을 다 함 | push·PR 한다 |
| 연속 3회 실패했습니다 | 같은 작업에서 테스트 실패·claude 오류·status 없음이 3번 연속 | `loop/logs/`와 `progress.md`의 원인을 보고 고친 뒤 다시 실행 |
| status는 done인데 테스트가 실패했습니다 | 마지막 작업 뒤 테스트 실패 | `loop/logs/…log.test`를 본다 |
| 남은 작업이 없습니다 | TASKS.md가 모두 체크됨 | push·PR 한다 |
| docs/ 아래 파일이 바뀌었습니다 | 규칙 위반 | `git diff`로 보고 되돌린다 |

## 지켜지는 규칙

- `main`/`master` 브랜치와 `loop/`가 아닌 브랜치에서는 실행되지 않습니다.
- Claude가 쓸 수 있는 도구는 파일 읽기·쓰기, `./mvnw`, `git status/diff/log/add/commit`뿐입니다. `git push`는 막혀 있습니다.
- 한 번 실행할 때 최대 40턴까지만 진행합니다.
- 키 파일(`config/ai.properties`, `config/db.properties`)은 열지 않습니다. DB 테스트는 `./mvnw test`가 읽습니다.
- `docs/`는 고치지 않습니다(`progress.md`만 예외). 고치면 루프가 멈춥니다.
- `cases/allowed-drugs.json`의 약물 값은 채우지 않습니다. 팀이 근거표를 보고 직접 채웁니다(D26). 비어 있으면 AI 케이스 생성만 꺼집니다.
- 실행 비용이 듭니다. 횟수는 작게 시작하세요.
