# Protokół NASK Desk 2.0.2.71 ↔ EZD RP (podpis plików)

Źródło: dekompilacja `naskdesk.zip` (MSI `NaskDesk2.0.2.71.msi`, SHA256 `8524f7de…53662c`)
pobranego 2026-10-21 z https://pliki.ezdrp.eadministracja.nask.pl/naskdesk.zip,
plus bundle frontu `ezdrp-web.saas1.ezdrp.gov.pl/main.ed72ecb517220185.js`.
Dekompilowane źródła: `naskdesk-2.0.2.71/src-decompiled/`.

## 1. Stos technologiczny NASK Desk 2.0

- .NET 10 self-contained (win-x64), **Avalonia UI** (wieloplatformowy), ReactiveUI, Autofac, Serilog.
- Podpis: `NASK.ElectronicSignatures` + `NASK.ElectronicSignatures.SecureBlackbox`
  (nsoftware SecureBlackbox 24 – komercyjna, wymaga licencji; stara ścieżka: SecureBlackbox.NetCore 16).
- Konwersja DOCX→PDF przed PAdES: Aspose.Words/Aspose.PDF (komercyjne).
- Kod zawiera gotowe gałęzie macOS: `OperatingSystem.IsMacOS()`, `MacKeychainSelector`,
  `PathToMacOsDrivers` (dylib PKCS#11), `Pkcs11LibraryFinder` (szuka w /usr/local/lib, /opt/homebrew/lib …),
  obsługa SIGTERM/SIGINT, `MacOsForegroundHelper`. Wersja macOS jest w kodzie, brak tylko builda.
- Windows-only: Microsoft.Office.Interop.Word (edycja), drukowanie etykiet (winspool), EventLog, WindowsStoreSelector.

## 2. Link uruchamiający

Front EZD RP woła `POST {API}/wygeneruj-link-do-podpisow` (plik `main.*.js`, klucz `getSignLink`),
backend zwraca `link`, front robi `window.location.href = link`.

```
ezdrp://{TOKEN}/{BASE64}/{TYP_OPERACJI}
```

- `TOKEN` – GUID bez myślników, token sesji operacji (jednorazowy, wydany przez backend).
- `BASE64` – base64(`"{urlApi}"` lub `"{urlApi}*{urlProxy}"`); rozdzielacz `*`.
  Parsowanie: `FileMonitorProxy.OdtworzUrl`, `EzdRpSessionFactory.Build`.
- `TYP_OPERACJI` – enum `EZDRP.Desktop.Common.TypOperacji`:

| kod | operacja |
|----|----|
| 1 | OdczytPliku |
| 2 | EdycjaPliku |
| 3 | Wydruk |
| 4 | PorownaniePlikow |
| **5** | **PodpisywaniePlikow** |
| 6 | ZapisywanieCertyfikatow (no-op w 2.0) |
| 7, 71, 72 | WydrukEtykietKK / SCD / SCK |
| 31 | WydrukEtykietRPW |
| 81–88 | wydruki RKW (etykieta, koperta, zwrotka, wkładka, ZPO, numer R…) |
| 91 | WydrukWEW |

Przykład (token fikcyjny): `ezdrp://0123456789abcdef0123456789abcdef/aHR0cHM6Ly9lemRycC1hcGkuc2FhczEuZXpkcnAuZ292LnBs/5`
→ token `84bee5fd…`, API `https://ezdrp-api.saas1.ezdrp.gov.pl`, operacja 5 = podpis.

## 3. Wspólne elementy każdego żądania do „ICE API” (FileMonitor API)

Klient: `IceApiIntegrationHttpClient.GetResponse`.

- Metoda `POST`, URL = `{urlApi}/{endpoint}`, body JSON (`PropertyNameCaseInsensitive`).
- Body dziedziczy po `RequestBase`: `{ "Token": "<TOKEN>", "Patch": 175, ... }`
  (`Patch` = ustawienie `File.Monitor.Patch`, domyślnie **175**).
- Nagłówki (`EnritchRequestWithRxCx`):
  - `RX` – licznik wywołań dla danego tokenu (1, 2, 3…) w ramach procesu,
  - `CX` – SHA-1 z UTF-8 tokenu, zapisany jako **konkatenacja bajtów w zapisie dziesiętnym** (`b.ToString()` dla każdego bajtu, bez separatorów),
  - `x-client-version` – wersja aplikacji (np. `2.0.2.71`).
- Timeout 3 min (`HttpRequestTimeoutEzdRp`).
- Odpowiedzi błędów: `application/problem+json` (`title`, `detail`, `status`, `instance`, `errorId`).
- Nagłówki odpowiedzi sterujące białą listą TLS: `X-ND-Pid` (id podmiotu), `X-ND-Date`, `X-ND-WhL` (czy biała lista);
  gdy obecne → `POST filemonitor-get-white-list-data` `{IdPodmiot, LastUpdate, Patch}` → lista domen + SHA256/SHA1 certyfikatów;
  pinning włączany ustawieniem `Blokowanie.CertyfikatowSSL`. Opcjonalnie `Blokowanie.LokalnychAdresowIp`.
- Lista zablokowanych rozszerzeń: `filemonitor-get-extensions-data` (klasa `GetExtensionsDataRequest {IdPodmiot, LastUpdate}`), ustawienie `Blokowanie.Rozszerzen`.

## 4. Sekwencja operacji 5 – podpis (`ElectronicSignaturesManager.SignFilesAsync`)

1. `POST {api}/filemonitor-pobierz-dane-hurtowe-podpisu`
   body `{ "Token": TOKEN, "Patch": 175 }`
   → `DaneHurtowePodpisuResponse { Tokeny: string[], NumerSeryjnyCertyfikatu: string, UrlSerweraTSA: string }`
   (`Tokeny` = po jednym tokenie na każdy plik do podpisu; numer seryjny z profilu podpisu użytkownika w EZD RP).
2. Dla każdego `tokenPliku` z `Tokeny`:
   1. `POST {api}/filemonitor-pobierz-plik/true` body `{ "Token": tokenPliku, "Patch": 175 }`
      → odpowiedź **strumieniowa** (binarna treść pliku), `Content-Disposition` z `filename*`,
      nagłówki:
      - `X-Dane-Podpisu-Type` – `PAdES` | `XAdES_Enveloping` | `XAdES_Enveloped` | `XAdES_Detached`
        (enum `FileMonitor.Module.Signatures.SignatureType` = 1..4),
      - `X-Dane-Podpisu-Czy-Kontrasygnata` – `true/false` (kontrasygnata XAdES),
      - `X-Dane-Podpisu-Czy-Znacznik-Czasu` – `true/false`,
      - `X-Dane-Podpisu-Znacznik-Czasu-Url-Serwera-TSA` – URL TSA (fallback: `UrlSerweraTSA` z kroku 1).
      Wariant nie-strumieniowy: `POST filemonitor-pobierz-plik` → JSON `{Name, Extension, Size, Content(base64), Signature{Type, CzyKontrasygnata, ZancznikCzasu{UrlSerweraTSA}}}`.
   2. Przygotowanie (`SignInputPreparer`): PAdES wymaga PDF; `.doc/.docx` konwertowane do PDF
      (Aspose) z przeniesieniem pól podpisu; inne formaty → błąd „Podpis PAdES wymaga PDF”.
   3. Wybór certyfikatu: ustawienie `Podpisy.Protokol` = `PKCS11` lub `Systemowy` (Windows Store / macOS Keychain),
      PKCS#11: ścieżka `Podpisy.SciezkaSterownikaPkcs11` lub autodetekcja
      (macOS: `/usr/local/lib/libSimplySignPKCS.dylib`, `/usr/local/lib/libcryptoCertum3PKCS.dylib`,
      `/Applications/Charismathics/libcmP11.dylib`, `/usr/local/lib/libencardp11.dylib`),
      szukanie po numerze seryjnym (`Serial`), skan slotów, PIN z okna aplikacji (6 cyfr lub pełne, do 120 s).
   4. Podpis (`SignService`): profil wg mapowania
      PAdES→PAdES (widoczny widget jeśli jest pole, inaczej niewidoczny), XAdES_* → Detached/Enveloped/Enveloping,
      kontrasygnata → `XAdESCountersign`. XAdES: SHA-256, C14N inclusive, `DetachedResourceURI` = nazwa pliku.
      Znacznik czasu: `Timestamp.URL` = TSA z nagłówka, SHA-256, nonce, z certyfikatami.
   5. `POST {api}/filemonitor-save-file-stream` – `multipart/form-data`:
      - część `json` (`application/octet-stream`): `{ "Token": tokenPliku, "Patch": 175, "File": null }`,
      - część `file` (`application/octet-stream`): bajty wyniku (PDF z PAdES albo XML z XAdES).
      Wariant nie-strumieniowy: `POST filemonitor-save-file` `{Token, Patch, File: base64}`.
      Odpowiedź: sam status HTTP (200 = OK).
3. Powiadomienia użytkownika lokalnie (toast); EZD RP w przeglądarce odświeża stan sam (SignalR `ezdrp-signalr`).

Obsługa błędów PKCS#11: retry 3× co 750 ms dla błędów przejściowych (brak slotu, enumeracja), przerwanie partii przy
odrzuconym PIN lub zajętym tokenie.

## 5. Inne operacje (do późniejszych etapów)

- Odczyt/edycja pliku (1, 2): `filemonitor-pobierz-plik[/true]` → zapis lokalny → watcher → `filemonitor-save-file[-stream]`.
- Wydruki (3, 7x, 8x, 91): `filemonitor-get-wydruk-dane`, `filemonitor-get-wydruk-korespondencji`,
  `filemonitor-get-wydruk-etykiety-kk` → ZPL/EPL/PDF na drukarkę.
- Lokalne „Inner API” (localhost, port z ustawień): `api/Message/Show`, `api/Message/Enqueue`,
  `api/Configuration/Printer`, `api/PDFPrint/PrintDocument` – komunikacja między procesami NASK Desk, nie z EZD RP.
- Strona `/naskdesk-pin/{token}` i `ezdrp/naskdesk/pobierz-pin` w EZD RP: 6-cyfrowy PIN z czasem ważności –
  w kodzie 2.0.2.71 PIN wpisywany w NASK Desk trafia do `RelayQue.Pin` i jest używany jako **PIN do karty**
  (`PasswordProvider`), nie do parowania z serwerem. Przeznaczenie strony PIN w EZD RP do potwierdzenia.

## 6. Co z tego wynika dla wersji macOS

Minimalny klient „podpis jednym kliknięciem” = ~5 wywołań HTTP (sekcja 3–4) + implementacja podpisu
PAdES/XAdES z PKCS#11 i TSA. Elementy nie do skopiowania 1:1: SecureBlackbox (licencja), Aspose (licencja) –
zamienniki open source: EU DSS (Java) lub iText/PDFBox + xades4j; konwersję DOCX→PDF można zostawić serwerowi
(EZD RP sam konwertuje w wielu konfiguracjach) albo LibreOffice headless.

## 7. Stan końcowy (2026-10-21, 20:21) – podpis z Chrome na macOS działa produkcyjnie

- Aplikacja `~/Applications/EZD RP Podpis.app` (jpackage, Java 21 + EU DSS 6.3, ad-hoc codesign) jest jedynym
  handlerem schematu `ezdrp://` w LaunchServices. Chrome: „Zawsze zezwalaj" dla EZD RP Podpis.
- Zależności runtime: `/Applications/Szafir2.app/Contents/app/libPKCS11Graphite.dylib` (karta KIR, PKCS#11),
  `~/Applications/LibreOffice.app` (tylko dla DOCX→PDF). Folder `~/ezdrp-mac-podpis` nie jest potrzebny w runtime
  (opcjonalne: znacznik `DRYRUN` = tryb próbny bez wysyłki, `.pin` = PIN z pliku w trybie testowym).
- Log: `~/Library/Logs/EZD RP Podpis/ezdrp-podpis.log`.
- Zapis podpisanego pliku: **`filemonitor-save-file` (JSON, `File` base64)** – domyślna ścieżka NASK Desk
  (ustawienie `StreamUploadEzdRp` jest domyślnie wyłączone). Wariant strumieniowy `filemonitor-save-file-stream`
  zwraca na saas1 HTTP 500 (errorId ba8d998a…) – używany tylko jako zapas.
- Wybór slotu karty: przegląd bez PIN (sun.security.pkcs11.wrapper: C_GetTokenInfo + publiczne CKO_CERTIFICATE),
  PIN tylko do slotu z certyfikatem o numerze seryjnym z EZD RP. Karta KIR Graphite: slot 0 „PKI Token 1 (Primary)"
  (login zwraca CKR_PIN_LOCKED), slot 1 „PKI Token 2 (QSCD)" = certyfikat kwalifikowany, widoczny bez PIN.
  Wymaga `--add-exports=jdk.crypto.cryptoki/sun.security.pkcs11.wrapper=ALL-UNNAMED` (jest w build-app.sh).
- Zweryfikowane: PAdES-BASELINE-B, SHA-256/RSA – ten sam format co podpisy NASK Desk na Windows; kontrasygnata PAdES =
  kolejny podpis w PDF, wcześniejsze podpisy nienaruszone (DSS SignedDocumentValidator, `spike/ValidateProbe`,
  classpath `spike/validator/lib/*`).
- Praca spoza sieci firmowej: AnyDesk TCP tunnel (forward: local 443 → ezdrp-api.saas1.ezdrp.gov.pl:443, reguła w
  `~/.anydesk/user.conf` jako `ad.session.tcp_tunneling.fwd.local.<ID>=443@host:443,`; aktywuje się przy ponownym
  połączeniu sesji) + wpis w `/etc/hosts` 127.0.0.1 dla wszystkich hostów `*.saas1.ezdrp.gov.pl` (api, web, sso-idp,
  filerepo-api, connectors, mail, ezdrp-forms, zitadel, muas-web, anonimizator-new; wszystkie = 193.59.180.36)
  + w Chrome zezwolenie „Dostęp do sieci lokalnej" dla ezdrp-web (Local Network Access blokuje XHR do 127.0.0.1).
  Wpis w hosts jest obecnie zakomentowany (praca w sieci firmowej).
