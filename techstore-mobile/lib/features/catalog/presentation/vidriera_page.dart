import 'dart:async';
import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/animacion/aparicion.dart';
import '../../../core/diseno/forma_con_corte.dart';
import '../../../core/diseno/marca_tema.dart';
import '../../../core/diseno/medidas.dart';
import '../../../core/diseno/movimiento.dart';
import '../../../core/diseno/paleta.dart';
import '../../../core/diseno/tipografia.dart';
import '../../../core/widgets/async_states.dart';
import '../../../core/widgets/baldosa_de_acceso.dart';
import '../../../core/widgets/marca/isotipo.dart';
import '../../../core/widgets/marca/logotipo.dart';
import '../../../core/widgets/marca/rotulo.dart';
import '../../auth/application/session_controller.dart';
import '../../cart/presentation/boton_carrito.dart';
import '../../notifications/presentation/campana_de_avisos.dart';
import '../application/catalog_providers.dart';
import '../domain/product.dart';
import 'vitrina/carrusel_de_productos.dart';
import 'vitrina/escenario_de_producto.dart';
import 'vitrina/estilo_de_coleccion.dart';
import 'vitrina/foto_de_producto.dart';
import 'vitrina/tarjetas_de_producto.dart';

/// El inicio de quien compra: una vidriera, no una grilla de módulos.
///
/// Todo sale de datos reales: el carrusel muestra ofertas vigentes o, si no
/// hay, lo recién llegado; las colecciones son las marcas; «últimas unidades»
/// aparece solo si hay escasez de verdad. Abajo quedan los accesos de la
/// cuenta, para que nada de lo que había en la grilla se pierda.
class VidrieraPage extends ConsumerWidget {
  const VidrieraPage({this.etiquetaDeEntorno, super.key});

  /// Nula en producción: ahí no hay nada que aclararle al usuario.
  final String? etiquetaDeEntorno;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final usuario = ref.watch(authSessionProvider).user;
    final vidriera = ref.watch(vidrieraProvider);
    // Las fotos de los carruseles se bajan en los ratos libres, antes de que
    // alguien los deslice.
    ref.listen(vidrieraProvider, (_, siguiente) {
      final datos = siguiente.value;
      if (datos != null) precalentarFotos(context, datos.productos);
    });

