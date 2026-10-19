import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart' show ScrollCacheExtent;
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/animacion/aparicion.dart';
import '../../../core/diseno/marca_tema.dart';
import '../../../core/diseno/medidas.dart';
import '../../../core/diseno/movimiento.dart';
import '../../../core/diseno/paleta.dart';
import '../../../core/diseno/tipografia.dart';
import '../../../core/widgets/async_states.dart';
import '../../../core/widgets/marca/rotulo.dart';
import '../../../core/widgets/voice_dictation_button.dart';
import '../../cart/presentation/boton_carrito.dart';
import '../application/catalog_providers.dart';
import '../domain/product.dart';
import 'vitrina/estilo_de_coleccion.dart';
import 'vitrina/foto_de_producto.dart';
import 'vitrina/motor_de_presentacion.dart';
import 'vitrina/tarjetas_de_producto.dart';

/// El catálogo como vidriera: sin filtros es una composición editorial; con
/// una búsqueda o una colección elegida pasa a una grilla pareja, que es lo
/// que sirve para comparar.
class CatalogPage extends ConsumerStatefulWidget {
  const CatalogPage({this.marcaInicial, super.key});

  /// La colección con la que se abre (desde la vidriera del inicio).
  final int? marcaInicial;

  @override
  ConsumerState<CatalogPage> createState() => _CatalogPageState();
}

class _CatalogPageState extends ConsumerState<CatalogPage> {
  final _buscador = TextEditingController();
  String _busqueda = '';
  int? _marca;
  OrdenDeCatalogo _orden = OrdenDeCatalogo.novedades;
  ScaffoldMessengerState? _mensajero;

  @override
  void initState() {
    super.initState();
    _marca = widget.marcaInicial;
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    // Se guarda acá porque en dispose() ya no se puede buscar por el árbol.
    _mensajero = ScaffoldMessenger.of(context);
  }

  @override
  void dispose() {
    // El aviso «Agregado al carrito» lo emite el ScaffoldMessenger de la app,
    // que vive por encima del Navigator: al salir del catálogo seguía pegado
    // abajo en las pantallas siguientes, tapando lo que hubiera ahí. Muere con
    // la pantalla que lo mostró.
    _mensajero?.hideCurrentSnackBar();
    _buscador.dispose();
    super.dispose();
  }

  ConsultaOrdenada get _consulta =>
      (search: _busqueda, brandId: _marca, orden: _orden);

  bool get _filtrado => _busqueda.isNotEmpty || _marca != null;

  void _buscar(String valor) {
    final limpio = valor.trim();
    if (limpio == _busqueda) return;
    setState(() => _busqueda = limpio);
  }

  void _limpiar() {
    _buscador.clear();
    setState(() {
      _busqueda = '';
      _marca = null;
    });
  }

