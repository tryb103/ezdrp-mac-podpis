# EZD RP Podpis – podpis kwalifikowany z EZD RP na macOS

Zamiennik NASK Desk dla macOS: obsługuje link `ezdrp://…/5` (podpisywanie plików) z EZD RP w przeglądarce.
Po kliknięciu „Podpisz” w EZD RP pojawia się okno PIN, dokument jest podpisywany PAdES na karcie KIR
i odsyłany do EZD RP. Działa produkcyjnie od 2026-10-21.

## Co jest zainstalowane

| Element | Lokalizacja | Rola |
|---|---|---|
| EZD RP Podpis.app | `~/Applications/EZD RP Podpis.app` | handler `ezdrp://`, Java 21 + EU DSS 6.3 (wbudowane środowisko Java) |
| Szafir 2.0 (KIR) | `/Applications/Szafir2.app` | sterownik karty PKCS#11 `libPKCS11Graphite.dylib` |
| LibreOffice | `~/Applications/LibreOffice.app` | tylko konwersja DOCX→PDF przed podpisem PAdES |
| Log | `~/Library/Logs/EZD RP Podpis/ezdrp-podpis.log` | diagnostyka każdego przebiegu |

Aplikacja nie potrzebuje tego katalogu do działania; katalog służy do odbudowy, kopii zapasowej i testów.

## Szybka naprawa / ponowna instalacja (bez kompilacji)

```
~/ezdrp-mac-podpis/install-app.sh --check   # stan: aplikacja, rejestracja, sterownik, tryb
~/ezdrp-mac-podpis/install-app.sh           # ponowna instalacja z dist/*.zip lub z app/target
```

Gotowa aplikacja: `dist/EZD-RP-Podpis-1.0.0-macOS.zip` (kopia także w `~/Documents/EZD RP Podpis/`).
Na innym Macu: rozpakować do `~/Applications`, uruchomić `install-app.sh` (zdejmuje kwarantannę i rejestruje
schemat; podpis ad hoc, bez notaryzacji – przy pierwszym uruchomieniu prawy klik → Otwórz), zainstalować Szafir 2.0
i opcjonalnie LibreOffice.

## Przebudowa ze źródeł

```
~/ezdrp-mac-podpis/build-app.sh     # mvn package → jpackage → ~/Applications + rejestracja
```
Narzędzia w `tools/` (JDK 21 Temurin, Maven 3.9.9, .NET 10 SDK + ilspycmd do dekompilacji). Źródła: `app/`.

## Tryb próbny (bez wysyłki do EZD RP)

```
touch ~/ezdrp-mac-podpis/DRYRUN      # podpisany PDF trafia do spike/signed-*.pdf, EZD RP nic nie dostaje
rm    ~/ezdrp-mac-podpis/DRYRUN      # powrót do produkcji (sprawdza to install-app.sh --check)
```
Walidacja podpisu: `cd spike && ../tools/jdk/Contents/Home/bin/java -cp ".:validator/lib/*" ValidateProbe plik.pdf`.

## Praca spoza sieci firmowej (EZD RP SaaS dopuszcza tylko adresy instytucji)

1. AnyDesk: sesja do komputera w sieci firmowej, reguła tunelu Forward `443 → ezdrp-api.saas1.ezdrp.gov.pl:443`
   (zapisana w `~/.anydesk/user.conf`, uruchamia się przy połączeniu sesji).
2. `/etc/hosts`: odkomentować wiersz `127.0.0.1 ezdrp-api.saas1… ezdrp-web.saas1… sso-idp.saas1… …`
   (`sudo nano /etc/hosts`, potem `sudo dscacheutil -flushcache; sudo killall -HUP mDNSResponder`).
3. Chrome: dla `ezdrp-web.saas1.ezdrp.gov.pl` ustawienie witryny „Dostęp do sieci lokalnej” → Zezwalaj.
W sieci firmowej wiersz w hosts ma być zakomentowany.

## Najczęstsze problemy

- Chrome: „scheme does not have a registered handler” → `install-app.sh`.
- Okno błędu aplikacji → szczegóły w logu; `HTTP 500 filemonitor-save-file-stream` jest znane (aplikacja używa
  `filemonitor-save-file`, wariant strumieniowy tylko jako zapas).
- „Nie znaleziono certyfikatu o numerze seryjnym …” → profil podpisu w EZD RP (Ustawienia użytkownika → Podpisy)
  wskazuje inny certyfikat niż na karcie.
- „PIN zablokowany” → odblokowanie w aplikacji Szafir. Aplikacja wysyła PIN tylko do slotu z właściwym
  certyfikatem (slot „PKI Token 2 (QSCD)”), nigdy do pozostałych.
- Obsługiwane: tylko PAdES (podpis domyślny), PDF i DOCX/DOC. XAdES, znacznik czasu i widoczne pola podpisu
  nie są jeszcze zaimplementowane – aplikacja zgłasza to komunikatem.

Szczegóły protokołu NASK Desk ↔ EZD RP: `PROTOKOL-NASK-DESK.md`.
