#!/bin/bash
# share-start.sh로 켠 잠자기 막기·터널·중계 서버를 모두 끈다.
set -u
cd "$(dirname "$0")/.." || exit 1
RUN="$(pwd)/.share"
for name in caffeinate tunnel relay; do
  f="$RUN/$name.pid"
  if [ -f "$f" ]; then
    pid="$(cat "$f")"
    if kill -0 "$pid" 2>/dev/null; then
      kill "$pid" 2>/dev/null
      for _ in 1 2 3 4 5; do kill -0 "$pid" 2>/dev/null || break; sleep 1; done
      kill -0 "$pid" 2>/dev/null && kill -9 "$pid" 2>/dev/null
      echo "$name 끔"
    fi
    rm -f "$f"
  fi
done
rm -f "$RUN/url.txt"
echo "모두 껐습니다. (주소는 더 이상 쓸 수 없고, 다음에 켜면 새 주소가 생깁니다)"
