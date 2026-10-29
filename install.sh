#!/bin/zsh
# =====================================================================================
#  EZD RP Podpis – instalator dla macOS (Apple Silicon)
#
#  Jedna komenda w Terminalu instaluje wszystko, co potrzebne do podpisywania w EZD RP:
#    1. Szafir 2.0 (KIR) – sterownik karty kryptograficznej,
#    2. LibreOffice – konwersja dokumentów Word (DOCX) do PDF (można pominąć),
#    3. EZD RP Podpis.app – obsługa linku ezdrp:// z przeglądarki.
#
#  Użycie:
#    curl -fsSL https://raw.githubusercontent.com/tryb103/ezdrp-mac-podpis/main/install.sh | zsh
#    ... | zsh -s -- --sprawdz          tylko sprawdza stan instalacji, nic nie zmienia
#    ... | zsh -s -- --bez-libreoffice  nie instaluje LibreOffice (podpis tylko plików PDF)
#    ... | zsh -s -- --odinstaluj       usuwa EZD RP Podpis.app (Szafir i LibreOffice zostają)
#
#  Uruchomiony z katalogu repozytorium używa lokalnego pliku dist/*.zip (bez pobierania).
# =====================================================================================
set -e
setopt NULL_GLOB

REPO="tryb103/ezdrp-mac-podpis"
APP_NAME="EZD RP Podpis.app"
APP_DEST="$HOME/Applications/$APP_NAME"
SZAFIR_LIB="/Applications/Szafir2.app/Contents/app/libPKCS11Graphite.dylib"
# Oficjalna strona KIR z aplikacjami: https://www.elektronicznypodpis.pl/en/applications-and-drivers
SZAFIR_PKG_URL="${SZAFIR_PKG_URL:-https://www.elektronicznypodpis.pl/storage/file/core_files/2026/1/14/8fbddaefe0f0ee566625cd052a50e3a0/Szafir2-703.pkg}"
LO_BASE="https://download.documentfoundation.org/libreoffice/stable"
LS=/System/Library/Frameworks/CoreServices.framework/Frameworks/LaunchServices.framework/Support/lsregister

TYLKO_SPRAWDZ=0; BEZ_LO=0; ODINSTALUJ=0
for a in "$@"; do
  case "$a" in
    --sprawdz|--check) TYLKO_SPRAWDZ=1 ;;
    --bez-libreoffice) BEZ_LO=1 ;;
    --odinstaluj|--uninstall) ODINSTALUJ=1 ;;
    -h|--help) sed -n '2,17p' "$0" 2>/dev/null; exit 0 ;;
    *) echo "Nieznana opcja: $a (dostępne: --sprawdz, --bez-libreoffice, --odinstaluj)"; exit 2 ;;
  esac
done

ok()   { print -P "%F{green}[OK]%f    $1"; }
brak() { print -P "%F{red}[BRAK]%f  $1"; }
info() { print -P "%F{blue}[INFO]%f  $1"; }
krok() { print -P "\n%F{cyan}%B== $1 ==%b%f"; }
blad() { print -P "%F{red}%BBŁĄD:%b%f $1" >&2; exit 1; }

TMP=$(mktemp -d /tmp/ezdrp-install.XXXXXX)
trap 'rm -rf "$TMP"' EXIT