  @override
  Widget build(BuildContext context) {
    final catalogo = ref.watch(catalogoOrdenadoProvider(_consulta));
    // Las fotos de lo que todavía no se ve se bajan en los ratos libres.
    ref.listen(catalogoOrdenadoProvider(_consulta), (_, siguiente) {
      final productos = siguiente.value;
      if (productos != null) precalentarFotos(context, productos);
    });
    final marcas = ref.watch(brandsProvider).value ?? const [];
    final cargador = ref.read(catalogoOrdenadoProvider(_consulta).notifier);
    final nombreMarca = _nombreDeMarca(marcas, _marca);

    return Scaffold(
      appBar: AppBar(
        title: const Text('Catálogo'),
        actions: const [BotonCarrito(), SizedBox(width: 6)],
      ),
      body: RefreshIndicator(
        onRefresh: () => ref.refresh(catalogoOrdenadoProvider(_consulta).future),
        child: CustomScrollView(
          slivers: [
            SliverToBoxAdapter(
              child: Padding(
                padding: const EdgeInsets.fromLTRB(
                  Medidas.margen,
                  4,
                  Medidas.margen,
                  14,
                ),
                child: SearchBar(
                  controller: _buscador,
                  hintText: 'Buscar productos',
                  leading: const Icon(Icons.search_rounded),
                  onSubmitted: _buscar,
                  textInputAction: TextInputAction.search,
                  trailing: [
                    if (_busqueda.isNotEmpty)
                      IconButton(
                        tooltip: 'Limpiar búsqueda',
                        onPressed: () {
                          _buscador.clear();
                          _buscar('');
                        },
                        icon: const Icon(Icons.close_rounded),
                      ),
                    VoiceDictationButton(
                      controller: _buscador,
                      tooltip: 'Buscar por voz',
                      onFinalResult: _buscar,
                    ),
                  ],
                ),
              ),
            ),
            if (marcas.isNotEmpty)
              SliverToBoxAdapter(
                child: _CarrilDeColecciones(
                  marcas: marcas,
                  elegida: _marca,
                  onElegir: (id) => setState(() => _marca = id),
                ),
              ),
            SliverToBoxAdapter(
              child: AnimatedSwitcher(
                duration: Movimiento.o(context, Movimiento.medio),
                switchInCurve: Movimiento.llegada,
                transitionBuilder: (hijo, animacion) => FadeTransition(
                  opacity: animacion,
                  child: SizeTransition(sizeFactor: animacion, child: hijo),
                ),
                child: nombreMarca == null
                    ? const SizedBox(key: ValueKey('sin-coleccion'))
                    : _EncabezadoDeColeccion(
                        key: ValueKey(nombreMarca),
                        nombre: nombreMarca,
                      ),
              ),
            ),
            SliverToBoxAdapter(
              child: _BarraDeOrden(
                orden: _orden,
                cantidad: catalogo.value != null && !cargador.hayMas
                    ? catalogo.value!.length
                    : null,
                filtrado: _filtrado,
                onOrden: (orden) => setState(() => _orden = orden),
                onLimpiar: _limpiar,
              ),
            ),
            ...catalogo.when(
              skipLoadingOnRefresh: true,
              loading: () => const [
                SliverFillRemaining(
                  hasScrollBody: false,
                  child: AppLoading(label: 'Cargando catálogo'),
                ),
              ],
              error: (error, _) => [
                SliverFillRemaining(
                  hasScrollBody: false,
                  child: AppErrorState(
                    message:
                        'No pudimos cargar el catálogo. '
                        'Revisá tu conexión e intentá de nuevo.',
                    onRetry: () =>
                        ref.invalidate(catalogoOrdenadoProvider(_consulta)),
                  ),
                ),
              ],
              data: (productos) => productos.isEmpty
                  ? [
                      SliverFillRemaining(
                        hasScrollBody: false,
                        child: AppEmptyState(
                          icon: _filtrado
                              ? Icons.search_off_rounded
                              : Icons.inventory_2_outlined,
                          title: _filtrado ? 'Nada por acá' : null,
                          message: _filtrado
                              ? 'No encontramos productos con esos filtros.'
                              : 'Todavía no hay productos publicados.',
                          accion: _filtrado ? 'Limpiar filtros' : null,
                          onAccion: _filtrado ? _limpiar : null,
                        ),
                      ),
                    ]
                  : [
                      _filtrado
                          ? _Grilla(productos: productos, onFinal: cargador.cargarMas)
                          : _Editorial(
                              productos: productos,
                              orden: _orden,
                              onFinal: cargador.cargarMas,
                            ),
                      const SliverToBoxAdapter(child: SizedBox(height: 32)),
                    ],
            ),
          ],
        ),
      ),
    );
  }

  static String? _nombreDeMarca(List<Map<String, Object?>> marcas, int? id) {
    if (id == null) return null;
    for (final m in marcas) {
      if ((m['id'] as num?)?.toInt() == id) return m['nombre']?.toString();
    }
    return null;
  }
}

/// Las colecciones (las marcas) en un carril horizontal. La elegida toma el
/// acento de su colección.
class _CarrilDeColecciones extends StatefulWidget {
  const _CarrilDeColecciones({
    required this.marcas,
    required this.elegida,
    required this.onElegir,
  });

