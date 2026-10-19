import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';
import 'package:share_plus/share_plus.dart';

import '../../../core/animacion/aparicion.dart';
import '../../../core/di/providers.dart';
import '../../../core/diseno/marca_tema.dart';
import '../../../core/diseno/medidas.dart';
import '../../../core/diseno/movimiento.dart';
import '../../../core/diseno/paleta.dart';
import '../../../core/format/money.dart';
import '../../../core/widgets/add_to_cart_button.dart';
import '../../../core/widgets/async_states.dart';
import '../../../core/widgets/marca/rotulo.dart';
import '../../../core/widgets/quantity_stepper.dart';
import '../../cart/presentation/agregar_al_carrito.dart';
import '../../cart/presentation/boton_carrito.dart';
import '../application/catalog_providers.dart';
import '../domain/product.dart';
import 'vitrina/carrusel_de_productos.dart';
import 'vitrina/escenario_de_producto.dart';
import 'vitrina/foto_de_producto.dart';
import 'vitrina/piezas_de_producto.dart';
import 'vitrina/tarjetas_de_producto.dart';

/// La ficha: la presentación de un producto en su propia escena.
///
/// La foto llega volando desde la tarjeta que se tocó, se mueve con el
/// desplazamiento más lento que el texto (paralaje) y se abre en grande con un
/// toque. Lo comercial —precio, stock, cantidad y el botón— queda siempre a
/// mano en la barra de abajo.
class ProductDetailPage extends ConsumerStatefulWidget {
  const ProductDetailPage({required this.productId, this.heroTag, super.key});

  final int productId;

  /// El nombre del Hero de la tarjeta que se tocó. Nulo al llegar por un
  /// enlace: entonces la foto no viaja, aparece.
  final Object? heroTag;

  @override
  ConsumerState<ProductDetailPage> createState() => _ProductDetailPageState();
}

class _ProductDetailPageState extends ConsumerState<ProductDetailPage> {
  final _desplazamiento = ScrollController();
  final _corrimiento = ValueNotifier<double>(0);
  int _cantidad = 1;

  Object get _heroTag => widget.heroTag ?? 'producto-${widget.productId}-ficha';

  @override
  void initState() {
    super.initState();
    _desplazamiento.addListener(
      () => _corrimiento.value = _desplazamiento.offset,
    );
  }

  @override
  void dispose() {
    _desplazamiento.dispose();
    _corrimiento.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final producto = ref.watch(productProvider(widget.productId));
    final item = producto.value;

    return Scaffold(
      extendBodyBehindAppBar: true,
      appBar: AppBar(
        // Transparente sobre la escena; al pasar el contenido por debajo, la
        // barra se cierra en negro y aparece el nombre.
        backgroundColor: WidgetStateColor.resolveWith(
          (estados) => estados.contains(WidgetState.scrolledUnder)
              ? Paleta.negroPrincipal.withValues(alpha: 0.94)
              : Colors.transparent,
        ),
        title: item == null
            ? null
            : ValueListenableBuilder<double>(
                valueListenable: _corrimiento,
                builder: (context, corrido, _) => AnimatedOpacity(
                  opacity: corrido > 280 ? 1 : 0,
                  duration: Movimiento.o(context, Movimiento.corto),
                  child: Text(item.name, overflow: TextOverflow.ellipsis),
                ),
              ),
        actions: [
          if (item != null) ...[
            BotonFavorito(producto: item),
            IconButton(
              tooltip: 'Compartir',
              onPressed: () => _compartir(item),
              icon: const Icon(Icons.ios_share_rounded),
            ),
          ],
          const BotonCarrito(),
          const SizedBox(width: 6),
        ],
      ),
      body: producto.when(
        loading: () => const AppLoading(label: 'Cargando producto'),
        error: (error, _) => AppErrorState(
          message: 'No pudimos cargar este producto. Intentá de nuevo.',
          onRetry: () => ref.invalidate(productProvider(widget.productId)),
        ),
        data: _contenido,
      ),
      bottomNavigationBar: item == null ? null : _barraDeCompra(item),
    );
  }

