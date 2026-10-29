import pl.ezdrp.podpis.*;
public class ParserCheck {
    public static void main(String[] a) throws Exception {
        EzdrpLink l = EzdrpLink.parse("ezdrp://0123456789abcdef0123456789abcdef/aHR0cHM6Ly9lemRycC1hcGkuc2FhczEuZXpkcnAuZ292LnBs/5");
        System.out.println(l);
        var m = EzdRpClient.class.getDeclaredMethod("cx", String.class); m.setAccessible(true);
        System.out.println("CX=" + m.invoke(null, l.token()));
    }
}
