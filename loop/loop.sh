#!/usr/bin/env bash
# 랄프 루프: loop/PROMPT.md를 Claude에 주고 loop/TASKS.md의 작업을 하나씩 처리한다.
# 사용법: ./loop/loop.sh [반복 횟수, 기본 1]
# - loop/ 로 시작하는 브랜치에서만 실행된다 (main/master 거부).
# - Claude는 커밋까지만 한다. push와 PR은 사람이 한다.
# - status가 blocked/done이거나, 테스트가 실패하거나, docs/가 바뀌면 멈춘다.
set -euo pipefail

ROOT="$(git rev-parse --show-toplevel)"
cd "$ROOT"

stop() { echo "[loop] 중단: $*" >&2; exit 1; }

# ---- 반복 횟수 ----
COUNT="${1:-1}"
[[ "$COUNT" =~ ^[1-9][0-9]*$ ]] || stop "반복 횟수는 1 이상의 정수여야 합니다: '$COUNT'"

# ---- 브랜치 확인 ----
BRANCH="$(git symbolic-ref --short -q HEAD || true)"
[[ -n "$BRANCH" ]] || stop "브랜치가 아닌 상태(detached HEAD)에서는 실행하지 않습니다."
case "$BRANCH" in
  main|master) stop "'$BRANCH' 브랜치에서는 실행하지 않습니다. 예: git switch -c loop/작업이름" ;;
  loop/*) ;;
  *) stop "loop/ 로 시작하는 브랜치에서만 실행합니다 (현재: $BRANCH)." ;;
esac

# ---- 시작 전 상태 확인 ----
[[ -f loop/BLOCKED.md ]] && stop "loop/BLOCKED.md가 있습니다. 내용을 해결하고 파일을 지운 뒤 다시 실행하세요."
[[ -z "$(git status --porcelain)" ]] || stop "커밋하지 않은 변경이 있습니다. 정리한 뒤 실행하세요 (git status)."
command -v claude >/dev/null || stop "claude 명령을 찾을 수 없습니다."

# macOS에서 JDK 21을 지정한다 (다른 OS는 PATH의 java 사용)
if [[ -x /usr/libexec/java_home ]] && /usr/libexec/java_home -v 21 >/dev/null 2>&1; then
  export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
fi

LOG_DIR="loop/logs"
mkdir -p "$LOG_DIR"

# Claude가 쓸 수 있는 도구. git push는 넣지 않고, 명시적으로도 막는다.
ALLOWED_TOOLS=(
  "Read" "Write" "Edit" "Glob" "Grep"
  "Bash(./mvnw:*)"
  "Bash(git status:*)" "Bash(git diff:*)" "Bash(git log:*)"
  "Bash(git add:*)" "Bash(git commit:*)"
)
DISALLOWED_TOOLS=("Bash(git push:*)")

for ((i = 1; i <= COUNT; i++)); do
  STAMP="$(date +%Y%m%d-%H%M%S)"
  LOG="$LOG_DIR/$STAMP-$i.log"
  START_HEAD="$(git rev-parse HEAD)"
  echo "[loop] $i/$COUNT 시작 (브랜치 $BRANCH, 로그 $LOG)"

  set +e
  claude -p "$(cat loop/PROMPT.md)" \
    --no-session-persistence \
    --max-turns 40 \
    --allowedTools "${ALLOWED_TOOLS[@]}" \
    --disallowedTools "${DISALLOWED_TOOLS[@]}" \
    >"$LOG" 2>&1
  RC=$?
  set -e

  # 마지막 비어 있지 않은 줄에서 status를 읽는다
  LAST_LINE="$(awk 'NF { line = $0 } END { print line }' "$LOG" | tr -d '\r')"
  STATUS=""
  if [[ "$LAST_LINE" =~ ^status:\ (continue|done|blocked)[[:space:]]*$ ]]; then
    STATUS="${BASH_REMATCH[1]}"
  fi
  echo "[loop] $i/$COUNT claude 종료 코드 $RC, status: ${STATUS:-(없음)}"

  [[ "$RC" -eq 0 ]] || stop "claude가 오류로 끝났습니다. 로그: $LOG"

  # docs/ 변경 확인 (progress.md는 루트에 있어 해당 없음)
  if [[ -n "$(git diff --name-only "$START_HEAD" -- docs/)" ]] \
     || [[ -n "$(git status --porcelain -- docs/)" ]]; then
    stop "docs/ 아래 파일이 바뀌었습니다. 규칙 위반이므로 확인하세요 (git diff $START_HEAD -- docs/)."
  fi

  # 테스트를 직접 다시 돌린다
  echo "[loop] $i/$COUNT 테스트 확인 (./mvnw test)"
  if ! ./mvnw -B -q test >"$LOG.test" 2>&1; then
    stop "테스트가 실패했습니다. 로그: $LOG.test"
  fi

  case "$STATUS" in
    continue) ;;
    done) echo "[loop] 모든 작업 완료(status: done). 결과를 확인하고 push·PR 하세요."; exit 0 ;;
    blocked) stop "작업이 막혔습니다(status: blocked). loop/BLOCKED.md를 확인하세요." ;;
    *) stop "마지막 줄에서 status를 찾지 못했습니다. 로그: $LOG" ;;
  esac

  [[ -n "$(git status --porcelain)" ]] && stop "status는 continue인데 커밋하지 않은 변경이 남았습니다 (git status)."
done

echo "[loop] $COUNT회 실행을 마쳤습니다. git log와 loop/TASKS.md를 확인한 뒤 push·PR 하세요."
