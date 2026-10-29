package pl.ezdrp.podpis;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Wypełnia w DOCX placeholdery szablonu EZD RP dla sygnatariusza:
 * $ezdPracownikStanowisko, $ezdPracownikNazwa, $ezdDataPodpisu (oraz warianty bez „Pracownik”).
 * Działa tylko, gdy placeholder jest w jednym węźle tekstowym; w innym wypadku zostawia tekst bez zmian.
 */
public final class DocxPlaceholders {

    private DocxPlaceholders() {}

    public static Map<String, String> forSigner(String nazwa, String stanowisko, String data) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("$ezdPracownikStanowisko", stanowisko);
        m.put("$ezdPracownikNazwa", nazwa);
        m.put("$ezdDataPodpisu", data);
        m.put("$ezdSygnatariuszStanowisko", stanowisko);
        m.put("$ezdSygnatariuszNazwa", nazwa);
        m.put("$ezdSygnatariuszImieINazwisko", nazwa);
        return m;
    }

    /** @return nowy DOCX oraz liczba podmienionych placeholderów (w result[0]). */
    public static byte[] fill(byte[] docx, Map<String, String> values, int[] replacedCount) throws Exception {
        int count = 0;
        ByteArrayOutputStream bos = new ByteArrayOutputStream(docx.length + 1024);
        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(docx));
             ZipOutputStream zout = new ZipOutputStream(bos)) {
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                byte[] data = zin.readAllBytes();
                String name = e.getName();
                if (name.startsWith("word/") && name.endsWith(".xml")
                        && (name.equals("word/document.xml") || name.startsWith("word/header") || name.startsWith("word/footer"))) {
                    String xml = new String(data, StandardCharsets.UTF_8);
                    for (Map.Entry<String, String> v : values.entrySet()) {
                        if (v.getValue() == null) continue;
                        String ph = v.getKey();
                        int before = xml.length();
                        String rep = xmlEscape(v.getValue());
                        int idx, from = 0, n = 0;
                        StringBuilder sb = new StringBuilder();
                        while ((idx = xml.indexOf(ph, from)) >= 0) {
                            // podmieniamy tylko wewnątrz tekstu (nie wewnątrz tagu)
                            int lt = xml.lastIndexOf('<', idx), gt = xml.lastIndexOf('>', idx);
                            sb.append(xml, from, idx);
                            if (gt >= lt) { sb.append(rep); n++; } else sb.append(ph);
                            from = idx + ph.length();
                        }
                        sb.append(xml.substring(from));
                        xml = sb.toString();
                        count += n;
                    }
                    data = xml.getBytes(StandardCharsets.UTF_8);
                }
                ZipEntry ne = new ZipEntry(name);
                zout.putNextEntry(ne);
                zout.write(data);
                zout.closeEntry();
            }
        }
        if (replacedCount != null && replacedCount.length > 0) replacedCount[0] = count;
        return count == 0 ? docx : bos.toByteArray();
    }

    private static String xmlEscape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
