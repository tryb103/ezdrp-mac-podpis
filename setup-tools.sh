#!/bin/zsh
# Pobiera narzędzia do kompilacji aplikacji (tylko dla programistów): JDK 21 (Eclipse Temurin) i Apache Maven.
# Nic nie instaluje w systemie – wszystko trafia do katalogu tools/ obok tego skryptu (lub do $TOOLS_DIR).
# Xcode ani Homebrew nie są potrzebne.
# Użycie: ./setup-tools.sh
set -e
ROOT="${0:A:h}"
TOOLS="${TOOLS_DIR:-$ROOT/tools}"
MAVEN_VER="${MAVEN_VER:-3.9.9}"
ADOPTIUM="https://api.adoptium.net/v3"
mkdir -p "$TOOLS"
TMP=$(mktemp -d /tmp/ezdrp-tools.XXXXXX); trap 'rm -rf "$TMP"' EXIT

if [[ -x "$TOOLS/jdk/Contents/Home/bin/javac" ]]; then
  echo "[OK] JDK: $("$TOOLS/jdk/Contents/Home/bin/javac" -version 2>&1) ($TOOLS/jdk)"
else
  echo "Pobieram JDK 21 (Temurin, Apple Silicon, ok. 190 MB)..."
  curl -fSL --progress-bar -o "$TMP/jdk.tar.gz" "$ADOPTIUM/binary/latest/21/ga/mac/aarch64/jdk/hotspot/normal/eclipse"
  # Suma kontrolna archiwum .tar.gz = sekcja "package" (nie "installer") w odpowiedzi API.
  SUM=$(curl -fsSL "$ADOPTIUM/assets/latest/21/hotspot?architecture=aarch64&image_type=jdk&os=mac&vendor=eclipse" \
        | tr -d '\n ' | grep -o '"package":{[^}]*}' | grep -o '"checksum":"[0-9a-f]*"' | head -1 | grep -o '[0-9a-f]\{64\}' || true)
  if [[ -z "$SUM" ]]; then echo "Nie udało się pobrać sumy kontrolnej JDK – przerwano" >&2; exit 1; fi
  if ! echo "$SUM  $TMP/jdk.tar.gz" | shasum -a 256 -c - >/dev/null; then echo "Suma kontrolna JDK nie zgadza się – przerwano" >&2; exit 1; fi
  echo "[OK] suma kontrolna JDK"
  mkdir -p "$TMP/jdk" && tar -xzf "$TMP/jdk.tar.gz" -C "$TMP/jdk"
  rm -rf "$TOOLS/jdk"; mv "$TMP"/jdk/jdk-* "$TOOLS/jdk"
  echo "[OK] JDK: $("$TOOLS/jdk/Contents/Home/bin/javac" -version 2>&1) → $TOOLS/jdk"
fi

if [[ -x "$TOOLS/maven/bin/mvn" ]]; then
  echo "[OK] Maven: $TOOLS/maven"
else
  echo "Pobieram Apache Maven $MAVEN_VER..."
  BASE="https://archive.apache.org/dist/maven/maven-3/$MAVEN_VER/binaries"
  curl -fSL --progress-bar -o "$TMP/maven.tar.gz" "$BASE/apache-maven-$MAVEN_VER-bin.tar.gz"
  SUM=$(curl -fsSL "$BASE/apache-maven-$MAVEN_VER-bin.tar.gz.sha512" | grep -o '[0-9a-f]\{128\}' | head -1)
  if ! echo "$SUM  $TMP/maven.tar.gz" | shasum -a 512 -c - >/dev/null; then echo "Suma kontrolna Maven nie zgadza się – przerwano" >&2; exit 1; fi
  echo "[OK] suma kontrolna Maven"
  mkdir -p "$TMP/mvn" && tar -xzf "$TMP/maven.tar.gz" -C "$TMP/mvn"
  rm -rf "$TOOLS/maven"; mv "$TMP"/mvn/apache-maven-* "$TOOLS/maven"
  echo "[OK] Maven $MAVEN_VER → $TOOLS/maven"
fi
echo "Gotowe. Kompilacja i instalacja aplikacji: $ROOT/build-app.sh"