  Widget _contenido(Product item) {
    // La barra flota sobre la escena: el producto se ubica debajo de ella y
    // la escena crece lo mismo, para que la foto no pierda tamaño.
    final barra = MediaQuery.paddingOf(context).top + kToolbarHeight;
    final alto =
        math.min(MediaQuery.sizeOf(context).width * 1.02, 470.0) +
        barra * 0.6 -
        28;
    return CustomScrollView(
      controller: _desplazamiento,
      slivers: [
        SliverToBoxAdapter(
          child: SizedBox(
            height: alto,
            child: GestureDetector(
              onTap: item.imageUrl == null ? null : () => _abrirVisor(item),
              child: Semantics(
                button: item.imageUrl != null,
                hint: item.imageUrl == null ? null : 'Ver la foto en grande',
                // La escena se corre dentro de su lugar y no más allá: en una
                // lista, la primera pieza se pinta encima de las demás, y al
                // bajar tapaba el título.
                child: ClipRect(
                  child: ValueListenableBuilder<double>(
                    valueListenable: _corrimiento,
                    builder: (context, corrido, hijo) {
                      // El escenario baja a la mitad de velocidad que el texto
                      // y sus capas se corren entre sí: profundidad, no adorno.
                      final avance = (corrido / alto).clamp(0.0, 1.0);
                      return Transform.translate(
                        offset: Offset(0, corrido * 0.45),
                        child: Opacity(
                          opacity: 1 - avance * 0.6,
                          child: EscenarioDeProducto(
                            producto: item,
                            encuadre: Encuadre.ficha,
                            heroTag: _heroTag,
                            paralaje: avance * 1.4,
                            aireSuperior: barra - 20,
                          ),
                        ),
                      );
                    },
                  ),
                ),
              ),
            ),
          ),
        ),
        SliverToBoxAdapter(child: _Datos(producto: item)),
        SliverToBoxAdapter(
          child: Padding(
            padding: const EdgeInsets.only(top: 12, bottom: 28),
            child: _Relacionados(producto: item),
          ),
        ),
      ],
    );
  }

