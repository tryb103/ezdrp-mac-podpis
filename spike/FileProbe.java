import pl.ezdrp.podpis.*;
import java.nio.file.*;
public class FileProbe {
    public static void main(String[] a) throws Exception {
        Log log = new Log(true);
        EzdRpClient c = new EzdRpClient(a[0], null, log);
        var f = c.pobierzPlikStrumieniem(a[1]);
        System.out.println("name=" + f.name() + " ext=" + f.extension() + " size=" + f.content().length + " podpis=" + f.podpis());
        f.headers().forEach((k,v) -> { if (k.toLowerCase().startsWith("x-") || k.equalsIgnoreCase("content-disposition") || k.equalsIgnoreCase("content-type")) System.out.println("  " + k + ": " + v); });
        Path out = Path.of(System.getProperty("user.home"), "ezdrp-mac-podpis", "spike", "in-" + f.name());
        Files.write(out, f.content());
        System.out.println("saved " + out + " magic=" + new String(f.content(), 0, Math.min(8, f.content().length), "ISO-8859-1"));
    }
}
