import 'dart:async';
import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:latlong2/latlong.dart';

import '../../../core/di/providers.dart';
import '../../../core/errors/app_failure.dart';
import '../../../core/ubicacion/directorio_de_lugares.dart';
import '../../../core/ubicacion/ubicacion_service.dart';
import '../../../core/widgets/mapa_osm.dart';
import '../../../core/widgets/titulo_de_pantalla.dart';
import '../../auth/application/session_controller.dart';
import '../application/checkout_providers.dart';
import '../domain/checkout_models.dart';

/// Dónde abrir el mapa cuando todavía no hay nada marcado.
///
/// Asunción a la vista: es el centro de Asunción. Abrirlo en el punto cero del
/// Atlántico no le sirve a nadie para orientarse.
const _centroPorDefecto = LatLng(-25.2637, -57.5759);

/// Cuánto esperar antes de buscar lo que se está escribiendo.
///
/// El servicio de direcciones pide no pasar de una consulta por segundo. Sin
/// esta pausa se manda una por tecla y terminan rechazándolas todas.
const _pausaDelBuscador = Duration(milliseconds: 600);

/// Marcar una dirección de entrega en el mapa.
///
/// Reemplaza al formulario de doce campos que había antes, en el que dos eran
/// «Latitud» y «Longitud» escritas a mano. Acá el punto se elige en el mapa y
/// el texto de la calle se deduce de ese punto: lo único que queda por escribir
/// es lo que ningún mapa sabe —a quién entregarle, a qué teléfono llamar, cómo
/// le dice la persona a ese lugar y qué referencia dar para encontrarlo—.
class AddressMapPage extends ConsumerStatefulWidget {
  const AddressMapPage({super.key, this.direccion});

  /// La dirección que se está editando, o nulo si es nueva.
  final DeliveryAddress? direccion;

  @override
  ConsumerState<AddressMapPage> createState() => _AddressMapPageState();
}

class _AddressMapPageState extends ConsumerState<AddressMapPage> {
  final _mapa = MapController();
  final _form = GlobalKey<FormState>();
  final _buscador = TextEditingController();
  late final TextEditingController _descripcion;
  late final TextEditingController _referencia;
  late final TextEditingController _destinatario;
  late final TextEditingController _telefono;

  late LatLng _punto;
  late bool _principal;
  DireccionDetectada _detectada = const DireccionDetectada();
  List<LugarEncontrado> _resultados = const [];
  Timer? _esperaDelBuscador;

  /// Si el mapa ya se dibujó una vez.
  ///
  /// El controlador de flutter_map lanza excepción si se le pide mover la
  /// cámara antes del primer dibujo. Con la ubicación en caché, el teléfono la
  /// contesta al instante y eso llega antes que el primer cuadro.
  bool _mapaListo = false;
  bool _detectando = false;
  bool _ubicando = false;
  bool _guardando = false;

  bool get _esNueva => widget.direccion == null;

  @override
  void initState() {
    super.initState();
    final direccion = widget.direccion;
    _punto =
        direccion != null &&
            direccion.latitude != null &&
            direccion.longitude != null
        ? LatLng(direccion.latitude!, direccion.longitude!)
        : _centroPorDefecto;
    _principal = direccion?.primary ?? false;
    _descripcion = TextEditingController(text: direccion?.description ?? '');
    _referencia = TextEditingController(text: direccion?.reference ?? '');
    // El nombre de quien está usando la app es la respuesta correcta casi
    // siempre; que venga escrito ahorra el campo sin quitarle la posibilidad
    // de mandarle el pedido a otra persona.
    _destinatario = TextEditingController(
      text:
          direccion?.recipient ??
          ref.read(authSessionProvider).user?.fullName ??
          '',
    );
    _telefono = TextEditingController(text: direccion?.phone ?? '');
    if (direccion != null) {
      _detectada = DireccionDetectada(
        departamento: direccion.department,
        ciudad: direccion.city,
        barrio: direccion.neighborhood,
        calle: direccion.line1,
        numero: direccion.houseNumber,
        codigoPostal: direccion.postalCode,
      );
    }
    if (_esNueva) unawaited(_ubicarme(automatico: true));
  }

