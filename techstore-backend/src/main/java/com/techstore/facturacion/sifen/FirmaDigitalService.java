package com.techstore.facturacion.sifen;

import com.techstore.comun.BusinessException;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.List;
import javax.xml.crypto.dsig.CanonicalizationMethod;
import javax.xml.crypto.dsig.DigestMethod;
import javax.xml.crypto.dsig.Reference;
import javax.xml.crypto.dsig.SignedInfo;
import javax.xml.crypto.dsig.Transform;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMSignContext;
import javax.xml.crypto.dsig.keyinfo.KeyInfo;
import javax.xml.crypto.dsig.keyinfo.KeyInfoFactory;
import javax.xml.crypto.dsig.keyinfo.X509Data;
import javax.xml.crypto.dsig.spec.C14NMethodParameterSpec;
import javax.xml.crypto.dsig.spec.TransformParameterSpec;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

/**
 * Firma el XML del Documento Electrónico con XMLDSig (firma envolvente sobre
 * el elemento {@code <DE>}, referenciando su Id, como exige el SIFEN).
 *
 * <p>Usa un certificado autofirmado de PRUEBA incluido en el proyecto. NO es
 * válido para el SIFEN real: para producción hay que reemplazarlo por un
 * certificado de firma emitido por un prestador habilitado.
 */
@Service
public class FirmaDigitalService {

    private static final String RECURSO_KEYSTORE = "/sifen/techstore-fe-test.p12";
    private static final char[] CLAVE = "changeit".toCharArray();
    private static final String ALIAS = "techstore-fe";
    private static final String RSA_SHA256 = "http://www.w3.org/2001/04/xmldsig-more#rsa-sha256";

    private final PrivateKey privateKey;
    private final X509Certificate certificado;

    public FirmaDigitalService() {
        try (InputStream in = getClass().getResourceAsStream(RECURSO_KEYSTORE)) {
            if (in == null) {
                throw new IllegalStateException("No se encontró el keystore de prueba " + RECURSO_KEYSTORE);
            }
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(in, CLAVE);
            this.privateKey = (PrivateKey) ks.getKey(ALIAS, CLAVE);
            this.certificado = (X509Certificate) ks.getCertificate(ALIAS);
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo cargar el certificado de firma: " + ex.getMessage(), ex);
        }
    }

    /** Firma el XML del DE y devuelve el XML firmado (con el nodo Signature). */
    public String firmar(String xml, String cdc) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            Document doc = dbf.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

            // El elemento <DE Id="CDC"> es el firmado; hay que registrar su Id como tipo ID.
            Element de = (Element) doc.getElementsByTagNameNS("*", "DE").item(0);
            de.setIdAttribute("Id", true);

            XMLSignatureFactory fac = XMLSignatureFactory.getInstance("DOM");
            Reference ref = fac.newReference(
                    "#" + cdc,
                    fac.newDigestMethod(DigestMethod.SHA256, null),
                    List.of(
                            fac.newTransform(Transform.ENVELOPED, (TransformParameterSpec) null),
                            fac.newCanonicalizationMethod(CanonicalizationMethod.EXCLUSIVE, (C14NMethodParameterSpec) null)),
                    null, null);

            SignedInfo si = fac.newSignedInfo(
                    fac.newCanonicalizationMethod(CanonicalizationMethod.EXCLUSIVE, (C14NMethodParameterSpec) null),
                    fac.newSignatureMethod(RSA_SHA256, null),
                    List.of(ref));

            KeyInfoFactory kif = fac.getKeyInfoFactory();
            X509Data x509 = kif.newX509Data(List.of(certificado));
            KeyInfo ki = kif.newKeyInfo(List.of(x509));

            // La firma se agrega como hijo del elemento raíz <rDE>.
            DOMSignContext dsc = new DOMSignContext(privateKey, doc.getDocumentElement());
            XMLSignature signature = fac.newXMLSignature(si, ki);
            signature.sign(dsc);

            return serializar(doc);
        } catch (Exception ex) {
            throw new BusinessException("sifen.firma.fallo", ex.getMessage());
        }
    }

    private static String serializar(Document doc) throws Exception {
        Transformer t = TransformerFactory.newInstance().newTransformer();
        t.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        StringWriter sw = new StringWriter();
        t.transform(new DOMSource(doc), new StreamResult(sw));
        return sw.toString();
    }
}
