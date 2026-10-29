import pl.ezdrp.podpis.*;
public class ApiProbe {
    public static void main(String[] a) throws Exception {
        Log log = new Log(true);
        EzdrpLink l = EzdrpLink.parse(a[0]);
        EzdRpClient c = new EzdRpClient(l.apiUrl(), l.proxyUrl(), log);
        try {
            var d = c.pobierzDaneHurtowePodpisu(l.token());
            System.out.println("OK: " + d);
        } catch (Exception e) {
            System.out.println("ERR: " + e.getMessage());
        }
    }
}