# ------------------------------------------------------------------ sprawdzenie stanu
sprawdz() {
  local problemy=0
  krok "Stan instalacji"
  [[ "$(uname -m)" == "arm64" ]] && ok "Mac z procesorem Apple Silicon" || { brak "Ten Mac ma procesor Intel – aplikacja jest zbudowana tylko dla Apple Silicon"; problemy=1; }
  info "macOS $(sw_vers -productVersion)"
  [[ -f "$SZAFIR_LIB" ]] && ok "Szafir 2.0 (sterownik karty KIR): /Applications/Szafir2.app" || { brak "Szafir 2.0 – brak sterownika karty"; problemy=1; }
  if [[ -d /Applications/LibreOffice.app || -d "$HOME/Applications/LibreOffice.app" ]]; then ok "LibreOffice (konwersja DOCX→PDF)"; else info "LibreOffice nie jest zainstalowany – podpis dokumentów Word (DOCX) nie zadziała, PDF tak"; fi
  if [[ -d "$APP_DEST" ]]; then
    local v; v=$(/usr/libexec/PlistBuddy -c 'Print :CFBundleShortVersionString' "$APP_DEST/Contents/Info.plist" 2>/dev/null || echo "?")
    ok "EZD RP Podpis.app $v: $APP_DEST"
  else brak "EZD RP Podpis.app nie jest zainstalowana"; problemy=1; fi
  if "$LS" -dump 2>/dev/null | grep -q "path:.*$APP_DEST"; then ok "Rejestracja linków ezdrp:// w systemie"; else brak "Linki ezdrp:// nie są zarejestrowane"; problemy=1; fi
  [[ -d "/Applications/Google Chrome.app" ]] && ok "Google Chrome" || info "Nie znaleziono Google Chrome – linki ezdrp:// działają też w Safari i Firefoksie, ale testowany był Chrome"
  if system_profiler SPUSBDataType 2>/dev/null | grep -q -i -E 'smart ?card|ACR|ACS|Omnikey|CCID|reader|czytnik'; then ok "Czytnik kart podłączony przez USB"; else info "Nie wykryto czytnika kart USB – podłącz czytnik z kartą przed podpisywaniem"; fi
  [[ -f "$HOME/ezdrp-mac-podpis/DRYRUN" ]] && info "Tryb próbny (plik ~/ezdrp-mac-podpis/DRYRUN) – podpisy NIE są wysyłane do EZD RP" || true
  echo
  [[ $problemy -eq 0 ]] && ok "Wszystko gotowe. W EZD RP kliknij „Podpisz” – pojawi się okno PIN." || info "Uruchom instalator bez opcji --sprawdz, aby uzupełnić brakujące elementy."
  return $problemy
}

if [[ $TYLKO_SPRAWDZ -eq 1 ]]; then sprawdz; exit $?; fi

# ------------------------------------------------------------------ odinstalowanie
if [[ $ODINSTALUJ -eq 1 ]]; then
  krok "Odinstalowanie EZD RP Podpis"
  [[ -d "$APP_DEST" ]] && "$LS" -u "$APP_DEST" >/dev/null 2>&1 || true
  rm -rf "$APP_DEST"
  ok "Usunięto $APP_DEST (logi w ~/Library/Logs/EZD RP Podpis zostawiono; Szafir i LibreOffice bez zmian)"
  exit 0
fi

# ------------------------------------------------------------------ wymagania
krok "Sprawdzenie komputera"
[[ "$(uname -m)" == "arm64" ]] || blad "Aplikacja działa tylko na Macach z procesorem Apple Silicon (M1 lub nowszy)."
MAJOR=$(sw_vers -productVersion | cut -d. -f1)
[[ "$MAJOR" -ge 13 ]] || blad "Wymagany macOS 13 (Ventura) lub nowszy. Zaktualizuj system w Ustawieniach systemowych."
ok "macOS $(sw_vers -productVersion), Apple Silicon"

# ------------------------------------------------------------------ 1. Szafir 2.0
krok "1/3 Szafir 2.0 (KIR) – sterownik karty"
if [[ -f "$SZAFIR_LIB" ]]; then
  ok "Szafir 2.0 już zainstalowany"
else
  info "Pobieram pakiet Szafir 2.0 ze strony KIR (ok. 100 MB)..."
  curl -fSL --progress-bar -o "$TMP/Szafir2.pkg" "$SZAFIR_PKG_URL" || blad "Nie udało się pobrać Szafir 2.0. Pobierz ręcznie ze strony https://www.elektronicznypodpis.pl/en/applications-and-drivers (pozycja „Szafir (macOS)”) i zainstaluj, potem uruchom ten instalator ponownie."
  if pkgutil --check-signature "$TMP/Szafir2.pkg" | grep -q -i -E 'Developer ID Installer: KRAJOWA IZBA ROZLICZENIOWA'; then
    ok "Podpis pakietu: $(pkgutil --check-signature "$TMP/Szafir2.pkg" | grep -m1 -o 'Developer ID Installer: [^(]*' )"
  else
    blad "Pakiet Szafir 2.0 nie ma oczekiwanego podpisu KIR – przerwano. Pobierz go ręcznie ze strony KIR."
  fi
  info "Instalacja wymaga hasła administratora Maca (podczas wpisywania nic nie będzie widać – to normalne)."
  sudo installer -pkg "$TMP/Szafir2.pkg" -target / >/dev/null || blad "Instalacja Szafir 2.0 nie powiodła się."
  [[ -f "$SZAFIR_LIB" ]] && ok "Szafir 2.0 zainstalowany w /Applications/Szafir2.app" || blad "Po instalacji nie znaleziono sterownika $SZAFIR_LIB"
