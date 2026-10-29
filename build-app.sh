#!/bin/zsh
# Buduje jar (Maven), pakuje w "EZD RP Podpis.app" (jpackage), dodaje schemat ezdrp:// do Info.plist,
# podpisuje ad hoc i instaluje do ~/Applications, rejestrując w LaunchServices.
set -e
ROOT="$HOME/ezdrp-mac-podpis"
export JAVA_HOME="$ROOT/tools/jdk/Contents/Home"
cd "$ROOT/app"
"$ROOT/tools/maven/bin/mvn" -q -B -DskipTests package

rm -rf target/pkg-input target/dist
mkdir -p target/pkg-input target/dist
cp target/ezdrp-podpis.jar target/pkg-input/
cp -R target/lib target/pkg-input/lib
"$JAVA_HOME/bin/jpackage" --type app-image --name "EZD RP Podpis" --app-version 1.0.0 \
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

# Podpis ad hoc (bez konta Apple Developer); do dystrybucji na inne Maki potrzebny Developer ID + notaryzacja.
codesign --force --deep --sign - "$APP"

mkdir -p "$HOME/Applications"
rm -rf "$HOME/Applications/EZD RP Podpis.app"
cp -R "$APP" "$HOME/Applications/"
/System/Library/Frameworks/CoreServices.framework/Frameworks/LaunchServices.framework/Support/lsregister -f "$HOME/Applications/EZD RP Podpis.app"
echo "OK: $HOME/Applications/EZD RP Podpis.app"
