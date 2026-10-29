import pl.ezdrp.podpis.*;
import java.nio.file.*;
public class FillProbe {
    public static void main(String[] a) throws Exception {
        Log log = new Log(true);
        Path in = Path.of(a[0]);
        int[] n = new int[1];
        byte[] docx = DocxPlaceholders.fill(Files.readAllBytes(in), DocxPlaceholders.forSigner("Wojciech Trybowski", "Pomorski Wojewódzki Lekarz Weterynarii", "2026-10-21 15:45:00"), n);
        System.out.println("replaced=" + n[0]);
        byte[] pdf = new DocxToPdf(log).convert(docx, in.getFileName().toString());
        Path out = in.resolveSibling("filled.pdf");
        Files.write(out, pdf);
        System.out.println("PDF: " + out + " " + pdf.length);
    }
}
