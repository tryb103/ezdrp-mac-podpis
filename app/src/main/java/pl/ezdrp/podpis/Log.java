package pl.ezdrp.podpis;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Prosty log do ~/Library/Logs/EZD RP Podpis/ezdrp-podpis.log oraz na stderr. */
public class Log {
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private final Path file;
    private final boolean debug;

    public Log(boolean debug) {
        this.debug = debug;
        Path dir = Path.of(System.getProperty("user.home"), "Library", "Logs", "EZD RP Podpis");
        Path f = null;
        try { Files.createDirectories(dir); f = dir.resolve("ezdrp-podpis.log"); } catch (IOException ignored) {}
        this.file = f;
    }

    public Path file() { return file; }

    public void info(String fmt, Object... a) { write("INFO ", String.format(fmt, a)); }
    public void error(String fmt, Object... a) { write("ERROR", String.format(fmt, a)); }
    public void debug(String fmt, Object... a) { if (debug) write("DEBUG", String.format(fmt, a)); }
    public void error(String msg, Throwable t) {
        java.io.StringWriter sw = new java.io.StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        write("ERROR", msg + "\n" + sw);
    }

    private synchronized void write(String level, String msg) {
        String line = TS.format(LocalDateTime.now()) + " " + level + " " + msg;
        System.err.println(line);
        if (file != null) {
            try { Files.writeString(file, line + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND); } catch (IOException ignored) {}
        }
    }
}
