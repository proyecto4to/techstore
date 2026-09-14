package com.techstore.seguridad;

import com.techstore.auditoria.AuditoriaService;
import com.techstore.comun.BusinessException;
import com.techstore.comun.ResourceNotFoundException;
import com.techstore.usuarios.Rol;
import com.techstore.usuarios.RolRepository;
import com.techstore.usuarios.Usuario;
import com.techstore.usuarios.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Los permisos, guardados en la base y no escritos en el código.
 *
 * <p>Hay dos niveles y se combinan siempre igual, para todos los módulos:
 *
 * <ol>
 *   <li>la <b>matriz del rol</b>, que es lo que usa la mayoría;
 *   <li>la <b>excepción individual</b>, para cuando a una persona le falta o le
 *       sobra una acción suelta y no vale la pena inventarle un rol.
 * </ol>
 *
 * <p>Gana lo que niega. Una restricción individual cierra aunque el rol abra;
 * una concesión individual abre lo que el rol cerraba. Sin excepción manda el
 * rol, y si el rol tampoco tiene nada cargado queda permitido: el sistema
 * funcionaba sin estas tablas y su ausencia no puede dejar a nadie afuera.
 *
 * <p><b>Nada de esto amplía lo que el backend ya niega.</b> Las anotaciones
 * {@code @PreAuthorize} de cada controlador siguen siendo el piso y se
 * comprueban después de esta capa, así que tildar una casilla nunca abre un
 * endpoint cerrado; destildarla sí lo cierra. Se eligió así porque lo contrario
 * convertiría una pantalla de administración en una forma de saltearse las
 * reglas de negocio: que alguien pueda darse permiso de vaciar la caja desde
 * una grilla no es parametrizar, es un agujero.
 */
@Service
@RequiredArgsConstructor
public class PermisoService {

    private static final String PANTALLA_SEGURIDAD = "seguridad";

    private final PermisoRolRepository permisoRepository;
    private final PermisoUsuarioRepository permisoUsuarioRepository;
    private final AccesoPantallaRolRepository accesoRepository;
    private final RolRepository rolRepository;
    private final UsuarioRepository usuarioRepository;
    private final AdministradoresGuard administradores;
    private final AuditoriaService auditoria;

    // ------------------------------------------------------------------
    // Lo que pregunta el interceptor en cada pedido a la API
    // ------------------------------------------------------------------

    /** Si ese rol puede hacer esa acción sobre ese módulo. */
    @Transactional(readOnly = true)
    public boolean puede(String rolNombre, Modulo modulo, AccionPermiso accion) {
        if (rolNombre == null) {
            return false;
        }
        return permisoRepository
                .findByRolNombreAndModuloAndAccion(rolNombre, modulo, accion)
                .map(PermisoRol::getPermitido)
                .orElse(true);
    }

    /**
     * Si esa persona puede, mirando primero su excepción y después su rol.
     *
     * <p>Va por email porque es lo único que trae la sesión: resolver antes el
     * usuario para después buscar su permiso sería una consulta de más en cada
     * llamada a la API.
     */
    @Transactional(readOnly = true)
    public boolean puedeUsuario(String email, String rolNombre, Modulo modulo, AccionPermiso accion) {
        if (email != null) {
            Optional<PermisoUsuario> excepcion = permisoUsuarioRepository
                    .findByUsuarioEmailAndModuloAndAccion(email, modulo, accion);
            if (excepcion.isPresent()) {
                return Boolean.TRUE.equals(excepcion.get().getPermitido());
            }
        }
        return puede(rolNombre, modulo, accion);
    }

    /** Si ese rol tiene habilitada esa pantalla. */
    @Transactional(readOnly = true)
    public boolean veLaPantalla(String rolNombre, String pantalla) {
        if (rolNombre == null) {
            return false;
        }
        return accesoRepository
                .findByRolNombreAndPantalla(rolNombre, pantalla)
                .map(AccesoPantallaRol::getPermitido)
                .orElse(true);
    }

    // ------------------------------------------------------------------
    // La matriz del rol
    // ------------------------------------------------------------------

