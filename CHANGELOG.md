# Historia zmian

## 1.0.0 – 2026-10-29

Pierwsza wersja produkcyjna (działa od 2026-10-21).

- Obsługa linku `ezdrp://…/5` (podpisywanie plików) z EZD RP w przeglądarce na macOS (Apple Silicon).
- Podpis PAdES-BASELINE-B (SHA-256) na karcie KIR przez PKCS#11 (Szafir 2.0, `libPKCS11Graphite.dylib`);
  PAdES-BASELINE-T, gdy EZD RP wskaże serwer TSA.
- Wybór slotu karty bez PIN (certyfikat o numerze seryjnym z profilu EZD RP); PIN trafia tylko do właściwego slotu.
- Pliki PDF, DOCX i DOC (DOCX/DOC konwertowane do PDF przez LibreOffice; wypełnianie pól `$ezdPracownik*`,
  `$ezdDataPodpisu`).
- Zapis podpisanego pliku przez `filemonitor-save-file` (base64) – domyślna ścieżka NASK Desk; wariant
  strumieniowy jako zapas.
- Instalator `install.sh` (Szafir 2.0 ze strony KIR z weryfikacją podpisu pakietu, LibreOffice z weryfikacją
  SHA-256, aplikacja z GitHub Releases z weryfikacją SHA-256), skrypty `setup-tools.sh` i `build-app.sh`.
- Dokumentacja: `README.md` (użytkownik), `DLA-PROGRAMISTOW.md`, `PROTOKOL-NASK-DESK.md`.
