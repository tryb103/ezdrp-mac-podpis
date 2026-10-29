#!/bin/zsh
# Test end-to-end w trybie próbnym: pobiera plik z EZD RP, konwertuje, podpisuje na karcie,
# zapisuje wynik lokalnie w spike/signed-*.pdf i NIE wysyła nic do EZD RP.
# Użycie: ./run-dryrun.sh "ezdrp://TOKEN/BASE64/5"
set -e
ROOT="$HOME/ezdrp-mac-podpis"
export JAVA_HOME="$ROOT/tools/jdk/Contents/Home"
touch "$ROOT/DRYRUN"
exec "$JAVA_HOME/bin/java" --add-exports=jdk.crypto.cryptoki/sun.security.pkcs11.wrapper=ALL-UNNAMED -Dezdrp.debug=true -Dezdrp.dryrun=true -Dezdrp.pinfile=true \
  -cp "$ROOT/app/target/ezdrp-podpis.jar:$ROOT/app/target/lib/*" pl.ezdrp.podpis.Main "$1"
