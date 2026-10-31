package com.techstore.ventas;

import com.techstore.auditoria.AuditoriaService;
import com.techstore.comun.BusinessException;
import com.techstore.comun.ResourceNotFoundException;
import com.techstore.empresa.Configuracion;
import com.techstore.empresa.ConfiguracionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.UUID;

/**
 * Los datos bancarios del comercio para cobrar por transferencia.
 *
 * <p>Viven en la tabla {@code configuracion}, como la ubicación de la empresa:
 * los edita la administración desde la app y no hay que reiniciar nada.
 *
 * <p>El QR no se genera acá. En Paraguay cada banco arma el suyo dentro de su
 * propia aplicación, y un QR inventado con el número de cuenta no lo lee
 * ninguna. Por eso se guarda la imagen que la tienda sacó de su banco, tal cual.
 */
@Service
@RequiredArgsConstructor
public class DatosTransferenciaService {

    private static final String PREFIJO = "pago.transferencia.";
    private static final String CLAVE_QR = "qr-archivo";
    private static final String CLAVE_QR_TIPO = "qr-tipo";
    private static final long MAXIMO_QR_BYTES = 2L * 1024 * 1024;
    private static final Map<String, String> EXTENSIONES = Map.of(
            "image/png", ".png",
            "image/jpeg", ".jpg");

    private final ConfiguracionRepository repo;
    private final AuditoriaService auditoriaService;

    @Value("${app.uploads.dir}")
    private String uploadsDir;

    @Transactional(readOnly = true)
    public DatosTransferenciaResponse actuales() {
        String banco = valor("banco");
        String titular = valor("titular");
        String cuenta = valor("numero-cuenta");
        String alias = valor("alias");
        boolean disponible = banco != null && titular != null && (cuenta != null || alias != null);
        return new DatosTransferenciaResponse(
                disponible, banco, titular, valor("documento"), cuenta,
                valor("tipo-cuenta"), alias, valor("instrucciones"),
                valor(CLAVE_QR) != null);
    }

    /** Si se puede ofrecer la transferencia ahora mismo. */
    @Transactional(readOnly = true)
    public boolean disponible() {
        return actuales().disponible();
    }

    @Transactional
    public DatosTransferenciaResponse guardar(DatosTransferenciaRequest req) {
        DatosTransferenciaResponse anterior = actuales();
        set("banco", req.banco());
        set("titular", req.titular());
        set("documento", req.documento());
        set("numero-cuenta", req.numeroCuenta());
        set("tipo-cuenta", req.tipoCuenta());
        set("alias", req.alias());
        set("instrucciones", req.instrucciones());
        DatosTransferenciaResponse nuevo = actuales();
        // A dónde va la plata de los clientes es lo primero que alguien cambiaría
        // para desviarla: el cambio queda con el antes y el después.
        auditoriaService.registrar("Empresa", null, AuditoriaService.Accion.Editar,
                "Cambió los datos para cobrar por transferencia",
                AuditoriaService.Cambios.nuevo()
                        .de("banco", anterior.banco(), nuevo.banco())
                        .de("titular", anterior.titular(), nuevo.titular())
                        .de("documento", anterior.documento(), nuevo.documento())
                        .de("numeroCuenta", anterior.numeroCuenta(), nuevo.numeroCuenta())
                        .de("tipoCuenta", anterior.tipoCuenta(), nuevo.tipoCuenta())
                        .de("alias", anterior.alias(), nuevo.alias()));
        return nuevo;
    }

    @Transactional
    public DatosTransferenciaResponse guardarQr(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new BusinessException("pagos.qr.vacio");
        }
        if (archivo.getSize() > MAXIMO_QR_BYTES) {
            throw new BusinessException("pagos.qr.tamano");
        }
        String extension = EXTENSIONES.get(archivo.getContentType());
        if (extension == null) {
            throw new BusinessException("pagos.qr.formato");
        }
        try {
            byte[] contenido = archivo.getBytes();
            if (!FirmaDeArchivo.coincide(archivo.getContentType(), contenido)) {
                throw new BusinessException("pagos.qr.formato");
            }
            Path carpeta = carpeta();
            Files.createDirectories(carpeta);
            String nombre = "qr-transferencia-" + UUID.randomUUID() + extension;
            Files.write(carpeta.resolve(nombre), contenido, StandardOpenOption.CREATE_NEW);
            String anterior = valor(CLAVE_QR);
            set(CLAVE_QR, nombre);
            set(CLAVE_QR_TIPO, archivo.getContentType());
            borrar(anterior);
            auditoriaService.registrar("Empresa", null, AuditoriaService.Accion.Editar,
                    "Cambió el QR para cobrar por transferencia",
                    AuditoriaService.Cambios.nuevo().de("qr", anterior, nombre));
            return actuales();
        } catch (IOException error) {
            throw new BusinessException("pagos.qr.guardado");
        }
    }

    @Transactional
    public DatosTransferenciaResponse quitarQr() {
        String anterior = valor(CLAVE_QR);
        set(CLAVE_QR, null);
        set(CLAVE_QR_TIPO, null);
        borrar(anterior);
        if (anterior != null) {
            auditoriaService.registrar("Empresa", null, AuditoriaService.Accion.Editar,
                    "Quitó el QR para cobrar por transferencia",
                    AuditoriaService.Cambios.nuevo().de("qr", anterior, null));
        }
        return actuales();
    }

    @Transactional(readOnly = true)
    public PagoComprobanteService.ArchivoAdjunto qr() {
        String nombre = valor(CLAVE_QR);
        if (nombre == null) {
            throw new ResourceNotFoundException("pagos.qr.no.cargado");
        }
        Path archivo = carpeta().resolve(nombre).normalize();
        if (!archivo.startsWith(carpeta())) {
            throw new ResourceNotFoundException("pagos.qr.no.cargado");
        }
        try {
            String tipo = valor(CLAVE_QR_TIPO);
            return new PagoComprobanteService.ArchivoAdjunto(
                    Files.readAllBytes(archivo), tipo == null ? "image/png" : tipo);
        } catch (IOException error) {
            throw new ResourceNotFoundException("pagos.qr.no.cargado");
        }
    }

    private Path carpeta() {
        return Paths.get(uploadsDir, "pagos").toAbsolutePath().normalize();
    }

    private void borrar(String nombre) {
        if (nombre == null) {
            return;
        }
        Path candidato = carpeta().resolve(nombre).normalize();
        if (!candidato.startsWith(carpeta())) {
            return;
        }
        try {
            Files.deleteIfExists(candidato);
        } catch (IOException ignorado) {
            // El valor ya no apunta al archivo; uno huérfano no molesta a nadie.
        }
    }

    private String valor(String clave) {
        return repo.findById(PREFIJO + clave)
                .map(Configuracion::getValor)
                .filter(v -> v != null && !v.isBlank())
                .map(String::trim)
                .orElse(null);
    }

    private void set(String clave, String valor) {
        String id = PREFIJO + clave;
        Configuracion c = repo.findById(id).orElseGet(() -> new Configuracion(id, null));
        c.setValor(valor == null || valor.isBlank() ? null : valor.trim());
        repo.save(c);
    }
}
