#!/bin/zsh
# Instalacja / naprawa „EZD RP Podpis.app” bez przebudowy.
# Źródło (pierwsze istniejące): dist/EZD-RP-Podpis-*-macOS.zip  albo  app/target/dist/EZD RP Podpis.app
# Użycie: ./install-app.sh            – instaluje/naprawia i rejestruje schemat ezdrp://
#         ./install-app.sh --check    – tylko sprawdza stan (bez zmian)
set -e
ROOT="${0:A:h}"
APP_NAME="EZD RP Podpis.app"
DEST="$HOME/Applications/$APP_NAME"
LS=/System/Library/Frameworks/CoreServices.framework/Frameworks/LaunchServices.framework/Support/lsregister

check() {
  local ok=0
  [[ -d "$DEST" ]] && echo "[OK] aplikacja: $DEST" || { echo "[BRAK] aplikacja: $DEST"; ok=1; }
  if "$LS" -dump 2>/dev/null | grep -q "path:.*$DEST"; then echo "[OK] rejestracja ezdrp:// w LaunchServices"; else echo "[BRAK] rejestracja w LaunchServices"; ok=1; fi
  [[ -f /Applications/Szafir2.app/Contents/app/libPKCS11Graphite.dylib ]] && echo "[OK] sterownik karty KIR (Szafir2)" || { echo "[BRAK] Szafir2 – zainstaluj Szafir 2.0 z KIR"; ok=1; }
  [[ -d "$HOME/Applications/LibreOffice.app" || -d /Applications/LibreOffice.app ]] && echo "[OK] LibreOffice (konwersja DOCX→PDF)" || echo "[UWAGA] brak LibreOffice – podpis DOCX nie zadziała, PDF tak"
  [[ -f "$ROOT/DRYRUN" ]] && echo "[UWAGA] istnieje $ROOT/DRYRUN – tryb próbny, nic nie trafia do EZD RP (usuń: rm \"$ROOT/DRYRUN\")" || echo "[OK] tryb produkcyjny (brak znacznika DRYRUN)"
  grep -q '^127.0.0.1 .*saas1.ezdrp.gov.pl' /etc/hosts 2>/dev/null && echo "[INFO] aktywny wpis hosts → praca przez tunel AnyDesk" || echo "[INFO] brak aktywnego wpisu hosts → połączenie bezpośrednie (sieć firmowa)"
  return $ok
}

if [[ "$1" == "--check" ]]; then check; exit $?; fi

SRC=""
ZIP=$(ls -t "$ROOT"/dist/EZD-RP-Podpis-*-macOS.zip 2>/dev/null | head -1)
if [[ -d "$ROOT/app/target/dist/$APP_NAME" ]]; then
  SRC="$ROOT/app/target/dist/$APP_NAME"
elif [[ -n "$ZIP" ]]; then
  TMP=$(mktemp -d)
  ditto -x -k "$ZIP" "$TMP"
  SRC="$TMP/$APP_NAME"
fi
if [[ -z "$SRC" || ! -d "$SRC" ]]; then
  echo "Brak gotowej aplikacji (dist/*.zip ani app/target/dist). Uruchom ./build-app.sh" >&2; exit 2
fi
mkdir -p "$HOME/Applications"
rm -rf "$DEST"
ditto "$SRC" "$DEST"
xattr -dr com.apple.quarantine "$DEST" 2>/dev/null || true
"$LS" -f "$DEST"
[[ -n "${TMP:-}" ]] && rm -rf "$TMP"
echo "Zainstalowano: $DEST"
check
