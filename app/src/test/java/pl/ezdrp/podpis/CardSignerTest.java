package pl.ezdrp.podpis;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CardSignerTest {

    @Test
    void normalizacjaNumeruSeryjnego() {
        assertEquals("6bd242ce", CardSigner.normalizeSerial("6BD242CE"));
        assertEquals("6bd242ce", CardSigner.normalizeSerial(" 0x6B:D2:42:CE "));
        assertEquals("6bd242ce", CardSigner.normalizeSerial("006bd242ce"));
        assertEquals("0", CardSigner.normalizeSerial("0000"));
        assertNull(CardSigner.normalizeSerial(null));
        assertNull(CardSigner.normalizeSerial("  "));
    }

    @Test
    void slotInfoRozpoznajeCertyfikatIProfilKwalifikowany() {
        SlotScanner.SlotInfo qscd = new SlotScanner.SlotInfo(1, 1L, "PKI Token 2 (QSCD)", false, false, false,
                List.of("6bd242ce1500b44de02f042686ba3525a9a40b22"));
        SlotScanner.SlotInfo primary = new SlotScanner.SlotInfo(0, 0L, "PKI Token 1 (Primary)", false, false, false, List.of());
        assertTrue(qscd.hasSerial(CardSigner.normalizeSerial("6BD242CE1500B44DE02F042686BA3525A9A40B22")));
        assertFalse(primary.hasSerial(CardSigner.normalizeSerial("6BD242CE1500B44DE02F042686BA3525A9A40B22")));
        assertFalse(qscd.hasSerial(null));
        assertTrue(qscd.looksQualified());
        assertFalse(primary.looksQualified());
    }
}