  Widget _barraDeCompra(Product item) {
    final enCarrito = unidadesEnCarrito(ref, item.id);
    // Lo que ya está en el carrito descuenta de lo que se puede agregar: sin
    // esto el cliente podría elegir 5, tener 7 y terminar pidiendo 12 de un
    // producto que solo tiene 10.
    final lugar = math.max(0, item.maxPerOrder - enCarrito);
    final cantidad = _cantidad.clamp(1, lugar > 0 ? lugar : 1);
    final url = ref.watch(urlDeFotoProvider(item.imageUrl));
    final foto = url == null ? null : ref.watch(fabricaDeFotosProvider)(url);

    return DecoratedBox(
      decoration: const BoxDecoration(
        color: Paleta.negroPrincipal,
        border: Border(top: BorderSide(color: Paleta.borde)),
      ),
      child: SafeArea(
        top: false,
        child: Padding(
          padding: const EdgeInsets.fromLTRB(16, 12, 16, 12),
          child: Row(
            children: [
              if (lugar > 0) ...[
                QuantityStepper(
                  value: cantidad,
                  max: lugar,
                  dense: true,
                  limitMeansScarcity: item.showsRemainingUnits,
                  onChanged: (valor) => setState(() => _cantidad = valor),
                ),
                const SizedBox(width: 12),
              ],
              Expanded(
                child: AddToCartButton(
                  imagenParaVuelo: foto,
                  disabledLabel: item.maxPerOrder <= 0
                      ? 'Sin stock disponible'
                      : 'Ya tenés todo en el carrito',
                  onPressed: lugar <= 0
                      ? null
                      : () async {
                          final agregado = await agregarAlCarrito(
                            context,
                            ref,
                            item,
                            cantidad: cantidad,
                          );
                          if (agregado && mounted) {
                            setState(() => _cantidad = 1);
                          }
                          return agregado;
                        },
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Future<void> _compartir(Product item) async {
    final texto =
        'Mirá esto en TechStore: ${item.name}, '
        '${formatMoney(item.price, item.currency)}.';
    // Compartir abre otra app: sin la marca, la sesión se cerraría al volver.
    await ref
        .read(appExcursionProvider)
        .durante(
          () => SharePlus.instance.share(
            ShareParams(text: texto, subject: item.name),
          ),
        );
  }

  void _abrirVisor(Product item) {
    final url = ref.read(urlDeFotoProvider(item.imageUrl));
    if (url == null) return;
    Navigator.of(context).push(
      PageRouteBuilder<void>(
        opaque: false,
        barrierColor: Colors.black,
        transitionDuration: Movimiento.o(context, Movimiento.medio),
        reverseTransitionDuration: Movimiento.o(context, Movimiento.corto),
        // El mismo nombre de Hero que el escenario: la foto viaja de la ficha
        // al visor y vuelve.
        pageBuilder: (context, _, _) => _VisorDeFoto(
          foto: ref.read(fabricaDeFotosProvider)(url),
          nombre: item.name,
          heroTag: _heroTag,
        ),
        transitionsBuilder: (context, animacion, _, hijo) =>
            FadeTransition(opacity: animacion, child: hijo),
      ),
    );
  }
}

/// Nombre, precio, stock, descripción y ficha técnica.
class _Datos extends StatelessWidget {
  const _Datos({required this.producto});

  final Product producto;

  @override
  Widget build(BuildContext context) {
    final textos = Theme.of(context).textTheme;
    final marca = MarcaTema.of(context);
    final puntos = _puntosDe(producto.description);
    var orden = 0;
    Widget entra(Widget hijo) => Aparicion(orden: orden++, child: hijo);

    return DecoratedBox(
      // El panel sube sobre la escena con las esquinas redondeadas y una
      // sombra hacia arriba: la foto queda detrás, no cortada por una línea.
      decoration: const BoxDecoration(
        color: Paleta.fondo,
        borderRadius: BorderRadius.vertical(top: Radius.circular(30)),
        boxShadow: [
          BoxShadow(
            color: Color(0xB3000000),
            blurRadius: 28,
            offset: Offset(0, -10),
          ),
        ],
      ),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(
          Medidas.margen,
          24,
          Medidas.margen,
          8,
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            entra(Rotulo(producto.brand ?? 'producto')),
            const SizedBox(height: 8),
            entra(Text(producto.name, style: textos.headlineMedium)),
            const SizedBox(height: 16),
            entra(
              Wrap(
                spacing: 12,
                runSpacing: 8,
                crossAxisAlignment: WrapCrossAlignment.center,
                children: [
                  PrecioDeProducto(producto: producto, grande: true),
                  if (producto.hasPromotion)
                    EtiquetaDePromocion(
                      porcentaje: producto.promotionPercentage!,
                    ),
                ],
              ),
            ),
            if (producto.hasPromotion) ...[
              const SizedBox(height: 6),
              entra(
                Text(
                  [
                    producto.promotionName ?? 'Promoción',
                    if (producto.promotionEndsAt != null)
                      'hasta el ${DateFormat('d/M', 'es_PY').format(producto.promotionEndsAt!.toLocal())}',
                  ].join(' · '),
                  style: textos.bodySmall?.copyWith(color: marca.promocion),
                ),
              ),
            ],
            const SizedBox(height: 14),
            entra(IndicadorDeStock(producto: producto)),
            if (puntos.isNotEmpty) ...[
              const SizedBox(height: 28),
              entra(const Rotulo('qué es')),
              const SizedBox(height: 10),
              for (final punto in puntos)
                entra(
                  Padding(
                    padding: const EdgeInsets.only(bottom: 10),
                    child: Row(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Container(
                          width: 10,
                          height: 2,
                          margin: const EdgeInsets.only(top: 11, right: 12),
                          color: marca.cursor,
                        ),
                        Expanded(child: Text(punto, style: textos.bodyLarge)),
                      ],
                    ),
                  ),
                ),
            ],
            const SizedBox(height: 22),
            entra(const Rotulo('ficha')),
            const SizedBox(height: 10),
            entra(
              _FichaTecnica(
                filas: [
                  if (producto.brand != null) ('Marca', producto.brand!),
                  ('Código', '#${producto.id.toString().padLeft(4, '0')}'),
                  (
                    'Por pedido',
                    producto.maxPerOrder <= 0
                        ? 'Sin stock por ahora'
                        : producto.showsRemainingUnits
                        ? 'Hasta ${producto.maxPerOrder} '
                              '${producto.maxPerOrder == 1 ? 'unidad' : 'unidades'}'
                        : 'Hasta ${producto.maxPerOrder} unidades',
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  /// La descripción en frases: se presentan de a una, como puntos. Solo se
  /// corta donde la descripción misma termina una frase; no se inventan
  /// características que el texto no dice.
  static List<String> _puntosDe(String? descripcion) {
    final texto = descripcion?.trim() ?? '';
    if (texto.isEmpty) return const [];
    return texto
        .split(RegExp(r'(?<=[.;])\s+'))
        .map((frase) => frase.trim())
        .where((frase) => frase.isNotEmpty)
        .toList(growable: false);
  }
}

class _FichaTecnica extends StatelessWidget {
  const _FichaTecnica({required this.filas});

  final List<(String, String)> filas;

  @override
  Widget build(BuildContext context) {
    final textos = Theme.of(context).textTheme;
    final marca = MarcaTema.of(context);
    return DecoratedBox(
      decoration: BoxDecoration(
        color: Paleta.negroAzulado,
        borderRadius: Medidas.bordeControl,
        border: Border.all(color: Paleta.borde),
      ),
      child: Column(
        children: [
          for (final (indice, (clave, valor)) in filas.indexed) ...[
            if (indice > 0) const Divider(height: 1),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
              child: Row(
                children: [
                  Expanded(
                    child: Text(
                      clave,
                      style: textos.bodyMedium?.copyWith(
                        color: Paleta.textoSecundario,
                      ),
                    ),
                  ),
                  Flexible(
                    child: Text(
                      valor,
                      textAlign: TextAlign.end,
                      style: clave == 'Código'
                          ? marca.codigo
                          : textos.bodyMedium?.copyWith(
                              fontWeight: FontWeight.w600,
                            ),
                    ),
                  ),
                ],
              ),
            ),
          ],
        ],
      ),
    );
  }
}

class _Relacionados extends ConsumerWidget {
  const _Relacionados({required this.producto});

  final Product producto;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final relacionados = ref.watch(
      relacionadosProvider((
        id: producto.id,
        marcaId: producto.brandId,
        marca: producto.brand,
      )),
    );
    return relacionados.maybeWhen(
      data: (datos) => CarruselDeProductos(
        rotulo: datos.rotulo,
        productos: datos.productos,
        contexto: 'relacionados-${producto.id}',
      ),
      orElse: () => const SizedBox.shrink(),
    );
  }
}

/// La foto a pantalla completa, con pellizco para acercar y doble toque para
/// alternar entre la foto entera y un acercamiento.
class _VisorDeFoto extends StatefulWidget {
  const _VisorDeFoto({
    required this.foto,
    required this.nombre,
    required this.heroTag,
  });

  final ImageProvider foto;
  final String nombre;
  final Object heroTag;

  @override
  State<_VisorDeFoto> createState() => _VisorDeFotoState();
}

class _VisorDeFotoState extends State<_VisorDeFoto>
    with SingleTickerProviderStateMixin {
  final _transformacion = TransformationController();
  late final AnimationController _zoom = AnimationController(
    vsync: this,
    duration: Movimiento.medio,
  );
  Animation<Matrix4>? _animacion;
  TapDownDetails? _toque;

  @override
  void initState() {
    super.initState();
    _zoom.addListener(() {
      if (_animacion != null) _transformacion.value = _animacion!.value;
    });
  }

  @override
  void dispose() {
    _zoom.dispose();
    _transformacion.dispose();
    super.dispose();
  }

  void _dobleToque() {
    final acercado = _transformacion.value.getMaxScaleOnAxis() > 1.01;
    final punto = _toque?.localPosition ?? Offset.zero;
    final destino = acercado
        ? Matrix4.identity()
        : (Matrix4.identity()
            ..translateByDouble(-punto.dx * 1.5, -punto.dy * 1.5, 0, 1)
            ..scaleByDouble(2.5, 2.5, 1, 1));
    _animacion = Matrix4Tween(
      begin: _transformacion.value,
      end: destino,
    ).animate(CurvedAnimation(parent: _zoom, curve: Movimiento.estandar));
    _zoom.forward(from: 0);
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    backgroundColor: Colors.black,
    body: Stack(
      children: [
        Positioned.fill(
          child: GestureDetector(
            onDoubleTapDown: (detalle) => _toque = detalle,
            onDoubleTap: _dobleToque,
            child: InteractiveViewer(
              transformationController: _transformacion,
              maxScale: 5,
              child: Center(
                child: Hero(
                  tag: widget.heroTag,
                  child: Image(
                    image: widget.foto,
                    fit: BoxFit.contain,
                    semanticLabel: 'Foto de ${widget.nombre}',
                  ),
                ),
              ),
            ),
          ),
        ),
        SafeArea(
          child: Align(
            alignment: Alignment.topRight,
            child: Padding(
              padding: const EdgeInsets.all(8),
              child: IconButton.filledTonal(
                tooltip: 'Cerrar',
                onPressed: () => Navigator.of(context).pop(),
                icon: const Icon(Icons.close_rounded),
              ),
            ),
          ),
        ),
      ],
    ),
  );
}