    return Scaffold(
      appBar: AppBar(
        leading: const CampanaDeAvisos(),
        titleSpacing: 4,
        title: const Imagotipo(tamano: 19),
        actions: [
          IconButton(
            tooltip: 'Buscar',
            onPressed: () => context.push('/catalog'),
            icon: const Icon(Icons.search_rounded),
          ),
          const BotonCarrito(),
          PopupMenuButton<String>(
            tooltip: 'Más opciones',
            icon: const Icon(Icons.more_vert_rounded),
            onSelected: (opcion) {
              if (opcion == 'cuenta') context.push('/account');
              if (opcion == 'salir') {
                ref.read(authSessionProvider.notifier).logout();
              }
            },
            itemBuilder: (context) => const [
              PopupMenuItem(value: 'cuenta', child: Text('Mi cuenta')),
              PopupMenuItem(value: 'salir', child: Text('Cerrar sesión')),
            ],
          ),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: () => ref.refresh(vidrieraProvider.future),
        child: CustomScrollView(
          slivers: [
            SliverToBoxAdapter(
              child: _Bienvenida(
                nombre: usuario?.firstName,
                entorno: etiquetaDeEntorno,
              ),
            ),
            const SliverToBoxAdapter(child: _AccesosDeCuenta()),
            ...vidriera.when(
              skipLoadingOnRefresh: true,
              loading: () => const [
                SliverToBoxAdapter(
                  child: SizedBox(
                    height: 260,
                    child: AppLoading(label: 'Cargando la vidriera'),
                  ),
                ),
              ],
              error: (error, _) => [
                SliverToBoxAdapter(
                  child: SizedBox(
                    height: 320,
                    child: AppErrorState(
                      message:
                          'No pudimos cargar las novedades. '
                          'Revisá tu conexión e intentá de nuevo.',
                      onRetry: () => ref.invalidate(vidrieraProvider),
                    ),
                  ),
                ),
              ],
              data: (datos) => [
                if (datos.destacados.isNotEmpty)
                  SliverToBoxAdapter(
                    child: _CarruselDeCampanas(
                      productos: datos.destacados,
                      rotulo: datos.destacaOfertas
                          ? 'en oferta'
                          : 'recién llegado',
                    ),
                  ),
                if (datos.colecciones.isNotEmpty)
                  SliverToBoxAdapter(
                    child: _Colecciones(
                      colecciones: datos.colecciones,
                      contar: datos.completo,
                    ),
                  ),
                SliverToBoxAdapter(
                  child: Padding(
                    padding: const EdgeInsets.only(top: 34),
                    child: CarruselDeProductos(
                      rotulo: 'recién llegados',
                      titulo: 'Lo último que entró',
                      productos: datos.novedades,
                      contexto: 'inicio-nuevos',
                      accion: 'Ver todo',
                      onAccion: () => context.push('/catalog'),
                    ),
                  ),
                ),
                if (datos.ultimasUnidades.isNotEmpty)
                  SliverToBoxAdapter(
                    child: Padding(
                      padding: const EdgeInsets.only(top: 34),
                      child: CarruselDeProductos(
                        rotulo: 'últimas unidades',
                        titulo: 'Queda poco',
                        productos: datos.ultimasUnidades,
                        contexto: 'inicio-ultimas',
                      ),
                    ),
                  ),
                if (datos.productos.isEmpty)
                  const SliverToBoxAdapter(
                    child: SizedBox(
                      height: 280,
                      child: AppEmptyState(
                        icon: Icons.inventory_2_outlined,
                        message: 'Todavía no hay productos publicados.',
                      ),
                    ),
                  ),
                SliverToBoxAdapter(
                  child: _TodoElCatalogo(
                    total: datos.completo ? datos.total : null,
                  ),
                ),
              ],
            ),
            const SliverToBoxAdapter(child: SizedBox(height: 36)),
          ],
        ),
      ),
    );
  }
}

/// La presentación de la marca: el saludo se escribe solo y el lema queda.
class _Bienvenida extends StatelessWidget {
  const _Bienvenida({required this.nombre, required this.entorno});

  final String? nombre;
  final String? entorno;

  @override
  Widget build(BuildContext context) {
    final marca = MarcaTema.of(context);
    final textos = Theme.of(context).textTheme;
    final saludo = nombre == null || nombre!.trim().isEmpty
        ? 'bienvenido'
        : 'hola, ${nombre!.trim().toLowerCase()}';
    return Padding(
      padding: const EdgeInsets.fromLTRB(
        Medidas.margen,
        10,
        Medidas.margen,
        22,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Text('// ', style: marca.rotulo.copyWith(color: marca.cursor)),
              Flexible(
                child: TextoTecleado(
                  entorno == null ? saludo : '$saludo · $entorno',
                  estilo: marca.rotulo,
                  demora: const Duration(milliseconds: 150),
                ),
              ),
            ],
          ),
          const SizedBox(height: 10),
          Aparicion(
            orden: 2,
            child: Text(
              'Precio claro.\nStock real.',
              style: textos.displaySmall,
            ),
          ),
          const SizedBox(height: 10),
          Aparicion(
            orden: 3,
            child: Text(
              'El precio que ves es el que pagás, y lo que dice que hay, hay.',
              style: textos.bodyMedium?.copyWith(color: Paleta.textoSecundario),
            ),
          ),
        ],
      ),
    );
  }
}

/// El carrusel principal: productos a todo el ancho que pasan solos, una
/// vuelta, mientras la vidriera está a la vista y nadie los está tocando.
class _CarruselDeCampanas extends StatefulWidget {
  const _CarruselDeCampanas({required this.productos, required this.rotulo});

  final List<Product> productos;
  final String rotulo;

  @override
  State<_CarruselDeCampanas> createState() => _CarruselDeCampanasState();
}

class _CarruselDeCampanasState extends State<_CarruselDeCampanas> {
  final _paginas = PageController(viewportFraction: 0.88);
  Timer? _avance;
  int _actual = 0;

  /// Ya no avanza solo: dio la vuelta entera o la persona lo tomó con la mano.
  /// Un carrusel que gira para siempre es movimiento que nadie pidió.
  bool _quieto = false;

