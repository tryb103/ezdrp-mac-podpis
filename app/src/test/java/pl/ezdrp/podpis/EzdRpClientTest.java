package pl.ezdrp.podpis;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EzdRpClientTest {

    /** CX = SHA-1 tokenu, każdy bajt jako liczba dziesiętna bez separatora (NASK Desk TokenToSha1Converter). */
    @Test
    void cxToSha1JakoCiagLiczbDziesietnych() throws Exception {
        String token = "0123456789abcdef0123456789abcdef";
        // niezależna implementacja: hex SHA-1 -> pary znaków -> liczby dziesiętne
        String hex = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(token.getBytes(StandardCharsets.UTF_8)));
        StringBuilder expected = new StringBuilder();
        for (int i = 0; i < hex.length(); i += 2) expected.append(Integer.parseInt(hex.substring(i, i + 2), 16));
        assertEquals(expected.toString(), EzdRpClient.cx(token));
        assertEquals(EzdRpClient.cx("abc"), EzdRpClient.cx("abc"));
        assertNotEquals(EzdRpClient.cx("abc"), EzdRpClient.cx("abd"));
    }

    @Test
    void cxZnanyWektor() throws Exception {
        // SHA-1("abc") = a9993e364706816aba3e25717850c26c9cd0d89d
        // bajty: 169 153 62 54 71 6 129 106 186 62 37 113 120 80 194 108 156 208 216 157
        assertEquals("1691536254716129106186623711312080194108156208216157", EzdRpClient.cx("abc"));
    }

    @Test
    void nazwaPlikuZContentDisposition() {
        assertEquals("Umowa nr 1.pdf", EzdRpClient.fileNameFromContentDisposition(
                "attachment; filename=\"Umowa nr 1.pdf\"").orElseThrow());
        assertEquals("Zażalenie ś.docx", EzdRpClient.fileNameFromContentDisposition(
                "attachment; filename=\"Za?alenie ?.docx\"; filename*=UTF-8''Za%C5%BCalenie%20%C5%9B.docx").orElseThrow());
        assertEquals("plik.pdf", EzdRpClient.fileNameFromContentDisposition("inline; filename=plik.pdf").orElseThrow());
        assertTrue(EzdRpClient.fileNameFromContentDisposition(null).isEmpty());
    }

    @Test
    void minimalnyParserJson() {
        String json = "{\"tokeny\":[\"a1\",\"b2\"],\"numerSeryjnyCertyfikatu\":\"6BD2\",\"urlSerweraTSA\":null,"
                + "\"sygnatariuszImieINazwisko\":\"Jan \\\"Kowalski\\\"\",\"x\":1}";
        assertEquals(List.of("a1", "b2"), EzdRpClient.Json.stringArray(json, "tokeny"));
        assertEquals("6BD2", EzdRpClient.Json.string(json, "numerSeryjnyCertyfikatu"));
        assertNull(EzdRpClient.Json.string(json, "urlSerweraTSA"));
        assertEquals("Jan \"Kowalski\"", EzdRpClient.Json.string(json, "sygnatariuszImieINazwisko"));
        assertNull(EzdRpClient.Json.string(json, "brak"));
        assertEquals("\"a\\\"b\\n\"", EzdRpClient.Json.str("a\"b\n"));
    }
}
