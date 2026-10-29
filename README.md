# EZD RP Podpis dla macOS

Podpisywanie dokumentów w **EZD RP** podpisem kwalifikowanym (karta KIR / Szafir) **na Macu, jednym kliknięciem
w przeglądarce**. Po kliknięciu „Podpisz” w EZD RP otwiera się okienko na PIN do karty, dokument jest podpisywany
(PAdES) i wraca do EZD RP. Program zastępuje na macOS aplikację NASK Desk, którą NASK udostępnia tylko dla Windows.

---

## 1. Czego potrzebujesz

| Co | Jak sprawdzić / skąd wziąć |
|---|---|
| Mac z procesorem **Apple Silicon** (M1, M2, M3, M4…) i **macOS 13** lub nowszym | Menu Apple () → „O tym Macu”. W wierszu „Chip” ma być „Apple M…”. Macy z procesorem Intel nie są obsługiwane. |
| **Karta kwalifikowana KIR (Szafir)** z ważnym certyfikatem i **czytnik USB** | Taki sam zestaw jak do podpisywania na Windows (np. czytnik ACS ACR39U). |
| **Google Chrome** | https://www.google.com/chrome/ (testowany; linki działają też w Safari i Firefoksie) |
| **Konto w EZD RP** z profilem podpisu „Podpis domyślny (PAdES)” | W EZD RP: Ustawienia użytkownika → Podpisy. Wybrany certyfikat musi być tym z Twojej karty. |
| **Hasło administratora Maca** (to, którym logujesz się do komputera) | Potrzebne raz, przy instalacji sterownika karty. |
| Internet | Pobierane jest ok. 500 MB. |

---

## 2. Instalacja na nowym Macu (ok. 10 minut)

Instalator sam pobiera i instaluje wszystkie trzy elementy: sterownik karty **Szafir 2.0** ze strony KIR,
**LibreOffice** (zamienia dokumenty Word na PDF przed podpisem) i aplikację **EZD RP Podpis**.

**Krok 1.** Podłącz czytnik z kartą do Maca.

**Krok 2.** Otwórz Terminal: naciśnij jednocześnie **⌘ cmd + spacja**, wpisz `Terminal` i naciśnij **Enter**.

**Krok 3.** Skopiuj poniższą linię (cały wiersz), wklej ją do Terminala (**⌘ cmd + V**) i naciśnij **Enter**:

```
curl -fsSL https://raw.githubusercontent.com/tryb103/ezdrp-mac-podpis/main/install.sh | zsh
```

**Krok 4.** Obserwuj komunikaty:

- Przy instalacji Szafira Terminal poprosi: `Password:` – wpisz **hasło administratora Maca** i naciśnij Enter.
  Podczas wpisywania **nic się nie wyświetla** (żadnych kropek) – to normalne.
- Pobieranie LibreOffice (ok. 300 MB) może potrwać kilka minut. Nie zamykaj Terminala.
- Na końcu zobaczysz listę z `[OK]` i zdanie **„Wszystko gotowe”**.

Jeśli zamiast tego pojawi się czerwony `BŁĄD:` – przeczytaj komunikat, zwykle mówi, co zrobić, i uruchom komendę
z kroku 3 ponownie. Instalator można uruchamiać wielokrotnie, pomija to, co już jest zainstalowane.

**Warianty:**

```
# bez LibreOffice (będzie można podpisywać tylko pliki PDF, nie Word):
curl -fsSL https://raw.githubusercontent.com/tryb103/ezdrp-mac-podpis/main/install.sh | zsh -s -- --bez-libreoffice
```

---

## 3. Pierwsze podpisanie

1. W Chrome zaloguj się do EZD RP i otwórz dokument do podpisu.
2. Kliknij **„Podpisz”** (typ podpisu: *Podpis domyślny*).
3. Chrome zapyta **„Otworzyć EZD RP Podpis?”**. Zaznacz **„Zawsze zezwalaj witrynie … na otwieranie linków tego
   typu”** i kliknij **„Otwórz EZD RP Podpis”**. To pytanie pojawia się tylko raz.
4. Pojawi się okienko **„EZD RP Podpis – Podaj PIN do karty”**. Wpisz PIN do karty (ten sam, co w Szafirze
   na Windows) i naciśnij Enter.
5. Po kilku sekundach pojawi się komunikat **„Podpisano 1 plik(ów)”**. Wróć do Chrome – EZD RP pokaże dokument jako
   podpisany (w razie potrzeby odśwież stronę).

Podpis składany jest wyłącznie na Twoim Macu, na karcie. PIN nie jest nigdzie zapisywany. Dokument jest pobierany
z EZD RP, podpisywany i odsyłany tym samym szyfrowanym połączeniem, którego używa EZD RP.

---

## 4. Sprawdzenie, aktualizacja, odinstalowanie

Wszystkie komendy wpisuje się w Terminalu (⌘ cmd + spacja → `Terminal`).

```
# Sprawdzenie, czy wszystko jest na miejscu (nic nie zmienia):
curl -fsSL https://raw.githubusercontent.com/tryb103/ezdrp-mac-podpis/main/install.sh | zsh -s -- --sprawdz

# Aktualizacja do najnowszej wersji (ta sama komenda, co instalacja):
curl -fsSL https://raw.githubusercontent.com/tryb103/ezdrp-mac-podpis/main/install.sh | zsh

# Odinstalowanie aplikacji EZD RP Podpis (Szafir i LibreOffice zostają):
curl -fsSL https://raw.githubusercontent.com/tryb103/ezdrp-mac-podpis/main/install.sh | zsh -s -- --odinstaluj

# Otwarcie folderu z dziennikiem działania (przydatne przy zgłaszaniu problemu):
open ~/Library/Logs/"EZD RP Podpis"
```