  /// Cuánto se queda cada producto antes de pasar al siguiente.
  static const _pausa = Duration(seconds: 6);

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _programar();
  }

  @override
  void dispose() {
    _avance?.cancel();
    _paginas.dispose();
    super.dispose();
  }

  /// Sin animaciones pedidas por el sistema, con el lector de pantalla
  /// activo —el contenido no puede cambiar mientras se lo están leyendo— o
  /// con un solo producto, no avanza solo. Tampoco cuando la pantalla quedó
  /// tapada por otra: un temporizador corriendo detrás no le sirve a nadie.
  void _programar() {
    _avance?.cancel();
    if (_quieto ||
        widget.productos.length < 2 ||
        Movimiento.reducido(context) ||
        MediaQuery.accessibleNavigationOf(context)) {
      return;
    }
    _avance = Timer.periodic(_pausa, (_) {
      if (!mounted || !_paginas.hasClients) return;
      if (!(ModalRoute.of(context)?.isCurrent ?? true)) return;
      final siguiente = (_actual + 1) % widget.productos.length;
      _paginas.animateToPage(
        siguiente,
        duration: Movimiento.escena,
        curve: Movimiento.estandar,
      );
      // De vuelta en el primero: la vuelta está completa y se queda ahí.
      if (siguiente == 0) _detener();
    });
  }

  void _detener() {
    _quieto = true;
    _avance?.cancel();
  }

  @override
  Widget build(BuildContext context) {
    final pantalla = MediaQuery.sizeOf(context);
    // Proporcional al ancho, pero nunca más alto que tres cuartos de la
    // pantalla: acostado, el carrusel no puede tapar todo lo demás.
    final alto = math.min(
      (pantalla.width * 0.88 / 0.9).clamp(320.0, 470.0),
      math.max(280.0, pantalla.height * 0.75),
    );
    return Column(
      children: [
        SizedBox(
          height: alto,
          child: NotificationListener<ScrollNotification>(
            // Si la persona lo toca, el carrusel queda en sus manos: no se le
            // mueve más el producto que está mirando.
            onNotification: (aviso) {
              if (aviso is ScrollStartNotification &&
                  aviso.dragDetails != null) {
                _detener();
              }
              return false;
            },
            child: PageView.builder(
              controller: _paginas,
              itemCount: widget.productos.length,
              onPageChanged: (pagina) => setState(() => _actual = pagina),
              itemBuilder: (context, indice) => AnimatedBuilder(
                animation: _paginas,
                builder: (context, hijo) {
                  // Qué tan lejos está esta página del centro: mueve las capas
                  // del escenario y achica apenas las de los costados.
                  final pagina =
                      _paginas.hasClients && _paginas.position.haveDimensions
                      ? _paginas.page ?? _actual.toDouble()
                      : _actual.toDouble();
                  final distancia = (indice - pagina).clamp(-1.0, 1.0);
                  return Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 6),
                    child: Transform.scale(
                      scale: 1 - 0.05 * distancia.abs(),
                      child: TarjetaDestacada(
                        producto: widget.productos[indice],
                        contexto: 'inicio-campana',
                        rotulo: widget.rotulo,
                        paralaje: distancia,
                      ),
                    ),
                  );
                },
              ),
            ),
          ),
        ),
        const SizedBox(height: 14),
        _Indicador(cantidad: widget.productos.length, actual: _actual),
      ],
    );
  }
}

/// Qué página se ve: la actual es el cursor de la marca, un bloque cian.
class _Indicador extends StatelessWidget {
  const _Indicador({required this.cantidad, required this.actual});

  final int cantidad;
  final int actual;

  @override
  Widget build(BuildContext context) => Semantics(
    label: 'Producto ${actual + 1} de $cantidad',
    excludeSemantics: true,
    child: Row(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        for (var i = 0; i < cantidad; i++)
          AnimatedContainer(
            duration: Movimiento.o(context, Movimiento.medio),
            curve: Movimiento.estandar,
            margin: const EdgeInsets.symmetric(horizontal: 3),
            width: i == actual ? 22 : 7,
            height: 7,
            decoration: BoxDecoration(
              color: i == actual ? Paleta.cian : Paleta.borde,
              borderRadius: BorderRadius.circular(i == actual ? 2 : 4),
            ),
          ),
      ],
    ),
  );
}

