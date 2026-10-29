# EZD RP Podpis – informacje dla administratorów i programistów

Instrukcja dla użytkowników końcowych jest w `README.md`. Tu: kompilacja ze źródeł, testowanie bez wysyłki
do EZD RP, wydawanie nowych wersji, praca spoza sieci urzędu i znane zachowania serwera EZD RP.
Szczegóły protokołu NASK Desk ↔ EZD RP: `PROTOKOL-NASK-DESK.md`.

## 1. Kompilacja ze źródeł na czystym Macu (Apple Silicon)

Potrzebne: macOS 13+, internet. **Nie** jest potrzebny Xcode, Command Line Tools ani Homebrew.

```
# 1. Źródła – wariant bez gita (git na czystym Macu wymaga instalacji Command Line Tools):
cd ~ && curl -fsSL -o ezdrp.zip https://github.com/tryb103/ezdrp-mac-podpis/archive/refs/heads/main.zip \
  && ditto -x -k ezdrp.zip . && mv ezdrp-mac-podpis-main ezdrp-mac-podpis && rm ezdrp.zip
#    …albo z gitem:
#    git clone https://github.com/tryb103/ezdrp-mac-podpis.git ~/ezdrp-mac-podpis

# 2. Narzędzia (JDK 21 Temurin + Maven 3.9.9 → ~/ezdrp-mac-podpis/tools, sumy kontrolne weryfikowane):
~/ezdrp-mac-podpis/setup-tools.sh

# 3. Kompilacja, pakowanie (jpackage), podpis ad hoc, instalacja do ~/Applications, rejestracja ezdrp://:
~/ezdrp-mac-podpis/build-app.sh
#    z pakietem do wydania (dist/EZD-RP-Podpis-<wersja>-macOS.zip + .sha256):
VERSION=1.0.0 ~/ezdrp-mac-podpis/build-app.sh --zip
```

Skrypty zakładają katalog `~/ezdrp-mac-podpis`; każdy inny też działa (ścieżki liczone od położenia skryptu).
Pierwsze `mvn package` pobiera zależności (EU DSS 6.3, PDFBox, BouncyCastle…) do `~/.m2` – kilka minut.

## 2. Co jest w repozytorium

| Ścieżka | Zawartość |
|---|---|
| `app/` | Projekt Maven `pl.ezdrp:ezdrp-podpis`, Java 21, EU DSS 6.3 (`dss-pades-pdfbox`, `dss-token`, `dss-cms-object`…) |
| `app/src/main/java/pl/ezdrp/podpis/Main.java` | start, odbiór linku `ezdrp://` (argument lub zdarzenie OpenURI z macOS), przebieg podpisu |
| `EzdrpLink.java` | parser linku `ezdrp://{token}/{base64(apiUrl[*proxy])}/{operacja}` |
| `EzdRpClient.java` | klient API EZD RP (nagłówki RX/CX/x-client-version, pobranie danych i pliku, zapis base64 i strumieniem) |
| `SlotScanner.java` | przegląd slotów PKCS#11 bez PIN (etykiety, flagi PIN, publiczne certyfikaty) |
| `CardSigner.java` | wybór klucza po numerze seryjnym certyfikatu, podpis PAdES-BASELINE-B/T |
| `DocxToPdf.java`, `DocxPlaceholders.java` | konwersja DOCX→PDF (LibreOffice headless) i wypełnianie pól `$ezdPracownik*`/`$ezdDataPodpisu` |
| `PinDialog.java`, `Log.java` | okno PIN (Swing, zawsze na wierzchu), log w `~/Library/Logs/EZD RP Podpis/` |
| `install.sh` | instalator dla użytkowników (Szafir + LibreOffice + aplikacja z GitHub Releases; `--sprawdz`, `--odinstaluj`, `--bez-libreoffice`) |
| `setup-tools.sh`, `build-app.sh` | narzędzia i kompilacja (patrz wyżej) |
| `run-dryrun.sh` | uruchomienie z linii poleceń w trybie próbnym (bez pakowania) |
| `spike/` | sondy użyte przy analizie: API, PKCS#11, konwersja, walidacja podpisu |
| `PROTOKOL-NASK-DESK.md` | odtworzony protokół NASK Desk ↔ EZD RP (interoperacyjność, art. 75 ust. 2 pkt 3 pr. aut.; NASK wyraził zgodę) |

Celowo **poza** repozytorium (`.gitignore`): zdekompilowane źródła NASK Desk i frontend EZD RP (nie nasz kod),
narzędzia (`tools/`), wynik kompilacji, podpisane dokumenty testowe.

