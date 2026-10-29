package pl.ezdrp.podpis;

import sun.security.pkcs11.wrapper.CK_ATTRIBUTE;
import sun.security.pkcs11.wrapper.CK_TOKEN_INFO;
import sun.security.pkcs11.wrapper.PKCS11;
import sun.security.pkcs11.wrapper.PKCS11Constants;

import java.io.ByteArrayInputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Przegląd slotów PKCS#11 BEZ logowania (bez PIN): etykieta tokenu, stan PIN-u (zablokowany / ostatnia próba)
 * oraz certyfikaty widoczne publicznie. Dzięki temu PIN jest wysyłany tylko do slotu, który na pewno
 * zawiera certyfikat wskazany przez EZD RP, i nie zużywa prób PIN w innych profilach karty.
 */
final class SlotScanner {

    record SlotInfo(int index, long slotId, String label, boolean pinLocked, boolean pinFinalTry, boolean pinCountLow,
                    List<String> certSerials) {
        boolean hasSerial(String normalized) {
            return normalized != null && certSerials.stream().anyMatch(s -> CardSigner.normalizeSerial(s).equals(normalized));
        }
        boolean looksQualified() {
            String l = label.toUpperCase(Locale.ROOT);
            return l.contains("QSCD") || l.contains("KWALIF") || l.contains("QUALIF");
        }
    }

    static List<SlotInfo> scan(String lib, Log log) {
        List<SlotInfo> out = new ArrayList<>();
        PKCS11 p = null;
        try {
            p = PKCS11.getInstance(lib, "C_GetFunctionList", null, false);
            long[] slots = p.C_GetSlotList(true);
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            for (int i = 0; i < slots.length; i++) {
                long s = slots[i];
                String label = "";
                long f = 0;
                try {
                    CK_TOKEN_INFO ti = p.C_GetTokenInfo(s);
                    label = new String(ti.label).trim();
                    f = ti.flags;
                } catch (Exception e) {
                    log.debug("slot %d: C_GetTokenInfo: %s", i, e.getMessage());
                }
                List<String> serials = new ArrayList<>();
                long sess = -1;
                try {
                    sess = p.C_OpenSession(s, PKCS11Constants.CKF_SERIAL_SESSION, null, null);
                    p.C_FindObjectsInit(sess, new CK_ATTRIBUTE[]{new CK_ATTRIBUTE(PKCS11Constants.CKA_CLASS, PKCS11Constants.CKO_CERTIFICATE)});
                    long[] objs = p.C_FindObjects(sess, 64);
                    p.C_FindObjectsFinal(sess);
                    for (long o : objs) {
                        try {
                            CK_ATTRIBUTE[] at = {new CK_ATTRIBUTE(PKCS11Constants.CKA_VALUE)};
                            p.C_GetAttributeValue(sess, o, at);
                            if (at[0].pValue instanceof byte[] der) {
                                X509Certificate c = (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(der));
                                serials.add(c.getSerialNumber().toString(16).toLowerCase(Locale.ROOT));
                            }
                        } catch (Exception e) {
                            log.debug("slot %d: certyfikat nieczytelny: %s", i, e.getMessage());
                        }
                    }
                } catch (Exception e) {
                    log.debug("slot %d: listowanie certyfikatów bez PIN nieudane: %s", i, e.getMessage());
                } finally {
                    if (sess >= 0) try { p.C_CloseSession(sess); } catch (Exception ignored) {}
                }
                SlotInfo si = new SlotInfo(i, s, label,
                        (f & PKCS11Constants.CKF_USER_PIN_LOCKED) != 0,
                        (f & PKCS11Constants.CKF_USER_PIN_FINAL_TRY) != 0,
                        (f & PKCS11Constants.CKF_USER_PIN_COUNT_LOW) != 0, serials);
                log.info("slot %d: '%s' PIN: locked=%b finalTry=%b countLow=%b, certyfikaty bez PIN: %s",
                        i, label, si.pinLocked(), si.pinFinalTry(), si.pinCountLow(), serials);
                out.add(si);
            }
        } catch (Throwable t) {
            log.info("Przegląd slotów PKCS#11 nieudany (%s) – użyję trybu awaryjnego", CardSigner.rootMessage(t));
        } finally {
            // Nie wywołujemy C_Finalize: SunPKCS11 (DSS) korzysta z tej samej biblioteki w tym procesie.
        }
        return out;
    }
}