/// Las colecciones, cada una con su textura y la foto de uno de sus productos.
class _Colecciones extends StatelessWidget {
  const _Colecciones({required this.colecciones, required this.contar});

  final List<ColeccionDeVidriera> colecciones;

  /// Solo se dice cuántos productos tiene cada una si se vio el catálogo
  /// entero.
  final bool contar;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.only(top: 34),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(Medidas.margen, 0, 8, 10),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Rotulo('colecciones'),
                    const SizedBox(height: 4),
                    Text(
                      'Por marca',
                      style: Theme.of(context).textTheme.titleLarge,
                    ),
                  ],
                ),
              ),
              TextButton(
                onPressed: () => context.push('/catalog'),
                child: const Text('Ver catálogo'),
              ),
            ],
          ),
        ),
        SizedBox(
          height: 150,
          child: ListView.separated(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.symmetric(horizontal: Medidas.margen),
            itemCount: colecciones.length,
            separatorBuilder: (_, _) => const SizedBox(width: 12),
            itemBuilder: (context, indice) => Aparicion(
              clave: 'coleccion-${colecciones[indice].nombre}',
              orden: indice.clamp(0, 4),
              animar: indice < 3,
              desplazamiento: 0,
              escala: 0.94,
              child: _TarjetaDeColeccion(
                coleccion: colecciones[indice],
                contar: contar,
              ),
            ),
          ),
        ),
      ],
    ),
  );
}

class _TarjetaDeColeccion extends StatelessWidget {
  const _TarjetaDeColeccion({required this.coleccion, required this.contar});

  final ColeccionDeVidriera coleccion;
  final bool contar;

