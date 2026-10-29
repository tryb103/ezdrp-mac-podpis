package pl.ezdrp.podpis;

import eu.europa.esig.dss.enumerations.DigestAlgorithm;
import eu.europa.esig.dss.enumerations.SignatureLevel;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.model.SignatureValue;
import eu.europa.esig.dss.model.ToBeSigned;
import eu.europa.esig.dss.model.x509.CertificateToken;
import eu.europa.esig.dss.pades.PAdESSignatureParameters;
import eu.europa.esig.dss.pades.signature.PAdESService;
import eu.europa.esig.dss.service.http.commons.TimestampDataLoader;
import eu.europa.esig.dss.service.tsp.OnlineTSPSource;
import eu.europa.esig.dss.spi.validation.CommonCertificateVerifier;
import eu.europa.esig.dss.token.DSSPrivateKeyEntry;
import eu.europa.esig.dss.token.PasswordInputCallback;
import eu.europa.esig.dss.token.Pkcs11SignatureToken;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Podpis PAdES na karcie przez PKCS#11 (EU DSS). Wybór klucza po numerze seryjnym certyfikatu
 * przekazanym przez EZD RP (profil podpisu użytkownika).
 */
public class CardSigner implements AutoCloseable {

    public record KeyChoice(Pkcs11SignatureToken token, DSSPrivateKeyEntry key, int slotIndex) {}

    private final String pkcs11Lib;
    private final PasswordInputCallback pinCallback;
    private final Log log;
    private final List<Pkcs11SignatureToken> opened = new ArrayList<>();

    public CardSigner(String pkcs11Lib, PasswordInputCallback pinCallback, Log log) {
        this.pkcs11Lib = pkcs11Lib;
        this.pinCallback = pinCallback;
        this.log = log;
    }

    /**
     * Wybiera slot z certyfikatem o podanym numerze seryjnym. Najpierw przegląd bez PIN (SlotScanner);
     * PIN trafia wyłącznie do slotu, w którym certyfikat jest widoczny. Gdy certyfikatów nie widać bez PIN,
     * próbuje slotów w kolejności: kwalifikowany (QSCD) → pozostałe, pomijając sloty z zablokowanym PIN
     * lub z ostatnią próbą PIN (ochrona przed zablokowaniem karty).
     */
    public KeyChoice findKey(String serialHexWanted, int maxSlots) {
        String wanted = normalizeSerial(serialHexWanted);
        List<SlotScanner.SlotInfo> scanned = SlotScanner.scan(pkcs11Lib, log);
        List<Integer> order = new ArrayList<>();
        boolean certVisible = false;
        if (!scanned.isEmpty()) {
            for (SlotScanner.SlotInfo s : scanned) if (s.hasSerial(wanted)) { order.add(s.index()); certVisible = true; }
            if (!certVisible) {
                boolean anyCerts = scanned.stream().anyMatch(s -> !s.certSerials().isEmpty());
                if (anyCerts && wanted != null) {
                    throw new IllegalStateException("Na karcie nie ma certyfikatu o numerze seryjnym " + serialHexWanted
                            + ", którego oczekuje EZD RP. Widoczne certyfikaty: " + scanned.stream()
                            .flatMap(s -> s.certSerials().stream()).toList()
                            + ". Sprawdź profil podpisu w EZD RP (Ustawienia użytkownika → Podpisy) albo włóż właściwą kartę.");
                }
                for (SlotScanner.SlotInfo s : scanned) if (s.looksQualified() && usable(s)) order.add(s.index());
                for (SlotScanner.SlotInfo s : scanned) if (!s.looksQualified() && usable(s)) order.add(s.index());
            }
        } else {
            for (int i = 0; i < maxSlots; i++) order.add(i);
        }
        if (order.isEmpty()) {
            throw new IllegalStateException("Brak slotu karty, do którego można bezpiecznie wysłać PIN (PIN zablokowany lub ostatnia próba). "
                    + "Odblokuj PIN w aplikacji Szafir.");
        }
        log.info("Kolejność prób slotów: %s%s", order, certVisible ? " (certyfikat widoczny bez PIN)" : " (tryb awaryjny)");

        List<String> seen = new ArrayList<>();
        KeyChoice fallback = null;
        for (int slot : order) {
            Pkcs11SignatureToken token;
            try {
                token = new Pkcs11SignatureToken(pkcs11Lib, pinCallback, slot);
                opened.add(token);
            } catch (Exception e) {
                log.debug("slot %d: nie można otworzyć (%s)", slot, e.getMessage());
                continue;
            }
            List<DSSPrivateKeyEntry> keys;
            try {
                keys = token.getKeys();
            } catch (Exception e) {
                String msg = rootMessage(e);
                log.info("slot %d: brak dostępu do kluczy (%s)", slot, msg);
                if (msg.contains("CKR_PIN_INCORRECT")) {
                    throw new IllegalStateException("Nieprawidłowy PIN (slot " + slot + "). Uwaga: kolejne błędne próby zablokują kartę.", e);
                }
                if (msg.contains("CKR_PIN_LOCKED")) {
                    throw new IllegalStateException("PIN karty jest zablokowany (slot " + slot + "). Odblokuj go w aplikacji Szafir.", e);
                }
                continue;
            }
            for (DSSPrivateKeyEntry k : keys) {
                CertificateToken c = k.getCertificate();
                String serial = c.getSerialNumber().toString(16).toLowerCase(Locale.ROOT);
                String subj = c.getSubject().getRFC2253();
                seen.add("slot " + slot + " serial=" + serial + " " + subj);
                log.info("slot %d: certyfikat serial=%s subject=%s", slot, serial, subj);
                if (wanted != null && normalizeSerial(serial).equals(wanted)) {
                    return new KeyChoice(token, k, slot);
                }
                if (fallback == null && isSigningCert(c)) fallback = new KeyChoice(token, k, slot);
            }
        }
        if (wanted == null && fallback != null) {
            log.info("EZD RP nie podał numeru seryjnego, używam pierwszego certyfikatu podpisu: slot %d", fallback.slotIndex());
            return fallback;
        }
        throw new IllegalStateException("Nie znaleziono na karcie certyfikatu o numerze seryjnym " + serialHexWanted
                + ". Dostępne: " + (seen.isEmpty() ? "brak" : String.join("; ", seen))
                + ". Sprawdź profil podpisu w EZD RP (Ustawienia użytkownika → Podpisy).");
    }

