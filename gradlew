#!/usr/bin/env sh
set -eu
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
cd "$ROOT"
VERSION=8.9
TOOLS="$ROOT/.tools"
DIST="$TOOLS/gradle-$VERSION"
GRADLE="$DIST/bin/gradle"
if [ ! -x "$GRADLE" ]; then
  mkdir -p "$TOOLS"
  ZIP="$TOOLS/gradle-$VERSION-bin.zip"
  URL="https://services.gradle.org/distributions/gradle-$VERSION-bin.zip"
  SUM_URL="$URL.sha256"
  if command -v curl >/dev/null 2>&1; then
    curl --fail --location --retry 3 "$URL" --output "$ZIP"
    EXPECTED="$(curl --fail --location "$SUM_URL" | tr -d '[:space:]' | tr '[:upper:]' '[:lower:]')"
  elif command -v wget >/dev/null 2>&1; then
    wget -O "$ZIP" "$URL"
    EXPECTED="$(wget -qO- "$SUM_URL" | tr -d '[:space:]' | tr '[:upper:]' '[:lower:]')"
  else
    echo 'curl atau wget diperlukan untuk mengunduh Gradle.' >&2
    exit 1
  fi
  ACTUAL="$(sha256sum "$ZIP" | awk '{print $1}' | tr '[:upper:]' '[:lower:]')"
  if [ -z "$EXPECTED" ] || [ "$EXPECTED" != "$ACTUAL" ]; then
    rm -f "$ZIP"
    echo 'Checksum Gradle tidak cocok; unduhan dihentikan.' >&2
    exit 1
  fi
  unzip -q "$ZIP" -d "$TOOLS"
  rm -f "$ZIP"
fi
exec "$GRADLE" "$@"
