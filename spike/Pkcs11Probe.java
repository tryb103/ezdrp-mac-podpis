import java.security.*;
import java.security.cert.X509Certificate;
import java.util.*;

public class Pkcs11Probe {
    public static void main(String[] a) throws Exception {
        String lib = a[0];
        String pin = a.length > 1 ? a[1] : null;
        for (int slot = 0; slot < 4; slot++) {
            String cfg = "--name=probe" + slot + "\nlibrary=" + lib + "\nslotListIndex=" + slot + "\n";
            Provider p;
            try {
                p = Security.getProvider("SunPKCS11").configure(cfg);
                Security.addProvider(p);
            } catch (Exception e) { System.out.println("slotListIndex " + slot + ": " + e.getMessage()); continue; }
            System.out.println("slotListIndex " + slot + ": provider " + p.getName() + " OK");
            try {
                KeyStore ks = KeyStore.getInstance("PKCS11", p);
                ks.load(null, pin == null ? null : pin.toCharArray());
                for (Enumeration<String> e = ks.aliases(); e.hasMoreElements();) {
                    String al = e.nextElement();
                    java.security.cert.Certificate c = ks.getCertificate(al);
                    System.out.println("  alias=" + al + " key=" + ks.isKeyEntry(al));
                    if (c instanceof X509Certificate x) {
                        System.out.println("    subject=" + x.getSubjectX500Principal());
                        System.out.println("    issuer =" + x.getIssuerX500Principal());
                        System.out.println("    serial =" + x.getSerialNumber().toString(16));
                        System.out.println("    valid  =" + x.getNotBefore() + " .. " + x.getNotAfter());
                        System.out.println("    keyUsage=" + Arrays.toString(x.getKeyUsage()));
                    }
                }
            } catch (Exception e) { System.out.println("  keystore: " + e); Throwable t=e.getCause(); if(t!=null) System.out.println("  cause: "+t); }
            Security.removeProvider(p.getName());
        }
    }
}
