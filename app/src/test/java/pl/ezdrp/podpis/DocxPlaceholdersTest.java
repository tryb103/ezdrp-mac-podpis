package pl.ezdrp.podpis;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class DocxPlaceholdersTest {

    static byte[] docx(Map<String, String> entries) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(bos)) {
            for (Map.Entry<String, String> e : entries.entrySet()) {
                z.putNextEntry(new ZipEntry(e.getKey()));
                z.write(e.getValue().getBytes(StandardCharsets.UTF_8));
                z.closeEntry();
            }
        }
        return bos.toByteArray();
    }

    static Map<String, String> unzip(byte[] zip) throws Exception {
        Map<String, String> out = new LinkedHashMap<>();
        try (ZipInputStream z = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry e;
            while ((e = z.getNextEntry()) != null) out.put(e.getName(), new String(z.readAllBytes(), StandardCharsets.UTF_8));
        }
        return out;
    }

    @Test
    void wypelniaPlaceholderyTylkoWTekscieIEscapujeXml() throws Exception {
        Map<String, String> in = new LinkedHashMap<>();
        in.put("[Content_Types].xml", "<Types/>");
        in.put("word/document.xml", "<w:p><w:t>$ezdPracownikNazwa</w:t><w:t>$ezdPracownikStanowisko</w:t>"
                + "<w:t>$ezdDataPodpisu</w:t><w:x attr=\"$ezdPracownikNazwa\"/></w:p>");
        in.put("word/footer1.xml", "<w:t>$ezdSygnatariuszImieINazwisko</w:t>");
        in.put("word/styles.xml", "<w:t>$ezdPracownikNazwa</w:t>");
        int[] n = new int[1];
        byte[] out = DocxPlaceholders.fill(docx(in), DocxPlaceholders.forSigner("Jan & <Kowalski>", "Dyrektor", "2026-10-29 12:00:00"), n);
        Map<String, String> res = unzip(out);
        assertEquals(4, n[0]);
        assertEquals("<w:p><w:t>Jan &amp; &lt;Kowalski&gt;</w:t><w:t>Dyrektor</w:t><w:t>2026-10-29 12:00:00</w:t>"
                + "<w:x attr=\"$ezdPracownikNazwa\"/></w:p>", res.get("word/document.xml"));
        assertEquals("<w:t>Jan &amp; &lt;Kowalski&gt;</w:t>", res.get("word/footer1.xml"));
        assertEquals("<w:t>$ezdPracownikNazwa</w:t>", res.get("word/styles.xml"), "styles.xml nie jest ruszane");
        assertEquals("<Types/>", res.get("[Content_Types].xml"));
        assertEquals(in.keySet(), res.keySet());
    }

    @Test
    void bezPlaceholderowZwracaOryginal() throws Exception {
        Map<String, String> in = Map.of("word/document.xml", "<w:t>zwykły tekst</w:t>");
        byte[] src = docx(in);
        int[] n = new int[1];
        assertSame(src, DocxPlaceholders.fill(src, DocxPlaceholders.forSigner("A", "B", "C"), n));
        assertEquals(0, n[0]);
    }

    @Test
    void wartoscNullNieJestPodmieniana() throws Exception {
        Map<String, String> in = Map.of("word/document.xml", "<w:t>$ezdPracownikStanowisko</w:t>");
        int[] n = new int[1];
        DocxPlaceholders.fill(docx(in), DocxPlaceholders.forSigner("A", null, "C"), n);
        assertEquals(0, n[0]);
    }
}