---

## 5. Gdy coś nie działa

| Objaw | Co zrobić |
|---|---|
| Po kliknięciu „Podpisz” nic się nie dzieje; w konsoli Chrome: `scheme does not have a registered handler` | Aplikacja nie jest zarejestrowana. Uruchom ponownie komendę instalacyjną z punktu 2 (krok 3). |
| Chrome nie pyta o otwarcie aplikacji | Kliknij ikonę po lewej stronie adresu w Chrome → „Ustawienia witryny” → „Programy obsługi protokołów” → Zezwalaj. Potem kliknij „Podpisz” jeszcze raz. |
| macOS: „Nie można otworzyć aplikacji EZD RP Podpis” / „program jest uszkodzony” | Uruchom ponownie komendę instalacyjną (zdejmuje blokadę systemu). Jeśli to nie pomoże: Ustawienia systemowe → Prywatność i ochrona → na dole „Otwórz mimo to”. |
| Okno błędu: „Nie znaleziono na karcie certyfikatu o numerze seryjnym …” | Profil podpisu w EZD RP wskazuje inny certyfikat niż ten na włożonej karcie. W EZD RP: Ustawienia użytkownika → Podpisy → wybierz certyfikat z karty. Sprawdź też, czy włożona jest właściwa karta. |
| Okno błędu: „Nieprawidłowy PIN” | Wpisz PIN jeszcze raz. Uwaga: trzy błędne próby blokują kartę. |
| Okno błędu: „PIN karty jest zablokowany” | Odblokuj PIN w aplikacji Szafir 2.0 (`/Applications/Szafir2.app`) kodem PUK od KIR. |
| Okno błędu: „EZD RP żąda podpisu XAdES…” | Ta wersja podpisuje tylko PAdES. W EZD RP ustaw typ podpisu „Podpis domyślny (PAdES)”. |
| Okno błędu: „Podpis PAdES wymaga PDF. Otrzymano format …” | Podpisywane mogą być pliki PDF, DOCX i DOC. Inne formaty nie są obsługiwane. |
| Okno błędu: „…nie znaleziono LibreOffice…” | Dokument jest w formacie Word, a LibreOffice nie jest zainstalowany. Uruchom instalator bez `--bez-libreoffice`. |
| Okno błędu: „HTTP connect timed out” / „Błąd w odpowiedzi z serwera” | Brak połączenia z EZD RP. Sprawdź, czy EZD RP otwiera się w Chrome. EZD RP bywa dostępne tylko z sieci urzędu (w domu – przez VPN lub tunel, patrz `DLA-PROGRAMISTOW.md`). |
| Nie wiadomo, co się stało | Otwórz dziennik: `open ~/Library/Logs/"EZD RP Podpis"` i przeczytaj ostatnie wiersze pliku `ezdrp-podpis.log`. |

---

## 6. Instalacja ręczna (bez skryptu)

Jeśli nie chcesz uruchamiać skryptu, zrób to samo ręcznie:

1. **Szafir 2.0 dla macOS** – pobierz ze strony KIR
   https://www.elektronicznypodpis.pl/en/applications-and-drivers (pozycja „Szafir (macOS)”, pakiet `Szafir2-*.pkg`),
   otwórz pobrany plik dwuklikiem i przejdź przez instalator (poprosi o hasło administratora).
2. **LibreOffice** (opcjonalnie, do dokumentów Word) – https://www.libreoffice.org/download/ , wersja dla
   **macOS (Apple Silicon)**. Otwórz pobrany `.dmg` i przeciągnij LibreOffice do folderu Programy (Applications).
3. **EZD RP Podpis** – pobierz plik `EZD-RP-Podpis-<wersja>-macOS.zip` z https://github.com/tryb103/ezdrp-mac-podpis/releases ,
   a potem w Terminalu:

```
cd ~/Downloads
ditto -x -k EZD-RP-Podpis-*-macOS.zip ~/Applications
xattr -dr com.apple.quarantine ~/Applications/"EZD RP Podpis.app"
/System/Library/Frameworks/CoreServices.framework/Frameworks/LaunchServices.framework/Support/lsregister -f ~/Applications/"EZD RP Podpis.app"
```

---

## 7. Ograniczenia tej wersji

- Tylko podpis **PAdES** (podpis domyślny EZD RP) plików **PDF, DOCX, DOC**. Dokumenty Word są przed podpisem
  zamieniane na PDF przez LibreOffice (NASK Desk robi to samo, innym programem; drobne różnice w układzie strony są możliwe).
- Brak: podpisu XAdES, znacznika czasu (EZD RP w standardowej konfiguracji go nie wymaga), widocznego pola podpisu
  w dokumencie, innych operacji NASK Desk (wydruki, edycja plików).
- Tylko Macy z Apple Silicon. Aplikacja jest podpisana lokalnie (bez certyfikatu Apple Developer), dlatego instalator
  zdejmuje z niej flagę kwarantanny; przy ręcznej instalacji trzeba to zrobić komendą z punktu 6.

Dla administratorów i programistów (kompilacja ze źródeł, tryb testowy, praca zdalna przez tunel, opis protokołu):
**`DLA-PROGRAMISTOW.md`** oraz **`PROTOKOL-NASK-DESK.md`**.
