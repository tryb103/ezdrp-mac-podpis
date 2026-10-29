import eu.europa.esig.dss.model.*;
import eu.europa.esig.dss.spi.validation.CommonCertificateVerifier;
import eu.europa.esig.dss.validation.SignedDocumentValidator;
import eu.europa.esig.dss.validation.reports.Reports;
import eu.europa.esig.dss.diagnostic.*;
import java.nio.file.*;
public class ValidateProbe {
    public static void main(String[] a) throws Exception {
        DSSDocument doc = new InMemoryDocument(Files.readAllBytes(Path.of(a[0])), a[0]);
        SignedDocumentValidator v = SignedDocumentValidator.fromDocument(doc);
        v.setCertificateVerifier(new CommonCertificateVerifier());
        Reports r = v.validateDocument();
        DiagnosticData d = r.getDiagnosticData();
        for (SignatureWrapper s : d.getSignatures()) {
            System.out.println("signature id=" + s.getId() + " format=" + s.getSignatureFormat() + " intact=" + s.isSignatureIntact() + " valid=" + s.isSignatureValid()
                + " digest=" + s.getDigestAlgorithm() + " enc=" + s.getEncryptionAlgorithm() + " keyLen=" + s.getKeyLengthUsedToSignThisToken()
                + " signer=" + (s.getSigningCertificate() != null ? s.getSigningCertificate().getCertificateDN() : "?")
                + " time=" + s.getClaimedSigningTime());
        }
        System.out.println(r.getSimpleReport().getIndication(r.getSimpleReport().getFirstSignatureId()) + " / " + r.getSimpleReport().getSubIndication(r.getSimpleReport().getFirstSignatureId()));
    }
}
