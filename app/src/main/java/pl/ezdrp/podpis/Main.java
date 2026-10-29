package pl.ezdrp.podpis;

import eu.europa.esig.dss.token.PasswordInputCallback;

import javax.swing.*;
import java.awt.*;
import java.awt.desktop.OpenURIHandler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * EZD RP Podpis – obsługa linku ezdrp:// na macOS.
 * Uruchomienie: z argumentem (link) albo przez zdarzenie OpenURI z macOS (CFBundleURLTypes: ezdrp).
 */
public class Main {

    static final List<String> PKCS11_CANDIDATES = List.of(
            "/Applications/Szafir2.app/Contents/app/libPKCS11Graphite.dylib",
            "/Applications/Szafir2.app/Contents/app/libccpkip11.dylib",
            "/usr/local/lib/libSimplySignPKCS.dylib",
            "/usr/local/lib/libcryptoCertum3PKCS.dylib",
            "/Applications/Charismathics/libcmP11.dylib",
            "/usr/local/lib/libencardp11.dylib");

    private static final LinkedBlockingQueue<String> LINKS = new LinkedBlockingQueue<>();
    private static Log log;

    public static void main(String[] args) throws Exception {
        System.setProperty("apple.awt.UIElement", "true"); // bez ikony w Docku
        log = new Log(Boolean.getBoolean("ezdrp.debug") || System.getenv("EZDRP_DEBUG") != null);
        log.info("Start, args=%s", List.of(args));

        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.APP_OPEN_URI)) {
            Desktop.getDesktop().setOpenURIHandler((OpenURIHandler) e -> {
                log.info("OpenURI: %s", e.getURI());
                LINKS.offer(e.getURI().toString());
            });
        }
        for (String a : args) if (a.toLowerCase().startsWith("ezdrp://")) LINKS.offer(a);

        // Czekamy na link (z argumentów lub ze zdarzenia macOS); po bezczynności kończymy.
        String link = LINKS.poll(15, TimeUnit.SECONDS);
        if (link == null) {
            log.info("Brak linku ezdrp:// – koniec.");
            System.exit(0);
        }
        int code = 0;
        do {
            try {
                handle(link);
            } catch (Throwable t) {
                code = 1;
                log.error("Operacja nieudana", t);
                PinDialog.error("EZD RP Podpis – błąd", CardSigner.rootMessage(t) + "\n\nSzczegóły w logu:\n" + log.file());
            }
            link = LINKS.poll(3, TimeUnit.SECONDS);
        } while (link != null);
        System.exit(code);
    }

    static void handle(String raw) throws Exception {
        EzdrpLink link = EzdrpLink.parse(raw);
        log.info("Operacja %s (%d), API %s, proxy %s, token %s", link.operationName(), link.operation(), link.apiUrl(), link.proxyUrl(), link.token());
        if (link.operation() != EzdrpLink.OP_PODPISYWANIE_PLIKOW) {
            throw new UnsupportedOperationException("Ta wersja obsługuje tylko podpisywanie plików. EZD RP poprosił o: " + link.operationName());
        }
        EzdRpClient api = new EzdRpClient(link.apiUrl(), link.proxyUrl(), log);
        EzdRpClient.DaneHurtowePodpisu dane = api.pobierzDaneHurtowePodpisu(link.token());
        log.info("Dane hurtowe: %d plik(ów), serial=%s, TSA=%s", dane.tokeny().size(), dane.numerSeryjnyCertyfikatu(), dane.urlSerweraTSA());
        if (dane.tokeny().isEmpty()) throw new IllegalStateException("EZD RP nie przekazał żadnych plików do podpisu.");

        String lib = pkcs11Library();
        final char[][] pinHolder = new char[1][];
        PasswordInputCallback pinCb = () -> {
            if (pinHolder[0] == null) {
                // Tryb testowy bez okna: PIN z pliku ~/ezdrp-mac-podpis/.pin (tylko gdy istnieje DRYRUN lub ezdrp.pinfile)
                Path pinFile = Path.of(System.getProperty("user.home"), "ezdrp-mac-podpis", ".pin");
                if ((dryRun() || Boolean.getBoolean("ezdrp.pinfile")) && Files.isRegularFile(pinFile)) {
                    try {
                        pinHolder[0] = Files.readString(pinFile).trim().toCharArray();
                        log.info("PIN odczytany z pliku (tryb testowy)");
                        return pinHolder[0];
                    } catch (Exception e) { log.error("Nie można odczytać pliku PIN: " + e.getMessage()); }
                }
                pinHolder[0] = PinDialog.ask("EZD RP Podpis", "Podaj PIN do karty",
                        "Podpis " + dane.tokeny().size() + " plik(ów) w EZD RP");
                if (pinHolder[0] == null) throw new RuntimeException("Anulowano wprowadzanie PIN.");
            }
            return pinHolder[0];
        };

        try (CardSigner signer = new CardSigner(lib, pinCb, log)) {
            CardSigner.KeyChoice key = signer.findKey(dane.numerSeryjnyCertyfikatu(), 4);
            log.info("Wybrany certyfikat: %s", key.key().getCertificate().getSubject().getRFC2253());
            int ok = 0;
            for (String t : dane.tokeny()) {
                EzdRpClient.PobranyPlik plik = api.pobierzPlikStrumieniem(t);
                String type = plik.podpis().type();
                if (!type.equalsIgnoreCase("PAdES")) {
                    throw new UnsupportedOperationException("EZD RP żąda podpisu " + type + " dla pliku " + plik.name()
                            + ". Ta wersja obsługuje tylko PAdES (podpis domyślny). Zmień profil podpisu w EZD RP.");
                }
                byte[] pdf = plik.content();
                String pdfName = plik.name();
                String ext = plik.extension().toLowerCase();
                if (!ext.equals("pdf")) {
                    if (!ext.equals("docx") && !ext.equals("doc")) {
                        throw new UnsupportedOperationException("Podpis PAdES wymaga PDF. Otrzymano format: " + plik.name());
                    }
                    byte[] docx = plik.content();
                    if (!"0".equals(System.getenv().getOrDefault("EZDRP_FILL_PLACEHOLDERS", "1"))) {
                        int[] n = new int[1];
                        String now = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                        docx = DocxPlaceholders.fill(docx, DocxPlaceholders.forSigner(
                                dane.sygnatariuszImieINazwisko(), dane.sygnatariuszStanowisko(), now), n);
                        if (n[0] > 0) log.info("Wypełniono %d placeholder(ów) sygnatariusza w %s", n[0], plik.name());
                    }
                    log.info("Konwersja %s do PDF przed podpisem PAdES", plik.name());
                    pdf = new DocxToPdf(log).convert(docx, plik.name());
                    pdfName = DocxToPdf.stripExt(plik.name()) + ".pdf";
                }
                String tsa = !plik.podpis().urlTSA().isBlank() ? plik.podpis().urlTSA() : dane.urlSerweraTSA();
                byte[] signed = signer.signPades(pdf, pdfName, key, plik.podpis().znacznikCzasu(), tsa);
                if (dryRun()) {
                    Path out = Path.of(System.getProperty("user.home"), "ezdrp-mac-podpis", "spike", "signed-" + pdfName);
                    Files.write(out, signed);
                    log.info("DRY RUN: podpisany plik zapisany lokalnie: %s (nie wysłano do EZD RP)", out);
                } else {
                    try {
                        api.zapiszPlik(t, signed);
                    } catch (RuntimeException e) {
                        log.info("Zapis base64 nieudany (%s) – próba zapisu strumieniem", e.getMessage());
                        api.zapiszPlikStrumieniem(t, signed);
                    }
                }
                ok++;
                log.info("Podpisano %s (%d B -> %d B)", plik.name(), plik.content().length, signed.length);
            }
            PinDialog.info("EZD RP Podpis", "Podpisano " + ok + " plik(ów). Wróć do przeglądarki, EZD RP odświeży status.");
        } finally {
            if (pinHolder[0] != null) java.util.Arrays.fill(pinHolder[0], '\0');
        }
    }

    /** Tryb próbny: plik ~/ezdrp-mac-podpis/DRYRUN istnieje → podpisany PDF zapisywany lokalnie, nic nie trafia do EZD RP. */
    static boolean dryRun() {
        return Boolean.getBoolean("ezdrp.dryrun")
                || Files.exists(Path.of(System.getProperty("user.home"), "ezdrp-mac-podpis", "DRYRUN"));
    }

    static String pkcs11Library() {
        String cfg = System.getProperty("ezdrp.pkcs11", System.getenv("EZDRP_PKCS11"));
        if (cfg != null && Files.isRegularFile(Path.of(cfg))) return cfg;
        for (String c : PKCS11_CANDIDATES) if (Files.isRegularFile(Path.of(c))) return c;
        throw new IllegalStateException("Nie znaleziono biblioteki PKCS#11 (sterownika karty). Zainstaluj Szafir 2.0 z KIR albo ustaw EZDRP_PKCS11.");
    }
}