  @override
  void dispose() {
    _esperaDelBuscador?.cancel();
    _mapa.dispose();
    _buscador.dispose();
    _descripcion.dispose();
    _referencia.dispose();
    _destinatario.dispose();
    _telefono.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: TituloDePantalla(
          _esNueva ? 'Nueva dirección' : 'Editar dirección',
        ),
      ),
      body: SafeArea(
        top: false,
        child: LayoutBuilder(
          builder: (context, restricciones) {
            // Con la pantalla ancha —una tablet, o el teléfono acostado— el
            // mapa va al lado del formulario: apilarlos dejaría el mapa en una
            // franja donde no se puede marcar nada.
            final lado = restricciones.maxWidth >= 720;
            final mapa = _Mapa(
              controlador: _mapa,
              punto: _punto,
              ubicando: _ubicando,
              onTocarMapa: _mover,
              onUbicarme: () => unawaited(_ubicarme()),
              onListo: () {
                _mapaListo = true;
                _mapa.move(_punto, 17);
              },
            );
            // El buscador va por encima de todo y no dentro del mapa: con el
            // teclado abierto, el mapa queda en una franja de pocos puntos y la
            // lista de resultados no tenía dónde caber. Así puede taparle un
            // rato al formulario, que es lo que hace cualquier buscador.
            final buscador = Positioned(
              top: 8,
              left: 8,
              right: 8,
              bottom: 8,
              child: Align(
                alignment: Alignment.topCenter,
                child: _Buscador(
                  controlador: _buscador,
                  resultados: _resultados,
                  onEscribir: _buscarConPausa,
                  onElegir: _irAlLugar,
                ),
              ),
            );
            final formulario = _Formulario(
              form: _form,
              punto: _punto,
              detectada: _detectada,
              detectando: _detectando,
              descripcion: _descripcion,
              referencia: _referencia,
              destinatario: _destinatario,
              telefono: _telefono,
              principal: _principal,
              guardando: _guardando,
              onPrincipal: (valor) => setState(() => _principal = valor),
              onGuardar: _guardar,
            );
            if (lado) {
              return Row(
                children: [
                  Expanded(child: Stack(children: [mapa, buscador])),
                  SizedBox(width: 380, child: formulario),
                ],
              );
            }
            // Un mínimo de 220: con el teléfono acostado, el 42% son unos
            // 145 puntos y el buscador solo ya se come casi todo el mapa. El
            // tope del 60% se aplica último, para que en una pantalla muy baja
            // el mínimo no termine dejando al formulario sin lugar.
            final alto = math.min(
              math.max(220.0, restricciones.maxHeight * .42),
              restricciones.maxHeight * .6,
            );
            return Stack(
              children: [
                Column(
                  children: [
                    SizedBox(height: alto, child: mapa),
                    Expanded(child: formulario),
                  ],
                ),
                buscador,
              ],
            );
          },
        ),
      ),
    );
  }

  /// Mueve el marcador y vuelve a deducir la dirección del punto nuevo.
  void _mover(LatLng punto) {
    setState(() {
      _punto = punto;
      _resultados = const [];
    });
    unawaited(_describir(punto));
  }

  Future<void> _describir(LatLng punto) async {
    setState(() => _detectando = true);
    final detectada = await ref
        .read(directorioDeLugaresProvider)
        .describir(punto);
    if (!mounted) return;
    setState(() {
      _detectada = detectada;
      _detectando = false;
    });
  }

  void _buscarConPausa(String consulta) {
    _esperaDelBuscador?.cancel();
    if (consulta.trim().length < 3) {
      setState(() => _resultados = const []);
      return;
    }
    _esperaDelBuscador = Timer(_pausaDelBuscador, () async {
      final encontrados = await ref
          .read(directorioDeLugaresProvider)
          .buscar(consulta);
      if (!mounted) return;
      setState(() => _resultados = encontrados);
    });
  }

  void _irAlLugar(LugarEncontrado lugar) {
    FocusScope.of(context).unfocus();
    _buscador.clear();
    _centrar(lugar.punto);
    _mover(lugar.punto);
  }

  /// Lleva la cámara al punto, si el mapa ya está en pantalla.
  ///
  /// Cuando todavía no lo está no hace falta: al terminar de dibujarse se
  /// centra solo en el punto que haya quedado elegido.
  void _centrar(LatLng punto) {
    if (_mapaListo) _mapa.move(punto, 17);
  }

  /// Lleva el mapa a donde está el teléfono.
  ///
  /// [automatico] es el intento que se hace solo al abrir una dirección nueva.
  /// Ese no muestra ningún cartel si falla: la persona no lo pidió, y el mapa
  /// igual quedó donde se puede marcar a mano.
  Future<void> _ubicarme({bool automatico = false}) async {
    setState(() => _ubicando = true);
    final resultado = await ref.read(ubicacionServiceProvider).donde();
    if (!mounted) return;
    setState(() => _ubicando = false);
    switch (resultado) {
      case UbicacionObtenida(:final punto):
        _centrar(punto);
        _mover(punto);
      case UbicacionNoDisponible(:final motivo, :final salida):
        if (automatico) return;
        _avisar(motivo, salida);
    }
  }

  void _avisar(String motivo, SalidaSugerida salida) {
    final servicio = ref.read(ubicacionServiceProvider);
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(
        SnackBar(
          content: Text('$motivo Podés marcarla tocando el mapa.'),
          duration: const Duration(seconds: 6),
          // Fijo solo con lector de pantalla; si no, se va solo.
          persist: MediaQuery.accessibleNavigationOf(context),
          action: salida == SalidaSugerida.reintentar
              ? SnackBarAction(
                  label: 'Reintentar',
                  onPressed: () => unawaited(_ubicarme()),
                )
              : SnackBarAction(
                  label: 'Abrir ajustes',
                  onPressed: () => unawaited(servicio.abrirAjustes(salida)),
                ),
        ),
      );
  }

  Future<void> _guardar() async {
    if (!_form.currentState!.validate()) return;
    setState(() => _guardando = true);
    final datos = <String, Object?>{
      'descripcion': _vacioANull(_descripcion.text),
      'nombreDestinatario': _destinatario.text.trim(),
      'telefono': _telefono.text.trim(),
      // El texto de la calle sale del punto, no de lo que alguien escriba.
      'departamento': _detectada.departamento,
      'ciudad': _detectada.ciudad,
      'barrio': _detectada.barrio,
      'direccionLinea1': _detectada.calle,
      'numeroCasa': _detectada.numero,
      'codigoPostal': _detectada.codigoPostal,
      'referencia': _vacioANull(_referencia.text),
      'latitud': _punto.latitude,
      'longitud': _punto.longitude,
      'principal': _principal,
    };
    try {
      final repositorio = ref.read(checkoutRepositoryProvider);
      if (_esNueva) {
        await repositorio.createAddress(datos);
      } else {
        await repositorio.updateAddress(widget.direccion!.id, datos);
      }
      if (!mounted) return;
      Navigator.pop(context, true);
    } on AppFailure catch (falla) {
      if (!mounted) return;
      setState(() => _guardando = false);
      ScaffoldMessenger.of(context)
        ..hideCurrentSnackBar()
        ..showSnackBar(SnackBar(content: Text(falla.message)));
    }
  }

  static String? _vacioANull(String valor) {
    final limpio = valor.trim();
    return limpio.isEmpty ? null : limpio;
  }
}