    private static boolean usable(SlotScanner.SlotInfo s) {
        return !s.pinLocked() && !s.pinFinalTry();
    }

    public byte[] signPades(byte[] pdf, String fileName, KeyChoice choice, boolean withTimestamp, String tsaUrl) throws IOException {
        DSSDocument doc = new InMemoryDocument(pdf, fileName);
        PAdESSignatureParameters p = new PAdESSignatureParameters();
        p.setDigestAlgorithm(DigestAlgorithm.SHA256);
        p.setSigningCertificate(choice.key().getCertificate());
        p.setCertificateChain(choice.key().getCertificateChain());
        boolean ts = withTimestamp && tsaUrl != null && !tsaUrl.isBlank();
        p.setSignatureLevel(ts ? SignatureLevel.PAdES_BASELINE_T : SignatureLevel.PAdES_BASELINE_B);

        PAdESService service = new PAdESService(new CommonCertificateVerifier());
        if (ts) {
            OnlineTSPSource tsp = new OnlineTSPSource(tsaUrl);
            tsp.setDataLoader(new TimestampDataLoader());
            service.setTspSource(tsp);
            log.info("Znacznik czasu z %s", tsaUrl);
        }
        ToBeSigned tbs = service.getDataToSign(doc, p);
        SignatureValue sv = choice.token().sign(tbs, p.getDigestAlgorithm(), choice.key());
        DSSDocument signed = service.signDocument(doc, p, sv);
        try (InputStream in = signed.openStream()) {
            return in.readAllBytes();
        }
    }

    static boolean isSigningCert(CertificateToken c) {
        boolean[] ku = c.getCertificate().getKeyUsage();
        // nonRepudiation (1) lub digitalSignature (0)
        return ku == null || ku[1] || ku[0];
    }

    static String normalizeSerial(String s) {
        if (s == null) return null;
        String t = s.trim().toLowerCase(Locale.ROOT).replace(":", "").replace(" ", "");
        if (t.startsWith("0x")) t = t.substring(2);
        t = t.replaceFirst("^0+(?=.)", "");
        return t.isEmpty() ? null : t;
    }

    static String rootMessage(Throwable t) {
        while (t.getCause() != null) t = t.getCause();
        return t.getMessage() == null ? t.toString() : t.getMessage();
    }

    @Override
    public void close() {
        for (Pkcs11SignatureToken t : opened) {
            try { t.close(); } catch (Exception ignored) {}
        }
    }
}
