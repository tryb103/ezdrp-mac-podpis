package pl.ezdrp.podpis;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Konwersja DOC/DOCX → PDF (odpowiednik Aspose w NASK Desk).
 * 1) Microsoft Word przez AppleScript (najwierniejszy układ), 2) LibreOffice headless.
 */
public class DocxToPdf {

    private static final List<String> WORD_APPS = List.of("/Applications/Microsoft Word.app");
    private static final List<String> SOFFICE = List.of(
            "/Applications/LibreOffice.app/Contents/MacOS/soffice",
            System.getProperty("user.home") + "/Applications/LibreOffice.app/Contents/MacOS/soffice");

    private final Log log;

    public DocxToPdf(Log log) { this.log = log; }

    public byte[] convert(byte[] docx, String fileName) throws Exception {
        Path dir = Files.createTempDirectory("ezdrp-podpis-");
        try {
            String safeName = fileName.replaceAll("[\\\\/:*?\"<>|]", "_");
            Path in = dir.resolve(safeName);
            Files.write(in, docx);
            Path out = dir.resolve(stripExt(safeName) + ".pdf");

            // Domyślnie LibreOffice (działa w tle bez okien). Word tylko na wyraźne życzenie: EZDRP_CONVERTER=word
            String pref = System.getProperty("ezdrp.converter", System.getenv().getOrDefault("EZDRP_CONVERTER", "libreoffice"));
            boolean wordOk = WORD_APPS.stream().anyMatch(p -> Files.isDirectory(Path.of(p)));
            String soffice = SOFFICE.stream().filter(p -> Files.isExecutable(Path.of(p))).findFirst().orElse(null);

            if (pref.equals("word") && wordOk) {
                try {
                    convertWithWord(in, out);
                    if (Files.exists(out)) { log.info("DOCX→PDF przez Microsoft Word: %s", out.getFileName()); return Files.readAllBytes(out); }
                } catch (Exception e) {
                    log.error("Konwersja w Microsoft Word nieudana: " + CardSigner.rootMessage(e) + (soffice != null ? " – próbuję LibreOffice" : ""));
                    if (soffice == null) throw e;
                }
            }
            if (soffice != null) {
                convertWithLibreOffice(soffice, in, dir);
                if (Files.exists(out)) { log.info("DOCX→PDF przez LibreOffice: %s", out.getFileName()); return Files.readAllBytes(out); }
                throw new IOException("LibreOffice nie utworzył pliku PDF");
            }
            if (wordOk) {
                convertWithWord(in, out);
                if (Files.exists(out)) { log.info("DOCX→PDF przez Microsoft Word: %s", out.getFileName()); return Files.readAllBytes(out); }
            }
            throw new IllegalStateException("Podpis PAdES wymaga konwersji " + fileName
                    + " do PDF, a nie znaleziono LibreOffice (ani Microsoft Word). Zainstaluj LibreOffice w ~/Applications lub /Applications.");
        } finally {
            try (var s = Files.walk(dir)) { s.sorted(java.util.Comparator.reverseOrder()).forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} }); }
        }
    }

    private void convertWithWord(Path in, Path out) throws Exception {
        String script = """
                tell application "Microsoft Word"
                    set wasRunning to running
                    set theDoc to open file name (POSIX file "%s" as text) with read only without add to recent files
                    save as theDoc file name (POSIX file "%s" as text) file format format PDF
                    close theDoc saving no
                    if not wasRunning then quit
                end tell
                """.formatted(in.toString(), out.toString());
        run(List.of("/usr/bin/osascript", "-e", script), 180);
    }

    private void convertWithLibreOffice(String soffice, Path in, Path dir) throws Exception {
        // Osobny profil użytkownika, żeby nie kolidować z otwartym LibreOffice i nie pokazywać okien.
        Path profile = dir.resolve("lo-profile");
        Files.createDirectories(profile);
        run(List.of(soffice, "--headless", "--norestore", "--nologo", "--nodefault", "--nolockcheck",
                "-env:UserInstallation=" + profile.toUri(),
                "--convert-to", "pdf:writer_pdf_Export", "--outdir", dir.toString(), in.toString()), 240);
    }

    private void run(List<String> cmd, int timeoutSec) throws Exception {
        log.debug("exec %s", cmd);
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        byte[] outBytes;
        try (var is = p.getInputStream()) { outBytes = is.readAllBytes(); }
        if (!p.waitFor(timeoutSec, TimeUnit.SECONDS)) { p.destroyForcibly(); throw new IOException("Przekroczono czas konwersji"); }
        String outText = new String(outBytes, StandardCharsets.UTF_8).trim();
        if (!outText.isEmpty()) log.debug("konwerter: %s", outText);
        if (p.exitValue() != 0) throw new IOException("Konwerter zakończył się kodem " + p.exitValue() + ": " + outText);
    }

    static String stripExt(String name) {
        int i = name.lastIndexOf('.');
        return i > 0 ? name.substring(0, i) : name;
    }
}