## 3. Testowanie bez wysyłki do EZD RP (tryb próbny)

```
touch ~/ezdrp-mac-podpis/DRYRUN          # od teraz aplikacja NIE wysyła podpisanych plików do EZD RP,
                                          # zapisuje je w ~/ezdrp-mac-podpis/spike/signed-<nazwa>.pdf
rm    ~/ezdrp-mac-podpis/DRYRUN          # powrót do trybu produkcyjnego (sprawdza to install.sh --sprawdz)
```

Pełny test z przeglądarki: utwórz DRYRUN, kliknij „Podpisz” w EZD RP, wpisz PIN, sprawdź `spike/signed-*.pdf`
i log. Dokument w EZD RP pozostaje niepodpisany (nic nie wysłano), można go podpisać ponownie.

Test z linii poleceń (PIN z pliku `~/ezdrp-mac-podpis/.pin`, usuń go po teście):
```
~/ezdrp-mac-podpis/run-dryrun.sh "ezdrp://<token>/<base64>/5"      # link skopiuj z konsoli Chrome (F12) po kliknięciu „Podpisz”
```

Walidacja podpisanego PDF (DSS SignedDocumentValidator; wymaga `dss-policy-jaxb`):
```
cd ~/ezdrp-mac-podpis/spike/validator && JAVA_HOME=../../tools/jdk/Contents/Home ../../tools/maven/bin/mvn -q dependency:copy-dependencies -DoutputDirectory=lib
cd .. && ../tools/jdk/Contents/Home/bin/javac -cp "validator/lib/*" ValidateProbe.java \
  && ../tools/jdk/Contents/Home/bin/java -cp ".:validator/lib/*" ValidateProbe "signed-<plik>.pdf"
```
Oczekiwane: `format=PAdES-BASELINE-B intact=true valid=true`; `INDETERMINATE / NO_CERTIFICATE_CHAIN_FOUND` bierze
się z braku listy zaufania w walidatorze i dotyczy jednakowo podpisów z NASK Desk.

