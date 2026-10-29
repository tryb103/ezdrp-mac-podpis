import sun.security.pkcs11.wrapper.*;
import java.security.cert.*;
import java.io.ByteArrayInputStream;

/** Odczyt informacji o slotach/tokenach i publicznych certyfikatów BEZ logowania (bez PIN). */
public class P11Info {
    public static void main(String[] a) throws Exception {
        String lib = a.length > 0 ? a[0] : "/Applications/Szafir2.app/Contents/app/libPKCS11Graphite.dylib";
        PKCS11 p = PKCS11.getInstance(lib, "C_GetFunctionList", null, false);
        long[] slots = p.C_GetSlotList(true);
        System.out.println("sloty z tokenem: " + slots.length);
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        for (long s : slots) {
            CK_TOKEN_INFO ti = p.C_GetTokenInfo(s);
            long f = ti.flags;
            System.out.printf("slot %d: label='%s' model='%s' serial='%s' flags=0x%x loginRequired=%b pinCountLow=%b pinFinalTry=%b pinLocked=%b%n",
                s, new String(ti.label).trim(), new String(ti.model).trim(), new String(ti.serialNumber).trim(), f,
                (f & PKCS11Constants.CKF_LOGIN_REQUIRED) != 0, (f & PKCS11Constants.CKF_USER_PIN_COUNT_LOW) != 0,
                (f & PKCS11Constants.CKF_USER_PIN_FINAL_TRY) != 0, (f & PKCS11Constants.CKF_USER_PIN_LOCKED) != 0);
            long sess = p.C_OpenSession(s, PKCS11Constants.CKF_SERIAL_SESSION, null, null);
            try {
                p.C_FindObjectsInit(sess, new CK_ATTRIBUTE[]{ new CK_ATTRIBUTE(PKCS11Constants.CKA_CLASS, PKCS11Constants.CKO_CERTIFICATE) });
                long[] objs = p.C_FindObjects(sess, 50);
                p.C_FindObjectsFinal(sess);
                System.out.println("  certyfikaty widoczne bez PIN: " + objs.length);
                for (long o : objs) {
                    CK_ATTRIBUTE[] at = { new CK_ATTRIBUTE(PKCS11Constants.CKA_VALUE) };
                    p.C_GetAttributeValue(sess, o, at);
                    byte[] der = (byte[]) at[0].pValue;
                    String label = "";
                    try { CK_ATTRIBUTE[] al = { new CK_ATTRIBUTE(PKCS11Constants.CKA_LABEL) }; p.C_GetAttributeValue(sess, o, al); label = al[0].pValue instanceof byte[] b ? new String(b) : String.valueOf(al[0].pValue); } catch (Exception ignored) {}
                    try {
                        X509Certificate c = (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(der));
                        System.out.printf("   - serial=%s subject=%s label='%s'%n", c.getSerialNumber().toString(16), c.getSubjectX500Principal().getName(), label);
                    } catch (Exception e) { System.out.println("   - (nieparsowalny) label='" + label + "'"); }
                }
            } catch (Exception e) {
                System.out.println("  listowanie certyfikatów: " + e);
            } finally { p.C_CloseSession(sess); }
        }
        p.C_Finalize(null);
    }
}
