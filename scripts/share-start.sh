#!/bin/bash
# AI 중계 서버 + Cloudflare 임시 터널 + 잠자기 막기를 한 번에 켠다 (맥미니에서 실행).
# 키(config/ai.properties)는 읽지도 출력하지도 않는다. 중계 서버(Java)만 키를 읽는다.
# 사용: scripts/share-start.sh        끄기: scripts/share-stop.sh
set -u
cd "$(dirname "$0")/.." || exit 1
ROOT="$(pwd)"
RUN="$ROOT/.share"
mkdir -p "$RUN"

# 이미 켜져 있으면 주소만 다시 보여 준다
if [ -f "$RUN/relay.pid" ] && kill -0 "$(cat "$RUN/relay.pid")" 2>/dev/null; then
  echo "이미 켜져 있습니다. 주소: $(cat "$RUN/url.txt" 2>/dev/null || echo '(아직 없음)')"
  echo "다시 켜려면 먼저 scripts/share-stop.sh"
  exit 0
fi

# 1) 준비물 확인 (키 파일은 있는지만 본다)
if [ ! -f config/ai.properties ]; then
  echo "config/ai.properties가 없습니다. 키는 직접 넣어 주세요 (config/ai.properties.example 참고)." >&2
  exit 1
fi
if ! command -v cloudflared >/dev/null 2>&1; then
  echo "cloudflared가 없습니다. 터미널에서 설치하세요: brew install cloudflared" >&2
  exit 1
fi
JAVA_HOME="$(/usr/libexec/java_home -v 21 2>/dev/null)"
if [ -z "$JAVA_HOME" ]; then
  echo "JDK 21이 필요합니다." >&2
  exit 1
fi
JAVA="$JAVA_HOME/bin/java"
JAR="$ROOT/target/nursing-chart-trainer.jar"
if [ ! -f "$JAR" ]; then
  echo "JAR이 없어 빌드합니다 (1~2분)…"
  JAVA_HOME="$JAVA_HOME" ./mvnw -q -DskipTests package || { echo "빌드 실패" >&2; exit 1; }
fi
PORT=8787
if [ -f config/relay.properties ]; then
  P="$(grep -E '^relay\.port=' config/relay.properties | tail -1 | cut -d= -f2 | tr -d '[:space:]')"
  [ -n "$P" ] && PORT="$P"
fi

# 2) 중계 서버 켜기 (127.0.0.1에만 연다)
nohup "$JAVA" -cp "$JAR" app.ai.AiRelayServer >> "$RUN/relay.log" 2>&1 &
echo $! > "$RUN/relay.pid"
ok=""
for _ in $(seq 1 30); do
  if curl -fsS -H "X-Nursing-Chart-App: nursing-chart-trainer" "http://127.0.0.1:$PORT/health" >/dev/null 2>&1; then ok=1; break; fi
  if ! kill -0 "$(cat "$RUN/relay.pid")" 2>/dev/null; then break; fi
  sleep 1
done
if [ -z "$ok" ]; then
  echo "중계 서버를 켜지 못했습니다. 기록: $RUN/relay.log" >&2
  tail -3 "$RUN/relay.log" >&2
  "$ROOT/scripts/share-stop.sh" >/dev/null 2>&1
  exit 1
fi
echo "1/3 중계 서버 켜짐 (http://127.0.0.1:$PORT)"

# 3) Cloudflare 임시 터널 켜기 (계정 없이 trycloudflare.com 주소가 생긴다)
: > "$RUN/tunnel.log"
nohup cloudflared tunnel --no-autoupdate --url "http://127.0.0.1:$PORT" >> "$RUN/tunnel.log" 2>&1 &
echo $! > "$RUN/tunnel.pid"
URL=""
for _ in $(seq 1 40); do
  URL="$(grep -oE 'https://[a-z0-9-]+\.trycloudflare\.com' "$RUN/tunnel.log" | head -1)"
  [ -n "$URL" ] && break
  sleep 1
done
if [ -z "$URL" ]; then
  echo "터널 주소를 받지 못했습니다. 기록: $RUN/tunnel.log" >&2
  "$ROOT/scripts/share-stop.sh" >/dev/null 2>&1
  exit 1
fi
echo "$URL" > "$RUN/url.txt"
echo "2/3 터널 켜짐"

# 4) 잠자기 막기 (중계 서버가 켜져 있는 동안)
nohup caffeinate -dims -w "$(cat "$RUN/relay.pid")" >/dev/null 2>&1 &
echo $! > "$RUN/caffeinate.pid"
echo "3/3 잠자기 막기 켜짐"

# 5) 터널을 거쳐 연결 확인 (새 주소는 인터넷에 퍼지는 데 30~60초 걸린다)
echo "인터넷에서 주소가 열리기를 기다립니다 (최대 2분)…"
reach=""
for _ in $(seq 1 60); do
  if curl -fsS -H "X-Nursing-Chart-App: nursing-chart-trainer" "$URL/health" >/dev/null 2>&1; then reach=1; break; fi
  sleep 2
done
echo
echo "AI 서버 주소: $URL"
if [ -n "$reach" ]; then
  echo "(인터넷에서 연결 확인됨) 팀원에게 이 주소를 보내 주세요."
else
  echo "(아직 인터넷에서 연결 확인 안 됨. 1~2분 뒤 프로그램에서 다시 시도해 보세요)"
fi
echo "끄기: scripts/share-stop.sh"
