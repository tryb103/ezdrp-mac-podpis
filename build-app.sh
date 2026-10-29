#!/bin/zsh
# Kompiluje aplikację (Maven), pakuje w „EZD RP Podpis.app” (jpackage), dodaje schemat ezdrp:// do Info.plist,
# podpisuje ad hoc, instaluje do ~/Applications i rejestruje w LaunchServices.
# Opcje:  --zip   dodatkowo tworzy dist/EZD-RP-Podpis-<wersja>-macOS.zip + .sha256 (do wydania na GitHub)
# Wymaga narzędzi z ./setup-tools.sh (JDK 21 + Maven w tools/). Xcode nie jest potrzebny.
set -e
ROOT="${0:A:h}"
VERSION="${VERSION:-1.0.0}"
export JAVA_HOME="$ROOT/tools/jdk/Contents/Home"
[[ -x "$JAVA_HOME/bin/java" && -x "$ROOT/tools/maven/bin/mvn" ]] || { echo "Brak narzędzi – uruchom najpierw $ROOT/setup-tools.sh" >&2; exit 2; }
cd "$ROOT/app"
"$ROOT/tools/maven/bin/mvn" -q -B -DskipTests clean package

rm -rf target/pkg-input target/dist
mkdir -p target/pkg-input target/dist
cp target/ezdrp-podpis.jar target/pkg-input/
cp -R target/lib target/pkg-input/lib
"$JAVA_HOME/bin/jpackage" --type app-image --name "EZD RP Podpis" --app-version "$VERSION" \
  --vendor "EZD RP Mac" --input target/pkg-input --main-jar ezdrp-podpis.jar \
  --main-class pl.ezdrp.podpis.Main --dest target/dist \
  --mac-package-identifier pl.ezdrp.podpis --mac-package-name "EZD RP Podpis" \
  --java-options "-Dapple.awt.UIElement=true" \
  --java-options "--add-exports=jdk.crypto.cryptoki/sun.security.pkcs11.wrapper=ALL-UNNAMED"

APP="target/dist/EZD RP Podpis.app"
P="$APP/Contents/Info.plist"
/usr/libexec/PlistBuddy -c "Add :CFBundleURLTypes array" "$P"
/usr/libexec/PlistBuddy -c "Add :CFBundleURLTypes:0 dict" "$P"
/usr/libexec/PlistBuddy -c "Add :CFBundleURLTypes:0:CFBundleURLName string pl.ezdrp.podpis.url" "$P"
/usr/libexec/PlistBuddy -c "Add :CFBundleURLTypes:0:CFBundleURLSchemes array" "$P"
/usr/libexec/PlistBuddy -c "Add :CFBundleURLTypes:0:CFBundleURLSchemes:0 string ezdrp" "$P"
/usr/libexec/PlistBuddy -c "Add :LSUIElement bool true" "$P"

# Podpis ad hoc (bez konta Apple Developer); instalator zdejmuje kwarantannę. Do dystrybucji przez internet
# bez tego kroku potrzebny byłby Developer ID + notaryzacja.
codesign --force --deep --sign - "$APP"

LS=/System/Library/Frameworks/CoreServices.framework/Frameworks/LaunchServices.framework/Support/lsregister
mkdir -p "$HOME/Applications"
rm -rf "$HOME/Applications/EZD RP Podpis.app"
cp -R "$APP" "$HOME/Applications/"
"$LS" -u "$ROOT/app/$APP" >/dev/null 2>&1 || true     # kopia w target/ nie ma obsługiwać linków
"$LS" -f "$HOME/Applications/EZD RP Podpis.app"
echo "OK: $HOME/Applications/EZD RP Podpis.app ($VERSION)"

if [[ "$1" == "--zip" ]]; then
  mkdir -p "$ROOT/dist"
  ZIP="$ROOT/dist/EZD-RP-Podpis-$VERSION-macOS.zip"
  rm -f "$ZIP" "$ZIP.sha256"
  ditto -c -k --keepParent "$APP" "$ZIP"
  (cd "$ROOT/dist" && shasum -a 256 "$(basename "$ZIP")" > "$(basename "$ZIP").sha256")
  echo "OK: $ZIP"; cat "$ZIP.sha256"
fi