/// El mapa con su buscador y sus controles.
class _Mapa extends StatelessWidget {
  const _Mapa({
    required this.controlador,
    required this.punto,
    required this.ubicando,
    required this.onTocarMapa,
    required this.onUbicarme,
    required this.onListo,
  });

  final MapController controlador;
  final LatLng punto;
  final bool ubicando;
  final ValueChanged<LatLng> onTocarMapa;
  final VoidCallback onUbicarme;

  /// Avisa cuando el mapa ya está dibujado y acepta que le muevan la cámara.
  final VoidCallback onListo;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Stack(
      children: [
        FlutterMap(
          mapController: controlador,
          options: MapOptions(
            initialCenter: punto,
            initialZoom: 16,
            minZoom: 3,
            maxZoom: 19,
            onTap: (_, tocado) => onTocarMapa(tocado),
            onMapReady: onListo,
          ),
          children: [
            capaDeMapa(),
            MarkerLayer(
              markers: [
                marcadorDePunto(punto: punto, color: theme.colorScheme.error),
              ],
            ),
          ],
        ),
        Positioned(
          right: 12,
          bottom: 12,
          child: Column(
            children: [
              FloatingActionButton.small(
                heroTag: 'ubicarme',
                tooltip: 'Usar mi ubicación',
                onPressed: ubicando ? null : onUbicarme,
                child: ubicando
                    ? const SizedBox(
                        width: 18,
                        height: 18,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : const Icon(Icons.my_location),
              ),
              const SizedBox(height: 8),
              FloatingActionButton.small(
                heroTag: 'acercar',
                tooltip: 'Acercar',
                onPressed: () => controlador.move(
                  controlador.camera.center,
                  controlador.camera.zoom + 1,
                ),
                child: const Icon(Icons.add),
              ),
              const SizedBox(height: 8),
              FloatingActionButton.small(
                heroTag: 'alejar',
                tooltip: 'Alejar',
                onPressed: () => controlador.move(
                  controlador.camera.center,
                  controlador.camera.zoom - 1,
                ),
                child: const Icon(Icons.remove),
              ),
            ],
          ),
        ),
      ],
    );
  }
}