fi

# ------------------------------------------------------------------ 2. LibreOffice
krok "2/3 LibreOffice – konwersja dokumentów Word do PDF"
if [[ $BEZ_LO -eq 1 ]]; then
  info "Pominięto na życzenie (--bez-libreoffice). Dokumenty DOCX nie będą podpisywane."
elif [[ -d /Applications/LibreOffice.app || -d "$HOME/Applications/LibreOffice.app" ]]; then
  ok "LibreOffice już zainstalowany"
else
  LO_VER=$(curl -fsSL "$LO_BASE/" | grep -o 'href="[0-9][0-9.]*/"' | tr -d 'href="/' | sort -V | tail -1)
  [[ -n "$LO_VER" ]] || blad "Nie udało się ustalić wersji LibreOffice. Zainstaluj ręcznie z https://www.libreoffice.org/download/ (wersja dla Apple Silicon) albo uruchom z --bez-libreoffice."
  LO_FILE="LibreOffice_${LO_VER}_MacOS_aarch64.dmg"
  info "Pobieram LibreOffice $LO_VER (ok. 300 MB, może potrwać kilka minut)..."
  curl -fSL --progress-bar -C - -o "$TMP/$LO_FILE" "$LO_BASE/$LO_VER/mac/aarch64/$LO_FILE" || blad "Pobieranie LibreOffice nie powiodło się. Zainstaluj ręcznie z https://www.libreoffice.org/download/ albo uruchom z --bez-libreoffice."
  LO_SHA=$(curl -fsSL "$LO_BASE/$LO_VER/mac/aarch64/$LO_FILE.mirrorlist" | grep -o 'SHA-256 Hash: *[0-9a-f]*' | grep -o '[0-9a-f]\{64\}' | head -1)
  if [[ -n "$LO_SHA" ]]; then
    echo "$LO_SHA  $TMP/$LO_FILE" | shasum -a 256 -c - >/dev/null || blad "Suma kontrolna LibreOffice nie zgadza się – plik uszkodzony. Uruchom instalator ponownie."
    ok "Suma kontrolna LibreOffice poprawna"
  else
    info "Nie udało się pobrać sumy kontrolnej LibreOffice – kontynuuję bez weryfikacji"
  fi
  MNT=$(hdiutil attach -nobrowse -readonly "$TMP/$LO_FILE" | grep -o '/Volumes/.*' | head -1)
  [[ -n "$MNT" ]] || blad "Nie udało się otworzyć obrazu LibreOffice."
  mkdir -p "$HOME/Applications"
  ditto "$MNT/LibreOffice.app" "$HOME/Applications/LibreOffice.app"
  hdiutil detach "$MNT" -quiet || true
  xattr -dr com.apple.quarantine "$HOME/Applications/LibreOffice.app" 2>/dev/null || true
  ok "LibreOffice $LO_VER zainstalowany w ~/Applications"
fi

# ------------------------------------------------------------------ 3. EZD RP Podpis.app
krok "3/3 EZD RP Podpis.app"
SRC_ZIP=""
SCRIPT_DIR="${0:A:h}"
LOCAL=( "$SCRIPT_DIR"/dist/EZD-RP-Podpis-*-macOS.zip )
if [[ -n "${LOCAL[1]:-}" && -f "${LOCAL[1]}" ]]; then
  SRC_ZIP=$(ls -t "$SCRIPT_DIR"/dist/EZD-RP-Podpis-*-macOS.zip | head -1)
  info "Używam lokalnego pakietu: $SRC_ZIP"
