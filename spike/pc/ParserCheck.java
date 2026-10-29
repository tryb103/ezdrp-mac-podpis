import pl.ezdrp.podpis.*;
public class ParserCheck {
    public static void main(String[] a) throws Exception {
        EzdrpLink l = EzdrpLink.parse("ezdrp://84bee5fd524c49b0bd6680e959c2e5f9/aHR0cHM6Ly9lemRycC1hcGkuc2FhczEuZXpkcnAuZ292LnBs/5");
        System.out.println(l);
        var m = EzdRpClient.class.getDeclaredMethod("cx", String.class); m.setAccessible(true);
        System.out.println("CX=" + m.invoke(null, l.token()));
    }
}