    /** La matriz completa de un rol, para la pantalla de Seguridad. */
    @Transactional(readOnly = true)
    public MatrizPermisosResponse matriz(String rolNombre) {
        Rol rol = rolPorNombre(rolNombre);
        List<PermisoRol> guardados = permisoRepository
                .findByRolNombreOrderByModuloAscAccionAsc(rol.getNombre());
        List<MatrizPermisosResponse.FilaModulo> filas = new ArrayList<>();
        for (Modulo modulo : Modulo.values()) {
            List<MatrizPermisosResponse.Casilla> casillas = new ArrayList<>();
            for (AccionPermiso accion : AccionPermiso.values()) {
                boolean permitido = guardados.stream()
                        .filter(p -> p.getModulo() == modulo && p.getAccion() == accion)
                        .findFirst()
                        .map(PermisoRol::getPermitido)
                        .orElse(true);
                casillas.add(new MatrizPermisosResponse.Casilla(
                        accion.name(), accion.etiqueta(), permitido));
            }
            filas.add(new MatrizPermisosResponse.FilaModulo(
                    modulo.name(), modulo.etiqueta(), casillas));
        }
        return new MatrizPermisosResponse(rol.getNombre(), filas, pantallas(rol.getNombre()));
    }

    private List<MatrizPermisosResponse.Pantalla> pantallas(String rolNombre) {
        return accesoRepository.findByRolNombreOrderByPantallaAsc(rolNombre).stream()
                .map(a -> new MatrizPermisosResponse.Pantalla(a.getPantalla(), a.getPermitido()))
                .toList();
    }

    /** Cambia una casilla de la matriz. */
    @Transactional
    public void guardarPermiso(String rolNombre, Modulo modulo, AccionPermiso accion, boolean permitido) {
        Rol rol = rolPorNombre(rolNombre);
        // Sin esto, un administrador distraído deja el sistema sin nadie que
        // pueda volver a repartir permisos, y de ahí no se sale por pantalla.
        if (!permitido && modulo == Modulo.SEGURIDAD && RolesDelSistema.esAdministrador(rol.getNombre())) {
            throw new BusinessException("seguridad.rol.admin.protegido");
        }
        PermisoRol permiso = permisoRepository
                .findByRolNombreAndModuloAndAccion(rol.getNombre(), modulo, accion)
                .orElseGet(() -> {
                    PermisoRol nuevo = new PermisoRol();
                    nuevo.setRol(rol);
                    nuevo.setModulo(modulo);
                    nuevo.setAccion(accion);
                    return nuevo;
                });
        boolean antes = Boolean.TRUE.equals(permiso.getPermitido());
        permiso.setPermitido(permitido);
        permisoRepository.save(permiso);

        auditoria.registrar("PermisoRol", rol.getId(), AuditoriaService.Accion.Editar,
                "%s: %s %s en %s".formatted(rol.getNombre(), permitido ? "habilitó" : "quitó",
                        accion.etiqueta().toLowerCase(Locale.ROOT), modulo.etiqueta()),
                AuditoriaService.Cambios.nuevo()
                        .de(clave(modulo, accion), antes, permitido));
    }

    /** Habilita o deshabilita una pantalla para un rol. */
    @Transactional
    public void guardarPantalla(String rolNombre, String pantalla, boolean permitido) {
        Rol rol = rolPorNombre(rolNombre);
        if (!permitido && PANTALLA_SEGURIDAD.equalsIgnoreCase(pantalla)
                && RolesDelSistema.esAdministrador(rol.getNombre())) {
            throw new BusinessException("seguridad.rol.admin.protegido");
        }
        AccesoPantallaRol acceso = accesoRepository
                .findByRolNombreAndPantalla(rol.getNombre(), pantalla)
                .orElseGet(() -> {
                    AccesoPantallaRol nuevo = new AccesoPantallaRol();
                    nuevo.setRol(rol);
                    nuevo.setPantalla(pantalla);
                    return nuevo;
                });
        boolean antes = Boolean.TRUE.equals(acceso.getPermitido());
        acceso.setPermitido(permitido);
        accesoRepository.save(acceso);

        auditoria.registrar("AccesoPantalla", rol.getId(), AuditoriaService.Accion.Editar,
                "%s: la pantalla %s queda %s".formatted(
                        rol.getNombre(), pantalla, permitido ? "visible" : "oculta"),
                AuditoriaService.Cambios.nuevo().de("pantalla." + pantalla, antes, permitido));
    }

    /** Los roles que se pueden configurar. */
    @Transactional(readOnly = true)
    public List<String> roles() {
        return rolRepository.findAll().stream().map(Rol::getNombre).sorted().toList();
    }

    // ------------------------------------------------------------------
    // Excepciones por persona y permisos efectivos
    // ------------------------------------------------------------------

