#!/usr/bin/env bash
# Starts whisper.cpp and the Spring Boot server together, for local
# development. The OpenAI key is deliberately not kept in this repo's own
# .env: it is read from the sibling ../bolivia project's .env instead, so
# there is exactly one place the key is ever written down on this machine.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SERVER_DIR="$SCRIPT_DIR/server"
BOLIVIA_ENV="$SCRIPT_DIR/../bolivia/.env"

WHISPER_BIN="$HOME/whisper.cpp/build/bin/whisper-server"
WHISPER_MODEL="$HOME/whisper.cpp/models/ggml-large-v3-turbo-q5_0.bin"
WHISPER_PORT=8090
WHISPER_LANGUAGE=pt

JAVA_HOME_21=/usr/lib/jvm/java-21-openjdk-amd64

if [[ ! -f "$BOLIVIA_ENV" ]]; then
  echo "run.sh: expected an OpenAI key at $BOLIVIA_ENV, found none" >&2
  exit 1
fi

# server/.env, if present, supplies everything else the server needs
# (currently just OPENAI_MODEL); the key itself always comes from bolivia.
if [[ -f "$SERVER_DIR/.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "$SERVER_DIR/.env"
  set +a
fi

OPENAI_API_KEY="$(grep -m1 '^OPENAI_API_KEY=' "$BOLIVIA_ENV" | cut -d= -f2-)"
if [[ -z "$OPENAI_API_KEY" ]]; then
  echo "run.sh: $BOLIVIA_ENV has no OPENAI_API_KEY" >&2
  exit 1
fi
export OPENAI_API_KEY
export OPENAI_MODEL="${OPENAI_MODEL:-o3}"

if [[ ! -x "$WHISPER_BIN" ]]; then
  echo "run.sh: whisper-server not found at $WHISPER_BIN" >&2
  exit 1
fi

echo "run.sh: starting whisper-server on port $WHISPER_PORT"
"$WHISPER_BIN" -m "$WHISPER_MODEL" -l "$WHISPER_LANGUAGE" --port "$WHISPER_PORT" &
WHISPER_PID=$!

cleanup() {
  echo "run.sh: stopping whisper-server (pid $WHISPER_PID)"
  kill "$WHISPER_PID" 2>/dev/null || true
  wait "$WHISPER_PID" 2>/dev/null || true
}
trap cleanup EXIT INT TERM

# whisper-server takes a few seconds to load the model; give it a bounded
# head start rather than a fixed sleep, though the server tolerates it being
# late (transcription is best-effort and never blocks a capture).
for _ in $(seq 1 30); do
  if (exec 3<>"/dev/tcp/127.0.0.1/$WHISPER_PORT") 2>/dev/null; then
    exec 3>&-
    break
  fi
  sleep 1
done

echo "run.sh: starting the Spring Boot server"
cd "$SERVER_DIR"
JAVA_HOME="$JAVA_HOME_21" mvn spring-boot:run