class _Buscador extends StatelessWidget {
  const _Buscador({
    required this.controlador,
    required this.resultados,
    required this.onEscribir,
    required this.onElegir,
  });

  final TextEditingController controlador;
  final List<LugarEncontrado> resultados;
  final ValueChanged<String> onEscribir;
  final ValueChanged<LugarEncontrado> onElegir;

  @override
  Widget build(BuildContext context) {
    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Material(
          elevation: 3,
          borderRadius: BorderRadius.circular(28),
          child: TextField(
            controller: controlador,
            textInputAction: TextInputAction.search,
            onChanged: onEscribir,
            decoration: const InputDecoration(
              hintText: 'Buscar una calle o un lugar',
              prefixIcon: Icon(Icons.search),
              border: InputBorder.none,
              contentPadding: EdgeInsets.symmetric(
                horizontal: 16,
                vertical: 14,
              ),
            ),
          ),
        ),
        if (resultados.isNotEmpty)
          // Flexible y no un alto fijo: la lista se queda con lo que sobra del
          // mapa y se desplaza por dentro, en vez de desbordar y que la corten.
          Flexible(
            child: Padding(
              padding: const EdgeInsets.only(top: 6),
              child: Material(
                elevation: 3,
                borderRadius: BorderRadius.circular(12),
                clipBehavior: Clip.antiAlias,
                child: ListView.separated(
                  shrinkWrap: true,
                  itemCount: resultados.length,
                  separatorBuilder: (context, index) =>
                      const Divider(height: 1),
                  itemBuilder: (context, indice) {
                    final lugar = resultados[indice];
                    return ListTile(
                      dense: true,
                      leading: const Icon(Icons.place_outlined),
                      title: Text(lugar.nombre, maxLines: 2),
                      onTap: () => onElegir(lugar),
                    );
                  },
                ),
              ),
            ),
          ),
      ],
    );
  }
}

/// Lo que hay que escribir, que es lo que el mapa no puede saber.
class _Formulario extends StatelessWidget {
  const _Formulario({
    required this.form,
    required this.punto,
    required this.detectada,
    required this.detectando,
    required this.descripcion,
    required this.referencia,
    required this.destinatario,
    required this.telefono,
    required this.principal,
    required this.guardando,
    required this.onPrincipal,
    required this.onGuardar,
  });