  final List<Map<String, Object?>> marcas;
  final int? elegida;
  final ValueChanged<int?> onElegir;

  @override
  State<_CarrilDeColecciones> createState() => _CarrilDeColeccionesState();
}

class _CarrilDeColeccionesState extends State<_CarrilDeColecciones> {
  final _elegidaClave = GlobalKey();

  @override
  void initState() {
    super.initState();
    // Al llegar desde la vidriera con una colección ya elegida, su pastilla
    // puede estar fuera de la vista: el carril se corre hasta mostrarla.
    if (widget.elegida != null) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        final contexto = _elegidaClave.currentContext;
        if (contexto == null || !mounted) return;
        Scrollable.ensureVisible(
          contexto,
          alignment: 0.5,
          duration: Movimiento.o(context, Movimiento.medio),
          curve: Movimiento.estandar,
        );
      });
    }
  }

  @override
  Widget build(BuildContext context) => SizedBox(
    height: 44,
    child: ListView.separated(
      scrollDirection: Axis.horizontal,
      padding: const EdgeInsets.symmetric(horizontal: Medidas.margen),
      itemCount: widget.marcas.length + 1,
      // Las pastillas son pocas y livianas: se arman todas, así la elegida
      // existe aunque esté lejos y el carril puede correrse hasta ella.
      scrollCacheExtent: const ScrollCacheExtent.pixels(4000),
      separatorBuilder: (_, _) => const SizedBox(width: 8),
      itemBuilder: (context, indice) {
        if (indice == 0) {
          return _Pastilla(
            texto: 'Todo',
            elegida: widget.elegida == null,
            acento: Paleta.azulPrincipal,
            onTap: () => widget.onElegir(null),
          );
        }
        final marca = widget.marcas[indice - 1];
        final id = (marca['id'] as num?)?.toInt();
        final nombre = marca['nombre']?.toString() ?? 'Marca';
        final elegida = widget.elegida == id;
        return KeyedSubtree(
          key: elegida ? _elegidaClave : null,
          child: _Pastilla(
            texto: nombre,
            elegida: elegida,
            acento: EstiloDeColeccion.paraMarca(nombre).acento,
            onTap: () => widget.onElegir(elegida ? null : id),
          ),
        );
      },
    ),
  );
}

class _Pastilla extends StatelessWidget {
  const _Pastilla({
    required this.texto,
    required this.elegida,
    required this.acento,
    required this.onTap,
  });

  final String texto;
  final bool elegida;
  final Color acento;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => Semantics(
    selected: elegida,
    button: true,
    label: texto == 'Todo' ? 'Todas las colecciones' : 'Colección $texto',
    excludeSemantics: true,
    child: Presionable(
      onTap: onTap,
      child: AnimatedContainer(
        duration: Movimiento.o(context, Movimiento.corto),
        curve: Movimiento.estandar,
        padding: const EdgeInsets.symmetric(horizontal: 16),
        alignment: Alignment.center,
        decoration: BoxDecoration(
          color: elegida ? acento.withValues(alpha: 0.16) : Paleta.negroAzulado,
          borderRadius: BorderRadius.circular(22),
          border: Border.all(
            color: elegida ? acento : Paleta.borde,
            width: elegida ? 1.4 : 1,
          ),
          boxShadow: elegida
              ? [
                  BoxShadow(
                    color: acento.withValues(alpha: 0.28),
                    blurRadius: 16,
                    spreadRadius: -4,
                  ),
                ]
              : const [],
        ),
        child: Text(
          texto,
          style: Theme.of(context).textTheme.labelLarge?.copyWith(
            color: elegida ? Paleta.texto : Paleta.textoSecundario,
          ),
        ),
      ),
    ),
  );
}

/// Al elegir una colección, su nombre ocupa la pantalla como marca de agua,
/// sobre la textura de la colección.
class _EncabezadoDeColeccion extends StatelessWidget {
  const _EncabezadoDeColeccion({required this.nombre, super.key});

  final String nombre;

