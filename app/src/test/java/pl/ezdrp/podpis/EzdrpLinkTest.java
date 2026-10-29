package pl.ezdrp.podpis;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EzdrpLinkTest {

    static final String LINK = "ezdrp://0123456789abcdef0123456789abcdef/aHR0cHM6Ly9lemRycC1hcGkuc2FhczEuZXpkcnAuZ292LnBs/5";

    @Test
    void parsujeLinkPodpisu() {
        EzdrpLink l = EzdrpLink.parse(LINK);
        assertEquals("0123456789abcdef0123456789abcdef", l.token());
        assertEquals("https://ezdrp-api.saas1.ezdrp.gov.pl", l.apiUrl());
        assertNull(l.proxyUrl());
        assertEquals(EzdrpLink.OP_PODPISYWANIE_PLIKOW, l.operation());
        assertEquals("PodpisywaniePlikow", l.operationName());
        assertEquals(LINK, l.raw());
    }

    @Test
    void obslugujeProxyUkosnikiWsteczneIKoncowyUkosnik() {
        // base64("https://api.example.gov.pl/*http://proxy.local:3128")
        String b64 = java.util.Base64.getEncoder().encodeToString("https://api.example.gov.pl/*http://proxy.local:3128".getBytes());
        EzdrpLink l = EzdrpLink.parse("EZDRP://tok\\" + b64 + "\\1");
        assertEquals("tok", l.token());
        assertEquals("https://api.example.gov.pl", l.apiUrl());
        assertEquals("http://proxy.local:3128", l.proxyUrl());
        assertEquals(1, l.operation());
        assertEquals("OdczytPliku", l.operationName());
    }

    @Test
    void odrzucaZleLinki() {
        assertThrows(IllegalArgumentException.class, () -> EzdrpLink.parse("https://ezdrp.gov.pl/x"));
        assertThrows(IllegalArgumentException.class, () -> EzdrpLink.parse("ezdrp://tylko-token"));
        assertThrows(IllegalArgumentException.class, () -> EzdrpLink.parse(null));
    }
}