else
  info "Pobieram najnowsze wydanie z GitHub ($REPO)..."
  AUTH=(); [[ -n "${GITHUB_TOKEN:-}" ]] && AUTH=(-H "Authorization: Bearer $GITHUB_TOKEN")
  REL=$(curl -fsSL "${AUTH[@]}" -H "Accept: application/vnd.github+json" "https://api.github.com/repos/$REPO/releases/latest") || blad "Nie udało się połączyć z GitHub. Sprawdź internet albo pobierz plik EZD-RP-Podpis-*-macOS.zip ręcznie z https://github.com/$REPO/releases i uruchom ten skrypt z katalogu, w którym obok jest folder dist/."
  TAG=$(echo "$REL" | grep -o '"tag_name": *"[^"]*"' | head -1 | sed 's/.*: *"//; s/"//')
  if [[ -n "${GITHUB_TOKEN:-}" ]]; then
    ZIP_URL=$(echo "$REL" | tr ',' '\n' | grep -B3 '"name": *"EZD-RP-Podpis-[^"]*-macOS.zip"' | grep -o '"url": *"https://api.github.com/repos/[^"]*/assets/[0-9]*"' | head -1 | sed 's/.*: *"//; s/"//')
    SHA_URL=$(echo "$REL" | tr ',' '\n' | grep -B3 '"name": *"EZD-RP-Podpis-[^"]*-macOS.zip.sha256"' | grep -o '"url": *"https://api.github.com/repos/[^"]*/assets/[0-9]*"' | head -1 | sed 's/.*: *"//; s/"//')
    ACCEPT=(-H "Accept: application/octet-stream")
  else
    ZIP_URL=$(echo "$REL" | grep -o '"browser_download_url": *"[^"]*EZD-RP-Podpis-[^"]*-macOS.zip"' | head -1 | sed 's/.*: *"//; s/"//')
    SHA_URL=$(echo "$REL" | grep -o '"browser_download_url": *"[^"]*EZD-RP-Podpis-[^"]*-macOS.zip.sha256"' | head -1 | sed 's/.*: *"//; s/"//')
    ACCEPT=()
  fi
  [[ -n "$ZIP_URL" ]] || blad "W wydaniu $TAG nie ma pliku aplikacji dla macOS."
  info "Wydanie $TAG – pobieram aplikację (ok. 75 MB)..."
  curl -fSL --progress-bar "${AUTH[@]}" "${ACCEPT[@]}" -o "$TMP/app.zip" "$ZIP_URL" || blad "Pobieranie aplikacji nie powiodło się."
  if [[ -n "$SHA_URL" ]]; then
    curl -fsSL "${AUTH[@]}" "${ACCEPT[@]}" -o "$TMP/app.zip.sha256" "$SHA_URL" || true
    if [[ -s "$TMP/app.zip.sha256" ]]; then
      EXPECTED=$(grep -o '^[0-9a-f]\{64\}' "$TMP/app.zip.sha256" | head -1)
      echo "$EXPECTED  $TMP/app.zip" | shasum -a 256 -c - >/dev/null || blad "Suma kontrolna aplikacji nie zgadza się – plik uszkodzony lub podmieniony. Przerwano."
      ok "Suma kontrolna aplikacji poprawna"
    fi
  fi
  SRC_ZIP="$TMP/app.zip"
fi

ditto -x -k "$SRC_ZIP" "$TMP/unpack"
[[ -d "$TMP/unpack/$APP_NAME" ]] || blad "W pakiecie nie ma $APP_NAME."
codesign -v --deep --strict "$TMP/unpack/$APP_NAME" 2>/dev/null || blad "Aplikacja w pakiecie jest uszkodzona (błąd podpisu kodu)."
mkdir -p "$HOME/Applications"
rm -rf "$APP_DEST"
ditto "$TMP/unpack/$APP_NAME" "$APP_DEST"
# Aplikacja jest podpisana lokalnie (bez certyfikatu Apple Developer) – zdjęcie flagi kwarantanny
# pozwala ją uruchomić bez ostrzeżeń Gatekeepera.
xattr -dr com.apple.quarantine "$APP_DEST" 2>/dev/null || true
"$LS" -f "$APP_DEST"
ok "EZD RP Podpis.app zainstalowana w ~/Applications i zarejestrowana dla linków ezdrp://"

sprawdz || true
cat <<'KONIEC'

Co dalej (pierwsze podpisanie):
  1. Podłącz czytnik z kartą KIR.
  2. W Chrome otwórz EZD RP, wejdź w dokument i kliknij „Podpisz” (typ: Podpis domyślny, PAdES).
  3. Chrome zapyta „Otworzyć EZD RP Podpis?” – zaznacz „Zawsze zezwalaj…” i kliknij „Otwórz EZD RP Podpis”.
  4. W oknie aplikacji wpisz PIN do karty. Po chwili pojawi się „Podpisano 1 plik(ów)”, a EZD RP odświeży dokument.

Dziennik działania (gdy coś pójdzie nie tak): ~/Library/Logs/EZD RP Podpis/ezdrp-podpis.log
KONIEC
