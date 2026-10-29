package pl.ezdrp.podpis;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

/**
 * Parser linku ezdrp://{token}/{base64(url[*proxy])}/{typOperacji}
 * zgodny z NASK Desk (FileMonitorProxy.ParseArgumenty / EzdRpSessionFactory.Build).
 */
public record EzdrpLink(String token, String apiUrl, String proxyUrl, int operation, String raw) {

    public static final int OP_ODCZYT_PLIKU = 1;
    public static final int OP_EDYCJA_PLIKU = 2;
    public static final int OP_PODPISYWANIE_PLIKOW = 5;

    public static EzdrpLink parse(String link) {
        if (link == null || !link.toLowerCase().startsWith("ezdrp://")) {
            throw new IllegalArgumentException("To nie jest link ezdrp://: " + link);
        }
        // NASK Desk: Split(new[]{"/","\\"}, RemoveEmptyEntries) -> [ "ezdrp:", token, base64, op ]
        List<String> parts = Arrays.stream(link.split("[/\\\\]+"))
                .filter(s -> !s.isEmpty())
                .toList();
        if (parts.size() < 4) {
            throw new IllegalArgumentException("Link ezdrp:// ma za mało segmentów: " + link);
        }
        String token = parts.get(1);
        String b64 = parts.get(2);
        String decoded = new String(Base64.getDecoder().decode(b64), StandardCharsets.UTF_8);
        String[] urls = decoded.split("\\*");
        String api = normalize(urls[0]);
        String proxy = urls.length > 1 ? normalize(urls[1]) : null;
        int op = Integer.parseInt(parts.get(3).trim());
        return new EzdrpLink(token, api, proxy, op, link);
    }

    private static String normalize(String url) {
        String s = URI.create(url.trim()).toString();
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }

    public String operationName() {
        return switch (operation) {
            case 1 -> "OdczytPliku";
            case 2 -> "EdycjaPliku";
            case 3 -> "Wydruk";
            case 4 -> "PorownaniePlikow";
            case 5 -> "PodpisywaniePlikow";
            case 6 -> "ZapisywanieCertyfikatow";
            default -> "Operacja " + operation;
        };
    }
}
