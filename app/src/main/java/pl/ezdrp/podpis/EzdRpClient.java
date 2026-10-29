package pl.ezdrp.podpis;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Klient „ICE API” EZD RP używanego przez NASK Desk (IceApiIntegrationHttpClient).
 * Każde żądanie: POST JSON {Token, Patch:175, ...}, nagłówki RX (licznik), CX (SHA-1 tokenu), x-client-version.
 */
public class EzdRpClient {

    public static final int PATCH = 175;
    public static final String CLIENT_VERSION = "2.0.2.71";
    private static final Duration TIMEOUT = Duration.ofMinutes(3);

    private final String baseUrl;
    private final HttpClient http;
    private final Map<String, Integer> tokenCounter = new ConcurrentHashMap<>();
    private final Log log;

    public EzdRpClient(String baseUrl, String proxyUrl, Log log) {
        this.baseUrl = baseUrl;
        this.log = log;
        HttpClient.Builder b = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NORMAL);
        if (proxyUrl != null && !proxyUrl.isBlank()) {
            URI p = URI.create(proxyUrl);
            b.proxy(ProxySelector.of(new InetSocketAddress(p.getHost(), p.getPort() > 0 ? p.getPort() : 8080)));
        }
        this.http = b.build();
    }

    // ---------- modele ----------

    public record DaneHurtowePodpisu(List<String> tokeny, String numerSeryjnyCertyfikatu, String urlSerweraTSA, String rawJson) {
        public String sygnatariuszImieINazwisko() { return Json.string(rawJson, "sygnatariuszImieINazwisko"); }
        public String sygnatariuszStanowisko() { return Json.string(rawJson, "sygnatariuszStanowisko"); }
        public String sygnatariuszKomorka() { return Json.string(rawJson, "sygnatariuszKomorkaOrganizacyjna"); }
        public String sygnatariuszPodmiot() { return Json.string(rawJson, "sygnatariuszNazwaPodmiotu"); }
    }

    public record DanePodpisu(String type, boolean kontrasygnata, boolean znacznikCzasu, String urlTSA) {}

    public record PobranyPlik(String name, String extension, byte[] content, DanePodpisu podpis, Map<String, String> headers) {}

    // ---------- operacje ----------

    public DaneHurtowePodpisu pobierzDaneHurtowePodpisu(String token) throws Exception {
        String body = requestJson(token, "{\"Token\":" + Json.str(token) + ",\"Patch\":" + PATCH + "}");
        HttpResponse<String> r = post(token, "filemonitor-pobierz-dane-hurtowe-podpisu", body);
        ensureOk(r, r.body());
        String json = r.body();
        List<String> tokeny = Json.stringArray(json, "tokeny");
        String serial = Json.string(json, "numerSeryjnyCertyfikatu");
        String tsa = Json.string(json, "urlSerweraTSA");
        return new DaneHurtowePodpisu(tokeny, serial, tsa, json);
    }

    public PobranyPlik pobierzPlikStrumieniem(String tokenPliku) throws Exception {
        String body = "{\"Token\":" + Json.str(tokenPliku) + ",\"Patch\":" + PATCH + "}";
        HttpRequest req = baseRequest(tokenPliku, "filemonitor-pobierz-plik/true")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<byte[]> r = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (r.statusCode() / 100 != 2) {
            ensureOk(r, new String(r.body(), StandardCharsets.UTF_8));
        }
        Map<String, String> headers = new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        r.headers().map().forEach((k, v) -> headers.put(k, String.join(",", v)));
        String filename = fileNameFromContentDisposition(headers.get("content-disposition")).orElse("downloaded.file");
        String ext = filename.contains(".") ? filename.substring(filename.lastIndexOf('.') + 1) : "";
        DanePodpisu dp = new DanePodpisu(
                headers.getOrDefault("X-Dane-Podpisu-Type", ""),
                Boolean.parseBoolean(headers.getOrDefault("X-Dane-Podpisu-Czy-Kontrasygnata", "false")),
                Boolean.parseBoolean(headers.getOrDefault("X-Dane-Podpisu-Czy-Znacznik-Czasu", "false")),
                headers.getOrDefault("X-Dane-Podpisu-Znacznik-Czasu-Url-Serwera-TSA", ""));
        log.info("Pobrano plik %s (%d B), podpis: %s, kontrasygnata=%s, TSA=%s %s",
                filename, r.body().length, dp.type(), dp.kontrasygnata(), dp.znacznikCzasu(), dp.urlTSA());
        return new PobranyPlik(filename, ext, r.body(), dp, headers);
    }

    /** Domyślna ścieżka NASK Desk (ustawienie StreamUploadEzdRp wyłączone): JSON z plikiem w base64. */
    public int zapiszPlik(String tokenPliku, byte[] signed) throws Exception {
        String json = "{\"Token\":" + Json.str(tokenPliku) + ",\"Patch\":" + PATCH + ",\"File\":\"" + java.util.Base64.getEncoder().encodeToString(signed) + "\"}";
        HttpRequest req = baseRequest(tokenPliku, "filemonitor-save-file")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> r = http.send(req, HttpResponse.BodyHandlers.ofString());
        ensureOk(r, r.body());
        log.info("Zapis pliku (base64): HTTP %d", r.statusCode());
        return r.statusCode();
    }

    public int zapiszPlikStrumieniem(String tokenPliku, byte[] signed) throws Exception {
        String boundary = "----EzdRpPodpis" + Long.toHexString(System.nanoTime());
        String json = "{\"Token\":" + Json.str(tokenPliku) + ",\"Patch\":" + PATCH + ",\"File\":null}";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writePart(out, boundary, "json", "json", json.getBytes(StandardCharsets.UTF_8));
        writePart(out, boundary, "file", "file", signed);
        out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.US_ASCII));
        HttpRequest req = baseRequest(tokenPliku, "filemonitor-save-file-stream")
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray()))
                .build();
        HttpResponse<String> r = http.send(req, HttpResponse.BodyHandlers.ofString());
        ensureOk(r, r.body());
        log.info("Zapis pliku: HTTP %d", r.statusCode());
        return r.statusCode();
    }

    // ---------- wewnętrzne ----------

    private static void writePart(ByteArrayOutputStream out, String boundary, String name, String filename, byte[] data) throws IOException {
        out.write(("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + filename + "\"\r\n"
                + "Content-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        out.write(data);
        out.write("\r\n".getBytes(StandardCharsets.US_ASCII));
    }

    private String requestJson(String token, String json) { return json; }

    private HttpResponse<String> post(String token, String endpoint, String jsonBody) throws Exception {
        HttpRequest req = baseRequest(token, endpoint)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .build();
        log.debug("POST %s body=%s", req.uri(), jsonBody);
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest.Builder baseRequest(String token, String endpoint) throws Exception {
        String url = endpoint.startsWith("/") ? baseUrl + endpoint : baseUrl + "/" + endpoint;
        int rx = tokenCounter.merge(token, 1, Integer::sum);
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Accept", "application/json, */*")
                .header("RX", Integer.toString(rx))
                .header("CX", cx(token))
                .header("x-client-version", CLIENT_VERSION);
    }

    /** SHA-1 tokenu, każdy bajt jako liczba dziesiętna bez separatora (jak NASK Desk TokenToSha1Converter). */
    static String cx(String token) throws Exception {
        byte[] d = MessageDigest.getInstance("SHA-1").digest(token.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : d) sb.append(b & 0xff);
        return sb.toString();
    }

    private void ensureOk(HttpResponse<?> r, String body) {
        if (r.statusCode() / 100 == 2) return;
        String title = Json.string(body, "title");
        String detail = Json.string(body, "detail");
        String msg = "Błąd w odpowiedzi z serwera (" + r.statusCode() + ") " + r.uri().getPath()
                + (title != null ? ": " + title : "") + (detail != null && !detail.equals(title) ? " " + detail : "");
        log.error("%s\n%s", msg, body);
        throw new RuntimeException(msg);
    }

    private static final Pattern FN_STAR = Pattern.compile("filename\\*\\s*=\\s*(?:UTF-8|utf-8)''([^;]+)");
    private static final Pattern FN = Pattern.compile("filename\\s*=\\s*\"?([^\";]+)\"?");

    static Optional<String> fileNameFromContentDisposition(String cd) {
        if (cd == null) return Optional.empty();
        Matcher m = FN_STAR.matcher(cd);
        if (m.find()) return Optional.of(java.net.URLDecoder.decode(m.group(1).trim(), StandardCharsets.UTF_8));
        m = FN.matcher(cd);
        if (m.find()) return Optional.of(m.group(1).trim());
        return Optional.empty();
    }

    /** Minimalny parser JSON wystarczający dla odpowiedzi ICE API (bez zależności). */
    static final class Json {
        static String str(String s) {
            StringBuilder sb = new StringBuilder("\"");
            for (char c : s.toCharArray()) {
                switch (c) {
                    case '"' -> sb.append("\\\"");
                    case '\\' -> sb.append("\\\\");
                    case '\n' -> sb.append("\\n");
                    case '\r' -> sb.append("\\r");
                    case '\t' -> sb.append("\\t");
                    default -> { if (c < 0x20) sb.append(String.format("\\u%04x", (int) c)); else sb.append(c); }
                }
            }
            return sb.append('"').toString();
        }

        static String string(String json, String key) {
            if (json == null) return null;
            Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(null|\"((?:[^\"\\\\]|\\\\.)*)\")", Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(json);
            if (!m.find()) return null;
            if (m.group(1).equals("null")) return null;
            return unescape(m.group(2));
        }

        static List<String> stringArray(String json, String key) {
            List<String> out = new ArrayList<>();
            Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\\[(.*?)\\]", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
            Matcher m = p.matcher(json);
            if (!m.find()) return out;
            Matcher e = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(m.group(1));
            while (e.find()) out.add(unescape(e.group(1)));
            return out;
        }

        private static String unescape(String s) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c == '\\' && i + 1 < s.length()) {
                    char n = s.charAt(++i);
                    switch (n) {
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'u' -> { sb.append((char) Integer.parseInt(s.substring(i + 1, i + 5), 16)); i += 4; }
                        default -> sb.append(n);
                    }
                } else sb.append(c);
            }
            return sb.toString();
        }
    }
}
