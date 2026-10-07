#!/bin/sh
set -eu
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
if [ -n "${JAVA_HOME:-}" ]; then
  JAVA_EXE="$JAVA_HOME/bin/java"
else
  JAVA_EXE="java"
fi
if ! command -v "$JAVA_EXE" >/dev/null 2>&1; then
  echo "ERROR: Java was not found. Install JDK 25 and set JAVA_HOME to it." >&2
  exit 1
fi
exec "$JAVA_EXE" -jar "$SCRIPT_DIR/gradle/wrapper/gradle-wrapper.jar" "$@"
