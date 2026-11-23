#!/usr/bin/env bash
# 랄프 루프: loop/PROMPT.md를 Claude에 주고 loop/TASKS.md의 작업을 하나씩 처리한다.
# 사용법: ./loop/loop.sh [반복 횟수, 기본 1]
# - loop/ 로 시작하는 브랜치에서만 실행된다 (main/master 거부).
# - Claude는 커밋까지만 한다. push와 PR은 사람이 한다.
# - 멈추는 경우: status blocked, done(남은 작업이 정말 없을 때만 정상 종료), 같은 작업에서 연속 3회 실패,
#   docs/ 변경, 남은 작업 없음.
#   실패 = 회차 뒤 ./mvnw test 실패, claude 오류 종료, 마지막 줄 status 없음, 진척 없음(커밋·변경 없이 continue).
# - 테스트가 실패했는데 그 회차에 작업이 체크됐으면 체크를 되돌려 같은 작업을 다시 하게 한다.
set -euo pipefail

ROOT="$(git rev-parse --show-toplevel)"
cd "$ROOT"

MAX_FAILS=3          # 같은 작업에서 연속 실패 허용 횟수
FAIL_PAUSE_SEC=30    # claude 오류 뒤 잠시 쉬는 시간 (일시적 오류 대비)

stop() { echo "[loop] 중단: $*" >&2; exit 1; }

# 체크되지 않은 첫 작업 ID (예: T-01). 없으면 빈 문자열
next_task() { awk '/^- \[ \] / { print $4; exit }' loop/TASKS.md; }

# 체크되지 않은 작업 수
unchecked_count() { grep -c '^- \[ \] ' loop/TASKS.md || true; }

# 작업이 체크되어 있는지 (예: is_checked T-01)
is_checked() { grep -q "^- \[x\] $1 " loop/TASKS.md; }

# 테스트가 실패한 작업의 체크와 날짜를 되돌리고 커밋한다
uncheck_task() {
  local task="$1"
  perl -0pi -e "s/^- \\[x\\] \Q$task\E (.*?)(?: \\(\\d{4}-\\d{2}-\\d{2}\\))?\$/- [ ] $task \$1/m" loop/TASKS.md
  git add loop/TASKS.md
  git commit -q -m "loop: $task 체크 되돌림 (테스트 재확인 실패)" || true
  echo "[loop] $task 체크를 되돌렸습니다 (테스트 실패)."
}

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

# ---- 시작 전 상태 확인 (사람이 시작할 때만) ----
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

FAIL_TASK=""
FAIL_COUNT=0

# 실패 1회를 기록하고, 같은 작업에서 MAX_FAILS회 연속이면 멈춘다
record_failure() {
  local task="$1" reason="$2"
  if [[ "$task" == "$FAIL_TASK" ]]; then
    FAIL_COUNT=$((FAIL_COUNT + 1))
  else
    FAIL_TASK="$task"
    FAIL_COUNT=1
  fi
  echo "[loop] 실패 ($task, 연속 $FAIL_COUNT/$MAX_FAILS): $reason"
  if (( FAIL_COUNT >= MAX_FAILS )); then
    stop "$task 작업에서 연속 $MAX_FAILS회 실패했습니다. 마지막 원인: $reason"
  fi
}

for ((i = 1; i <= COUNT; i++)); do
  TASK="$(next_task)"
  [[ -n "$TASK" ]] || { echo "[loop] 남은 작업이 없습니다. 결과를 확인하고 push·PR 하세요."; exit 0; }

  STAMP="$(date +%Y%m%d-%H%M%S)"
  LOG="$LOG_DIR/$STAMP-$i-$TASK.log"
  START_HEAD="$(git rev-parse HEAD)"
  WAS_CHECKED=0; is_checked "$TASK" && WAS_CHECKED=1
  echo "[loop] $i/$COUNT 시작: $TASK (브랜치 $BRANCH, 로그 $LOG)"

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

  # 규칙 위반: docs/ 변경 (progress.md는 루트에 있어 해당 없음)
  if [[ -n "$(git diff --name-only "$START_HEAD" -- docs/)" ]] \
     || [[ -n "$(git status --porcelain -- docs/)" ]]; then
    stop "docs/ 아래 파일이 바뀌었습니다. 규칙 위반이므로 확인하세요 (git diff $START_HEAD -- docs/)."
  fi

  # 막힘은 바로 멈춘다
  if [[ "$STATUS" == "blocked" || -f loop/BLOCKED.md ]]; then
    stop "작업이 막혔습니다($TASK). loop/BLOCKED.md를 확인하세요."
  fi

  if [[ "$RC" -ne 0 ]]; then
    record_failure "$TASK" "claude 오류 종료(코드 $RC). 로그: $LOG"
    sleep "$FAIL_PAUSE_SEC"
    continue
  fi

  # 테스트를 직접 다시 돌린다
  echo "[loop] $i/$COUNT 테스트 확인 (./mvnw test)"
  if ! ./mvnw -B -q test >"$LOG.test" 2>&1; then
    # 이번 회차에 체크됐다면 되돌려서, 다음 회차가 실패한 작업을 건너뛰지 않게 한다
    if [[ "$WAS_CHECKED" == 0 ]] && is_checked "$TASK"; then uncheck_task "$TASK"; fi
    record_failure "$TASK" "테스트 실패. 로그: $LOG.test"
    continue
  fi

  if [[ -z "$STATUS" ]]; then
    record_failure "$TASK" "마지막 줄에서 status를 찾지 못함. 로그: $LOG"
    continue
  fi

  # 진척 없음: 새 커밋도, 커밋되지 않은 변경도 없이 continue만 나온 경우
  if [[ "$STATUS" == "continue" && "$(git rev-parse HEAD)" == "$START_HEAD" && -z "$(git status --porcelain)" ]]; then
    record_failure "$TASK" "진척 없음(새 커밋·변경 없이 continue). 로그: $LOG"
    continue
  fi

  # 테스트 통과: 작업이 넘어갔든 아니든 연속 실패 기록을 지운다
  FAIL_TASK=""
  FAIL_COUNT=0

  if [[ "$STATUS" == "done" ]]; then
    LEFT="$(unchecked_count)"
    if (( LEFT > 0 )); then
      stop "status는 done인데 체크되지 않은 작업이 ${LEFT}개 남았습니다 (다음: $(next_task))."
    fi
    echo "[loop] 모든 작업 완료(status: done). 결과를 확인하고 push·PR 하세요."
    exit 0
  fi
done

echo "[loop] $COUNT회 실행을 마쳤습니다. git log와 loop/TASKS.md, progress.md를 확인한 뒤 push·PR 하세요."