  @override
  Widget build(BuildContext context) {
    final estilo = EstiloDeColeccion.paraMarca(coleccion.nombre);
    final cantidad = coleccion.productos == 1
        ? '1 producto'
        : '${coleccion.productos} productos';
    const forma = FormaConCorte(corte: 12, radio: 18);
    return Semantics(
      button: true,
      label: contar
          ? 'Colección ${coleccion.nombre}, $cantidad'
          : 'Colección ${coleccion.nombre}',
      excludeSemantics: true,
      child: Presionable(
        onTap: () => context.push(
          coleccion.id == null ? '/catalog' : '/catalog?marca=${coleccion.id}',
        ),
        child: SizedBox(
          width: 210,
          child: DecoratedBox(
            decoration: const ShapeDecoration(
              shape: FormaConCorte(
                corte: 12,
                radio: 18,
                side: BorderSide(color: Paleta.borde),
              ),
            ),
            child: ClipPath(
              clipper: const ShapeBorderClipper(shape: forma),
              child: Stack(
                fit: StackFit.expand,
                children: [
                  // El escenario de un producto de la colección, sin texto:
                  // la textura y la luz de la marca hacen de fondo.
                  Positioned(
                    right: -18,
                    top: 0,
                    bottom: 0,
                    width: 150,
                    child: EscenarioDeProducto(
                      producto: coleccion.muestra,
                      encuadre: Encuadre.tarjeta,
                    ),
                  ),
                  DecoratedBox(
                    decoration: BoxDecoration(
                      gradient: LinearGradient(
                        colors: [
                          Paleta.negroPrincipal,
                          Paleta.negroPrincipal.withValues(alpha: 0.82),
                          Paleta.negroPrincipal.withValues(alpha: 0),
                        ],
                        stops: const [0, 0.42, 0.85],
                      ),
                    ),
                  ),
                  Padding(
                    padding: const EdgeInsets.all(16),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      mainAxisAlignment: MainAxisAlignment.end,
                      children: [
                        Container(width: 18, height: 3, color: estilo.acento),
                        const SizedBox(height: 10),
                        Text(
                          coleccion.nombre,
                          maxLines: 2,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(
                            fontFamily: Tipografia.titulares,
                            fontWeight: FontWeight.w700,
                            fontSize: 22,
                            height: 1.05,
                            letterSpacing: -0.5,
                            color: Paleta.texto,
                          ),
                        ),
                        if (contar) ...[
                          const SizedBox(height: 4),
                          Text(cantidad, style: MarcaTema.of(context).rotulo),
                        ],
                      ],
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/// La entrada al catálogo completo, con el isotipo enorme de marca de agua.
class _TodoElCatalogo extends StatelessWidget {
  const _TodoElCatalogo({required this.total});

  /// Nulo si no se vio el catálogo entero.
  final int? total;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.fromLTRB(Medidas.margen, 34, Medidas.margen, 0),
    child: Presionable(
      onTap: () => context.push('/catalog'),
      etiqueta: 'Ver todo el catálogo',
      child: Container(
        // Alto mínimo y no fijo: con la letra del sistema agrandada, el texto
        // se salía por abajo.
        constraints: const BoxConstraints(minHeight: 132),
        clipBehavior: Clip.antiAlias,
        decoration: BoxDecoration(
          borderRadius: Medidas.bordeTarjeta,
          gradient: const LinearGradient(
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
            colors: [Color(0xFF0E1E3F), Paleta.negroPrincipal],
          ),
          border: Border.all(color: Paleta.borde),
        ),
        child: Stack(
          alignment: AlignmentDirectional.centerStart,
          children: [
            const Positioned(
              right: -26,
              bottom: -34,
              child: Opacity(opacity: 0.22, child: Isotipo(ancho: 170)),
            ),
            Padding(
              padding: const EdgeInsets.all(20),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Rotulo(total == null ? 'catálogo' : '$total productos'),
                  const SizedBox(height: 6),
                  Text(
                    'Todo el catálogo',
                    style: Theme.of(context).textTheme.headlineSmall,
                  ),
                  const SizedBox(height: 4),
                  Text(
                    'Buscá, filtrá por marca y ordená por precio.',
                    style: Theme.of(context).textTheme.bodySmall,
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

/// Los accesos de la cuenta: todo lo que estaba en la grilla del inicio, en un
/// carril arriba de la vidriera. Arriba y no al final: el aviso de que un
/// pedido cambió de estado tiene que verse sin bajar.
class _AccesosDeCuenta extends StatelessWidget {
  const _AccesosDeCuenta();

  // Las notificaciones no están acá: tienen su campana arriba a la izquierda,
  // con el contador, y repetirlas en el carril era ofrecer dos veces lo mismo.
  static const _accesos = <(IconData, String, String)>[
    (Icons.receipt_long_outlined, 'Mis pedidos', '/orders'),
    (Icons.favorite_border_rounded, 'Favoritos', '/favorites'),
    (Icons.description_outlined, 'Mis facturas', '/invoices'),
    // Para quien compra, ese canal se llama «Atención al Cliente» en todas las
    // pantallas.
    (Icons.support_agent_outlined, 'Atención al Cliente', '/chat'),
    (Icons.location_on_outlined, 'Direcciones', '/addresses'),
    (Icons.person_outline_rounded, 'Mi cuenta', '/account'),
  ];

  @override
  Widget build(BuildContext context) {
    // Las baldosas crecen con la letra del sistema: con la letra agrandada,
    // «Notificaciones» se partía al medio y el nombre se salía por abajo.
    final letra = MediaQuery.textScalerOf(context);
    final ancho = math.min(letra.scale(116), 180.0);
    final alto = 66 + letra.scale(32);
    return Padding(
      padding: const EdgeInsets.only(bottom: 26),
      child: SizedBox(
        height: alto,
        child: ListView.separated(
          scrollDirection: Axis.horizontal,
          padding: const EdgeInsets.symmetric(horizontal: Medidas.margen),
          itemCount: _accesos.length,
          separatorBuilder: (_, _) => const SizedBox(width: 10),
          itemBuilder: (context, indice) {
            final (icono, etiqueta, ruta) = _accesos[indice];
            final Widget baldosa = BaldosaDeAcceso(
              icono: icono,
              etiqueta: etiqueta,
              onTap: () => context.push(ruta),
              compacta: true,
            );
            return SizedBox(
              width: ancho,
              child: Aparicion(orden: indice + 3, child: baldosa),
            );
          },
        ),
      ),
    );
  }
}