    /**
     * Lo que esta persona puede hacer de verdad, con el origen de cada permiso.
     *
     * <p>El origen es la mitad de la respuesta: una tilde sin explicación no le
     * dice a quien administra si eso viene del rol —y entonces se toca el rol—
     * o si alguien se lo dio a esa persona en particular.
     */
    @Transactional(readOnly = true)
    public PermisosEfectivosResponse efectivos(Long usuarioId) {
        return efectivosDe(usuarioPorId(usuarioId));
    }

    /** Lo mismo, para quien pregunta por sí mismo. */
    @Transactional(readOnly = true)
    public PermisosEfectivosResponse mios(String email) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("usuarios.no.encontrado", email));
        return efectivosDe(usuario);
    }

    private PermisosEfectivosResponse efectivosDe(Usuario usuario) {
        String rolNombre = usuario.getRol().getNombre();
        List<PermisoRol> delRol = permisoRepository.findByRolNombreOrderByModuloAscAccionAsc(rolNombre);
        List<PermisoUsuario> excepciones =
                permisoUsuarioRepository.findByUsuarioIdOrderByModuloAscAccionAsc(usuario.getId());

        List<PermisosEfectivosResponse.FilaModulo> filas = new ArrayList<>();
        List<String> permitidos = new ArrayList<>();
        for (Modulo modulo : Modulo.values()) {
            List<PermisosEfectivosResponse.AccionEfectiva> acciones = new ArrayList<>();
            for (AccionPermiso accion : AccionPermiso.values()) {
                Optional<PermisoUsuario> excepcion = excepciones.stream()
                        .filter(p -> p.getModulo() == modulo && p.getAccion() == accion)
                        .findFirst();

                boolean permitido;
                OrigenPermiso origen;
                String motivo = null;
                if (excepcion.isPresent()) {
                    permitido = Boolean.TRUE.equals(excepcion.get().getPermitido());
                    origen = permitido ? OrigenPermiso.CONCEDIDO : OrigenPermiso.RESTRINGIDO;
                    motivo = excepcion.get().getMotivo();
                } else {
                    Optional<PermisoRol> configurado = delRol.stream()
                            .filter(p -> p.getModulo() == modulo && p.getAccion() == accion)
                            .findFirst();
                    permitido = configurado.map(PermisoRol::getPermitido).orElse(true);
                    origen = configurado.isPresent() ? OrigenPermiso.ROL : OrigenPermiso.PREDETERMINADO;
                }

                acciones.add(new PermisosEfectivosResponse.AccionEfectiva(
                        accion.name(), accion.etiqueta(), permitido,
                        origen.name(), origen.etiqueta(), motivo));
                if (permitido) {
                    permitidos.add(clave(modulo, accion));
                }
            }
            filas.add(new PermisosEfectivosResponse.FilaModulo(
                    modulo.name(), modulo.etiqueta(), acciones));
        }

        List<String> pantallasVisibles = accesoRepository
                .findByRolNombreAndPermitidoTrueOrderByPantallaAsc(rolNombre).stream()
                .map(AccesoPantallaRol::getPantalla)
                .toList();

        return new PermisosEfectivosResponse(
                usuario.getId(),
                usuario.getEmail(),
                (usuario.getNombre() + " " + usuario.getApellido()).trim(),
                rolNombre,
                usuario.getActivo(),
                filas,
                pantallasVisibles,
                permitidos);
    }

    /**
     * Concede o restringe una acción a una persona en particular.
     *
     * @param permitido {@code true} concede, {@code false} restringe
     */
    @Transactional
    public PermisosEfectivosResponse guardarPermisoUsuario(
            Long usuarioId, Modulo modulo, AccionPermiso accion,
            boolean permitido, String motivo, String emailSolicitante) {

        Usuario usuario = usuarioPorId(usuarioId);
        if (!permitido && modulo == Modulo.SEGURIDAD) {
            // Quitarse a uno mismo la llave estando adentro no tiene arreglo por
            // pantalla; dejar al sistema sin ningún administrador, tampoco.
            if (usuario.getEmail().equalsIgnoreCase(emailSolicitante)) {
                throw new BusinessException("seguridad.autorrestriccion");
            }
            administradores.verificarQueNoEsElUltimo(usuarioId, "restringirle el acceso a Seguridad");
        }

        PermisoUsuario permiso = permisoUsuarioRepository
                .findByUsuarioIdAndModuloAndAccion(usuarioId, modulo, accion)
                .orElseGet(() -> {
                    PermisoUsuario nuevo = new PermisoUsuario();
                    nuevo.setUsuario(usuario);
                    nuevo.setModulo(modulo);
                    nuevo.setAccion(accion);
                    return nuevo;
                });
        Boolean antes = permiso.getPermitido();
        permiso.setPermitido(permitido);
        permiso.setMotivo(motivo == null || motivo.isBlank() ? null : motivo.trim());
        permisoUsuarioRepository.save(permiso);

        auditoria.registrar("PermisoUsuario", usuarioId, AuditoriaService.Accion.Editar,
                "%s: %s %s en %s".formatted(usuario.getEmail(),
                        permitido ? "se le concedió" : "se le restringió",
                        accion.etiqueta().toLowerCase(Locale.ROOT), modulo.etiqueta()),
                AuditoriaService.Cambios.nuevo()
                        .de(clave(modulo, accion), textoDe(antes), permitido ? "concedido" : "restringido")
                        .valor("motivo", permiso.getMotivo()));

        return efectivosDe(usuario);
    }

    /** Le saca la excepción: vuelve a valer lo que diga su rol. */
    @Transactional
    public PermisosEfectivosResponse quitarPermisoUsuario(
            Long usuarioId, Modulo modulo, AccionPermiso accion) {

        Usuario usuario = usuarioPorId(usuarioId);
        permisoUsuarioRepository.findByUsuarioIdAndModuloAndAccion(usuarioId, modulo, accion)
                .ifPresent(permiso -> {
                    String tenia = Boolean.TRUE.equals(permiso.getPermitido())
                            ? "concedido" : "restringido";
                    permisoUsuarioRepository.delete(permiso);
                    auditoria.registrar("PermisoUsuario", usuarioId, AuditoriaService.Accion.Eliminar,
                            "%s: %s en %s vuelve a lo que diga el rol".formatted(
                                    usuario.getEmail(), accion.etiqueta().toLowerCase(Locale.ROOT),
                                    modulo.etiqueta()),
                            AuditoriaService.Cambios.nuevo()
                                    .de(clave(modulo, accion), tenia, "heredado"));
                });
        return efectivosDe(usuario);
    }

    // ------------------------------------------------------------------
    // Catálogo
    // ------------------------------------------------------------------

    /**
     * Qué módulos, acciones y pantallas existen.
     *
     * <p>Con esto se dibuja la pantalla de Seguridad. Sale del backend y no de
     * una lista escrita en el cliente para que agregar un módulo aparezca solo
     * en la Web y en el APK, sin publicar dos versiones nuevas.
     */
    @Transactional(readOnly = true)
    public CatalogoSeguridadResponse catalogo() {
        List<CatalogoSeguridadResponse.ModuloDisponible> modulos = new ArrayList<>();
        for (Modulo modulo : Modulo.values()) {
            modulos.add(new CatalogoSeguridadResponse.ModuloDisponible(modulo.name(), modulo.etiqueta()));
        }
        List<CatalogoSeguridadResponse.AccionDisponible> acciones = new ArrayList<>();
        for (AccionPermiso accion : AccionPermiso.values()) {
            acciones.add(new CatalogoSeguridadResponse.AccionDisponible(accion.name(), accion.etiqueta()));
        }
        List<CatalogoSeguridadResponse.RutaDeModulo> rutas = PermisoInterceptor.rutasPorModulo()
                .entrySet().stream()
                .map(entrada -> new CatalogoSeguridadResponse.RutaDeModulo(
                        entrada.getKey(), entrada.getValue().name()))
                .toList();
        return new CatalogoSeguridadResponse(
                modulos, acciones, accesoRepository.pantallasConocidas(), rutas);
    }

    // ------------------------------------------------------------------

    /** Cómo la nombra el cliente: {@code productos.ver}. */
    private static String clave(Modulo modulo, AccionPermiso accion) {
        return (modulo.name() + "." + accion.name()).toLowerCase(Locale.ROOT);
    }

    private static String textoDe(Boolean permitidoAnterior) {
        if (permitidoAnterior == null) {
            return "heredado";
        }
        return permitidoAnterior ? "concedido" : "restringido";
    }

    private Usuario usuarioPorId(Long usuarioId) {
        return usuarioRepository.findWithRolById(usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("usuarios.no.encontrado", usuarioId));
    }

    private Rol rolPorNombre(String nombre) {
        return rolRepository.findAll().stream()
                .filter(r -> r.getNombre().equalsIgnoreCase(nombre))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("seguridad.rol.no.encontrado", nombre));
    }
}
