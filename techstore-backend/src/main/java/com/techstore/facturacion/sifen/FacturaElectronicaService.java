package com.techstore.facturacion.sifen;

import com.techstore.comun.ResourceNotFoundException;
import com.techstore.facturacion.Factura;
import com.techstore.facturacion.FacturaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orquesta la generación del Documento Electrónico de una factura:
 * arma el XML del DE, lo firma y produce el KuDE. Demostrativo del SIFEN.
 */
@Service
@RequiredArgsConstructor
public class FacturaElectronicaService {

    private final FacturaRepository facturaRepository;
    private final DocumentoElectronicoService documentoElectronicoService;
    private final FirmaDigitalService firmaDigitalService;
    private final KudeService kudeService;
    private final SifenProperties sifenProperties;

    public record XmlFirmado(String numero, String cdc, String xml) {
    }

    public record Kude(String numero, byte[] pdf) {
    }

    @Transactional(readOnly = true)
    public XmlFirmado generarXml(Long facturaId) {
        sifenProperties.exigirActivo();
        Factura factura = buscar(facturaId);
        DocumentoElectronicoService.DocumentoElectronico de = documentoElectronicoService.generar(factura);
        String firmado = firmaDigitalService.firmar(de.xml(), de.cdc());
        String completo = kudeService.completarXmlConQr(factura, de.cdc(), firmado);
        return new XmlFirmado(factura.getNumero(), de.cdc(), completo);
    }

    @Transactional(readOnly = true)
    public Kude generarKude(Long facturaId) {
        sifenProperties.exigirActivo();
        Factura factura = buscar(facturaId);
        DocumentoElectronicoService.DocumentoElectronico de = documentoElectronicoService.generar(factura);
        String firmado = firmaDigitalService.firmar(de.xml(), de.cdc());
        String completo = kudeService.completarXmlConQr(factura, de.cdc(), firmado);
        byte[] pdf = kudeService.generar(factura, de.cdc(), completo);
        return new Kude(factura.getNumero(), pdf);
    }

    private Factura buscar(Long facturaId) {
        return facturaRepository.findParaPdfById(facturaId)
                .orElseThrow(() -> new ResourceNotFoundException("facturacion.factura.no.encontrada", facturaId));
    }
}
