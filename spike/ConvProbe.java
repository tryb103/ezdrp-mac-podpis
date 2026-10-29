import pl.ezdrp.podpis.*;
import java.nio.file.*;
public class ConvProbe {
    public static void main(String[] a) throws Exception {
        Log log = new Log(true);
        Path in = Path.of(a[0]);
        byte[] pdf = new DocxToPdf(log).convert(Files.readAllBytes(in), in.getFileName().toString());
        Path out = in.resolveSibling(in.getFileName().toString().replaceAll("\\.[^.]*$", "") + ".pdf");
        Files.write(out, pdf);
        System.out.println("PDF: " + out + " " + pdf.length + " B, magic=" + new String(pdf, 0, 5, "ISO-8859-1"));
    }
}