  @override
  Widget build(BuildContext context) {
    final estilo = EstiloDeColeccion.paraMarca(nombre);
    return Padding(
      padding: const EdgeInsets.fromLTRB(Medidas.margen, 16, Medidas.margen, 4),
      child: SizedBox(
        height: 112,
        child: ClipRRect(
          borderRadius: Medidas.bordeTarjeta,
          child: Stack(
            fit: StackFit.expand,
            children: [
              DecoratedBox(
                decoration: BoxDecoration(
                  color: Paleta.negroPrincipal,
                  gradient: RadialGradient(
                    center: const Alignment(0.7, -0.4),
                    radius: 1.2,
                    colors: [
                      estilo.acento.withValues(alpha: 0.32),
                      Paleta.negroPrincipal.withValues(alpha: 0),
                    ],
                  ),
                  border: Border.all(color: Paleta.borde),
                  borderRadius: Medidas.bordeTarjeta,
                ),
                child: CustomPaint(
                  painter: PintorDePatron(estilo: estilo, opacidad: 0.09),
                ),
              ),
              // Arriba a la derecha y apenas visible: es textura detrás del
              // título, no un segundo título encimado.
              Positioned(
                right: -10,
                top: -16,
                child: Text(
                  nombre.toUpperCase(),
                  maxLines: 1,
                  softWrap: false,
                  style: TextStyle(
                    fontFamily: Tipografia.titulares,
                    fontWeight: FontWeight.w700,
                    fontSize: 78,
                    height: 1,
                    letterSpacing: -3,
                    foreground: Paint()
                      ..style = PaintingStyle.stroke
                      ..strokeWidth = 1.2
                      ..color = estilo.acento.withValues(alpha: 0.2),
                  ),
                ),
              ),
              Padding(
                padding: const EdgeInsets.all(18),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  mainAxisAlignment: MainAxisAlignment.end,
                  children: [
                    const Rotulo('colección'),
                    const SizedBox(height: 4),
                    Text(
                      nombre,
                      style: Theme.of(context).textTheme.headlineMedium,
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// Cuántos productos hay (si se sabe) y cómo están ordenados.
class _BarraDeOrden extends StatelessWidget {
  const _BarraDeOrden({
    required this.orden,
    required this.cantidad,
    required this.filtrado,
    required this.onOrden,
    required this.onLimpiar,
  });

  final OrdenDeCatalogo orden;

  /// Nula mientras quedan bloques por traer: contar a medias sería mentir.
  final int? cantidad;
  final bool filtrado;
  final ValueChanged<OrdenDeCatalogo> onOrden;
  final VoidCallback onLimpiar;

  @override
  Widget build(BuildContext context) {
    final marca = MarcaTema.of(context);
    return Padding(
      padding: const EdgeInsets.fromLTRB(Medidas.margen, 10, 8, 8),
      child: Row(
        children: [
          Expanded(
            child: Text(
              cantidad == null
                  ? ''
                  : cantidad == 1
                  ? '1 producto'
                  : '$cantidad productos',
              style: marca.rotulo,
            ),
          ),
          if (filtrado)
            TextButton(onPressed: onLimpiar, child: const Text('Limpiar')),
          PopupMenuButton<OrdenDeCatalogo>(
            tooltip: 'Ordenar',
            initialValue: orden,
            onSelected: onOrden,
            itemBuilder: (context) => [
              for (final opcion in OrdenDeCatalogo.values)
                CheckedPopupMenuItem(
                  value: opcion,
                  checked: opcion == orden,
                  child: Text(opcion.etiqueta),
                ),
            ],
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  const Icon(
                    Icons.swap_vert_rounded,
                    size: 18,
                    color: Paleta.azulLuminoso,
                  ),
                  const SizedBox(width: 6),
                  Text(
                    orden.etiqueta,
                    style: Theme.of(context).textTheme.labelLarge?.copyWith(
                      color: Paleta.azulLuminoso,
                    ),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}

/// La composición editorial: escaparate, pares y tarjetas anchas.
class _Editorial extends StatelessWidget {
  const _Editorial({
    required this.productos,
    required this.orden,
    required this.onFinal,
  });

  final List<Product> productos;
  final OrdenDeCatalogo orden;
  final VoidCallback onFinal;

  @override
  Widget build(BuildContext context) {
    final bloques = componerVidriera(productos, orden: orden);
    return SliverPadding(
      padding: const EdgeInsets.symmetric(horizontal: Medidas.margen),
      sliver: SliverList.separated(
        itemCount: bloques.length,
        separatorBuilder: (_, _) => const SizedBox(height: 14),
        itemBuilder: (context, indice) {
          // Al asomar los últimos bloques se pide el siguiente, así la
          // vidriera no corta en treinta ni baja el catálogo entero de una.
          if (indice >= bloques.length - 2) {
            WidgetsBinding.instance.addPostFrameCallback((_) => onFinal());
          }
          final bloque = bloques[indice];
          return Aparicion(
            clave: 'catalogo-${bloque.clave}',
            orden: indice.clamp(0, 4),
            animar: indice < 4,
            child: _bloque(context, bloque),
          );
        },
      ),
    );
  }

  Widget _bloque(BuildContext context, BloqueDeCatalogo bloque) =>
      switch (bloque) {
        // En vertical, el escaparate es casi cuadrado; acostado o en una
        // tableta sería más alto que la pantalla, así que se apaisa.
        BloqueDestacado(:final producto, :final rotulo) => LayoutBuilder(
          builder: (context, limites) => AspectRatio(
            aspectRatio: limites.maxWidth > 560 ? 1.8 : 0.92,
            child: TarjetaDestacada(
              producto: producto,
              contexto: 'catalogo',
              rotulo: rotulo,
            ),
          ),
        ),
        BloqueAncho(:final producto, :final fotoALaDerecha) => SizedBox(
          height: TarjetaAncha.alto(context),
          child: TarjetaAncha(
            producto: producto,
            contexto: 'catalogo',
            fotoALaDerecha: fotoALaDerecha,
          ),
        ),
        BloquePar(:final primero, :final segundo) => LayoutBuilder(
          builder: (context, limites) {
            final ancho = (limites.maxWidth - 12) / 2;
            final alto = ancho + altoDeInfoDeTarjeta(context);
            return SizedBox(
              height: alto,
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Expanded(
                    child: TarjetaDeProducto(
                      producto: primero,
                      contexto: 'catalogo',
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: segundo == null
                        ? const SizedBox.shrink()
                        : TarjetaDeProducto(
                            producto: segundo,
                            contexto: 'catalogo',
                          ),
                  ),
                ],
              ),
            );
          },
        ),
      };
}

/// Grilla pareja para resultados de búsqueda y colecciones.
class _Grilla extends StatelessWidget {
  const _Grilla({required this.productos, required this.onFinal});

  final List<Product> productos;
  final VoidCallback onFinal;

  @override
  Widget build(BuildContext context) => SliverPadding(
    padding: const EdgeInsets.symmetric(horizontal: Medidas.margen),
    sliver: SliverLayoutBuilder(
      builder: (context, limites) {
        final columnas = math.max(2, (limites.crossAxisExtent / 240).floor());
        final ancho = (limites.crossAxisExtent - 12 * (columnas - 1)) / columnas;
        return SliverGrid.builder(
          gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
            crossAxisCount: columnas,
            crossAxisSpacing: 12,
            mainAxisSpacing: 12,
            mainAxisExtent: ancho + altoDeInfoDeTarjeta(context),
          ),
          itemCount: productos.length,
          itemBuilder: (context, indice) {
            if (indice >= productos.length - 4) {
              WidgetsBinding.instance.addPostFrameCallback((_) => onFinal());
            }
            final producto = productos[indice];
            return Aparicion(
              clave: 'grilla-${producto.id}',
              orden: indice % 6,
              animar: indice < 6,
              child: TarjetaDeProducto(producto: producto, contexto: 'catalogo'),
            );
          },
        );
      },
    ),
  );
}