Zmienne i właściwości: `EZDRP_PKCS11` / `-Dezdrp.pkcs11` (ścieżka biblioteki PKCS#11), `EZDRP_CONVERTER=word`
(Microsoft Word zamiast LibreOffice; wolniejsze, wymaga zgody na automatyzację), `EZDRP_FILL_PLACEHOLDERS=0`
(bez wypełniania pól `$ezd*`), `EZDRP_DEBUG=1` / `-Dezdrp.debug=true`.

## 4. Wydanie nowej wersji

```
cd ~/ezdrp-mac-podpis
VERSION=1.0.1 ./build-app.sh --zip                      # buduje, instaluje lokalnie, tworzy dist/*.zip + .sha256
git add -A && git commit -m "Wersja 1.0.1: …" && git push
tools/gh/bin/gh release create v1.0.1 dist/EZD-RP-Podpis-1.0.1-macOS.zip dist/EZD-RP-Podpis-1.0.1-macOS.zip.sha256 \
  --title "EZD RP Podpis 1.0.1" --notes "co się zmieniło"
```
`install.sh` u użytkowników pobiera zawsze najnowsze wydanie (`releases/latest`) i weryfikuje sumę SHA-256.
GitHub CLI (`gh`): https://cli.github.com (tu: binarka w `tools/gh/`, logowanie `gh auth login`).

Zmiana wersji w `pom.xml` nie jest wymagana (wersję pakietu nadaje `VERSION` w `build-app.sh`; jpackage wymaga
formatu `X.Y.Z` z pierwszą liczbą ≥ 1).

## 5. Praca spoza sieci urzędu (EZD RP SaaS dopuszcza tylko adresy IP instytucji)

Sprawdzony wariant z tunelem TCP w AnyDesk do komputera stojącego w sieci urzędu:

1. AnyDesk, sesja do komputera w urzędzie → menu „Actions” → „TCP tunneling” → reguła Forward:
   **Local port 443 → Remote host `ezdrp-api.saas1.ezdrp.gov.pl` → Remote port 443**. Reguła zapisuje się
   w `~/.anydesk/user.conf` (`ad.session.tcp_tunneling.fwd.local.<ID>=443@ezdrp-api.saas1.ezdrp.gov.pl:443,`)
   i uruchamia się przy (ponownym) połączeniu sesji. Tunel nasłuchuje tylko na 127.0.0.1.
2. `/etc/hosts` – jeden wiersz dla wszystkich hostów instancji (wszystkie wskazują publicznie ten sam adres):
   ```
   sudo sh -c 'printf "127.0.0.1 ezdrp-api.saas1.ezdrp.gov.pl ezdrp-web.saas1.ezdrp.gov.pl sso-idp.saas1.ezdrp.gov.pl filerepo-api.saas1.ezdrp.gov.pl connectors.saas1.ezdrp.gov.pl mail.saas1.ezdrp.gov.pl ezdrp-forms.saas1.ezdrp.gov.pl zitadel.saas1.ezdrp.gov.pl muas-web.saas1.ezdrp.gov.pl anonimizator-new.saas1.ezdrp.gov.pl\n" >> /etc/hosts; dscacheutil -flushcache; killall -HUP mDNSResponder'
   ```
   W sieci urzędu ten wiersz trzeba zakomentować (`#` na początku) i ponownie wyczyścić cache DNS.
3. Chrome blokuje żądania strony do adresu 127.0.0.1 („Local Network Access”): dla witryny
   `ezdrp-web.saas1.ezdrp.gov.pl` → Ustawienia witryny → „Dostęp do sieci lokalnej” → Zezwalaj
   (albo `chrome://flags/#local-network-access-check` → Disabled).
Weryfikacja: `curl -sS -o /dev/null -w '%{http_code}\n' https://ezdrp-web.saas1.ezdrp.gov.pl/` → `200`.

## 6. Znane zachowania serwera i karty

- `filemonitor-save-file-stream` (multipart) zwraca na saas1 HTTP 500 – aplikacja zapisuje przez
  `filemonitor-save-file` (JSON, plik w base64), tak jak NASK Desk w domyślnej konfiguracji
  (ustawienie `StreamUploadEzdRp` jest w nim domyślnie wyłączone). Strumień pozostaje jako zapas.
- Kontrasygnata przy PAdES = zwykły kolejny podpis w PDF (tak robi NASK Desk); wcześniejsze podpisy zostają nienaruszone.
- Karta KIR Graphite ma dwa sloty: „PKI Token 1 (Primary)” i „PKI Token 2 (QSCD)” (certyfikat kwalifikowany).
  Certyfikaty są widoczne bez PIN, więc aplikacja wysyła PIN wyłącznie do slotu z certyfikatem o numerze seryjnym
  z EZD RP (`numerSeryjnyCertyfikatu`). Wymaga to `--add-exports=jdk.crypto.cryptoki/sun.security.pkcs11.wrapper=ALL-UNNAMED`
  (ustawione w `build-app.sh` i `run-dryrun.sh`).
- Sterowniki: `libPKCS11Graphite.dylib` (uniwersalny, działa), `libccpkip11.dylib` (tylko x86_64 – nie działa pod arm64).
- NASK Desk nie wypełnia pól `$ezdPracownikNazwa` itp. w DOCX; aplikacja wypełnia je danymi sygnatariusza
  z EZD RP (`EZDRP_FILL_PLACEHOLDERS=0` wyłącza).

## 7. Dystrybucja na inne Maki

Pakiet jest podpisany ad hoc (bez konta Apple Developer), więc Gatekeeper blokuje go po pobraniu z internetu –
`install.sh` zdejmuje flagę kwarantanny (`xattr -dr com.apple.quarantine`). Dla dystrybucji w instytucji bez tego
kroku potrzebny jest certyfikat Developer ID i notaryzacja (`codesign --sign "Developer ID Application: …"`,
`xcrun notarytool submit` – wymaga Xcode CLT) albo wdrożenie przez MDM.

## 8. Licencje

Kod projektu: © 2026 Mateusz Trybowski, wszelkie prawa zastrzeżone (`LICENSE`). Komponenty użyte w pakiecie aplikacji:

| Komponent | Licencja | Uwagi |
|---|---|---|
| EU DSS (Digital Signature Service) 6.3 | LGPL-2.1 | biblioteki `.jar` ładowane bez modyfikacji |
| Apache PDFBox, Apache Commons, HttpClient | Apache-2.0 | |
| Bouncy Castle | MIT | |
| Eclipse Temurin JDK 21 (wbudowane środowisko) | GPL-2.0 + Classpath Exception | |
| SLF4J | MIT | |

Instalowane osobno przez `install.sh`: Szafir 2.0 (KIR, licencja właścicielska – pobierany ze strony KIR
przez użytkownika), LibreOffice (MPL-2.0). Protokół NASK Desk odtworzono w celu interoperacyjności
(art. 75 ust. 2 pkt 3 ustawy o prawie autorskim), za zgodą NASK; zdekompilowany kod nie jest częścią repozytorium.