  final GlobalKey<FormState> form;
  final LatLng punto;
  final DireccionDetectada detectada;
  final bool detectando;
  final TextEditingController descripcion;
  final TextEditingController referencia;
  final TextEditingController destinatario;
  final TextEditingController telefono;
  final bool principal;
  final bool guardando;
  final ValueChanged<bool> onPrincipal;
  final Future<void> Function() onGuardar;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Form(
      key: form,
      autovalidateMode: AutovalidateMode.onUserInteraction,
      child: ListView(
        padding: const EdgeInsets.fromLTRB(16, 12, 16, 24),
        children: [
          _DireccionDetectada(
            detectada: detectada,
            detectando: detectando,
            punto: punto,
          ),
          const SizedBox(height: 16),
          TextFormField(
            controller: descripcion,
            maxLength: 60,
            textCapitalization: TextCapitalization.sentences,
            decoration: const InputDecoration(
              labelText: 'Cómo le decís a este lugar',
              helperText: 'Así lo vas a reconocer en la lista',
              counterText: '',
              prefixIcon: Icon(Icons.label_outline),
            ),
          ),
          const SizedBox(height: 8),
          Wrap(
            spacing: 8,
            children: [
              for (final sugerencia in const ['Casa', 'Trabajo', 'Depósito'])
                ActionChip(
                  label: Text(sugerencia),
                  onPressed: () => descripcion.text = sugerencia,
                ),
            ],
          ),
          const SizedBox(height: 16),
          TextFormField(
            controller: referencia,
            maxLength: 300,
            maxLines: 2,
            textCapitalization: TextCapitalization.sentences,
            decoration: const InputDecoration(
              labelText: 'Referencia para encontrarlo (opcional)',
              helperText: 'Por ejemplo: portón negro, al lado de la despensa',
              counterText: '',
              alignLabelWithHint: true,
            ),
          ),
          const SizedBox(height: 16),
          Text('A quién le entregamos', style: theme.textTheme.titleSmall),
          const SizedBox(height: 8),
          TextFormField(
            controller: destinatario,
            maxLength: 120,
            textCapitalization: TextCapitalization.words,
            decoration: const InputDecoration(
              labelText: 'Nombre de quien recibe',
              counterText: '',
              prefixIcon: Icon(Icons.person_outline),
            ),
            validator: (valor) => (valor ?? '').trim().isEmpty
                ? 'Escribí a quién le entregamos'
                : null,
          ),
          const SizedBox(height: 12),
          TextFormField(
            controller: telefono,
            maxLength: 30,
            keyboardType: TextInputType.phone,
            decoration: const InputDecoration(
              labelText: 'Teléfono de contacto',
              helperText: 'Para avisarte cuando el pedido esté por llegar',
              counterText: '',
              prefixIcon: Icon(Icons.phone_outlined),
            ),
            validator: (valor) => (valor ?? '').trim().isEmpty
                ? 'Escribí un teléfono para el reparto'
                : null,
          ),
          const SizedBox(height: 8),
          SwitchListTile(
            contentPadding: EdgeInsets.zero,
            value: principal,
            onChanged: onPrincipal,
            title: const Text('Usar como dirección principal'),
            subtitle: const Text('Va a venir elegida al comprar'),
          ),
          const SizedBox(height: 8),
          FilledButton.icon(
            onPressed: guardando ? null : () => onGuardar(),
            icon: guardando
                ? const SizedBox(
                    width: 18,
                    height: 18,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Icon(Icons.check),
            label: Text(guardando ? 'Guardando…' : 'Guardar dirección'),
          ),
          const SizedBox(height: 12),
          const CreditoDelMapa(),
        ],
      ),
    );
  }
}

/// La dirección que se dedujo del punto marcado.
class _DireccionDetectada extends StatelessWidget {
  const _DireccionDetectada({
    required this.detectada,
    required this.detectando,
    required this.punto,
  });

  final DireccionDetectada detectada;
  final bool detectando;
  final LatLng punto;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final partes = [
      if (detectada.linea != null) detectada.linea!,
      if (detectada.barrio != null) detectada.barrio!,
      if (detectada.ciudad != null) detectada.ciudad!,
      if (detectada.departamento != null) detectada.departamento!,
    ];
    return Card(
      margin: EdgeInsets.zero,
      color: theme.colorScheme.surfaceContainerHighest,
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            ExcludeSemantics(
              child: Icon(
                Icons.place_outlined,
                color: theme.colorScheme.onSurfaceVariant,
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'Punto marcado en el mapa',
                    style: theme.textTheme.labelMedium?.copyWith(
                      color: theme.colorScheme.onSurfaceVariant,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    detectando
                        ? 'Buscando la dirección…'
                        : partes.isEmpty
                        ? 'Sin nombre de calle por acá. La ubicación queda '
                              'igual: el reparto va por el mapa.'
                        : partes.join(', '),
                    style: theme.textTheme.bodyMedium,
                  ),
                  const SizedBox(height: 6),
                  Text(
                    'Lat ${punto.latitude.toStringAsFixed(6)}   '
                    'Lon ${punto.longitude.toStringAsFixed(6)}',
                    style: theme.textTheme.bodySmall?.copyWith(
                      color: theme.colorScheme.onSurfaceVariant,
                      fontFeatures: const [FontFeature.tabularFigures()],
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
