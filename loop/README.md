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

**1. main을 최신으로 받고 루프용 브랜치를 만듭니다.** 브랜치 이름은 꼭 `loop/`로 시작해야 합니다.

```bash
git switch main && git pull
```

```bash
git switch -c loop/t01-models
```

**2. 루프를 실행합니다.** 숫자는 반복 횟수이고, 처음에는 1회를 권합니다.

```bash
./loop/loop.sh 1
```

**3. 결과를 확인합니다.**

```bash
git log --oneline -5
```

```bash
git show --stat
```

- `loop/TASKS.md`에 체크가 생겼는지, `progress.md`에 한 줄이 추가됐는지 봅니다.
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
| 커밋하지 않은 변경이 있습니다 | 작업 폴더가 깨끗하지 않음 | `git status`로 확인하고 정리한다 |
| status: blocked | 임상 값·정책이 필요하거나 문서와 다르게 해야 함 | `loop/BLOCKED.md`를 읽고 팀이 정한 뒤 문서를 고치고, `BLOCKED.md`를 지운다 |
| status: done | 할 일을 다 함 | push·PR 한다 |
| 테스트가 실패했습니다 | Claude가 끝낸 뒤 `./mvnw test`가 실패 | `loop/logs/…log.test`를 본다 |
| docs/ 아래 파일이 바뀌었습니다 | 규칙 위반 | `git diff`로 보고 되돌린다 |

## 지켜지는 규칙

- `main`/`master` 브랜치와 `loop/`가 아닌 브랜치에서는 실행되지 않습니다.
- Claude가 쓸 수 있는 도구는 파일 읽기·쓰기, `./mvnw`, `git status/diff/log/add/commit`뿐입니다. `git push`는 막혀 있습니다.
- 한 번 실행할 때 최대 40턴까지만 진행합니다.
- 키 파일(`config/ai.properties`, `config/db.properties`)은 열지 않습니다. DB 테스트는 `./mvnw test`가 읽습니다.
- 실행 비용이 듭니다. 횟수는 작게 시작하세요.
