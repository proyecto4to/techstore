import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/animacion/aparicion.dart';
import '../../../core/diseno/medidas.dart';
import '../../../core/widgets/async_states.dart';
import '../../../core/widgets/marca/rotulo.dart';
import '../../cart/presentation/boton_carrito.dart';
import '../application/catalog_providers.dart';
import 'vitrina/tarjetas_de_producto.dart';

/// Lo guardado con el corazón, con las mismas tarjetas del catálogo: desde
/// acá se compra igual que desde allá. Quitar uno es tocar su corazón, que
/// cambia en el acto y vuelve atrás —avisando— si el servidor no lo confirma.
class FavoritesPage extends ConsumerWidget {
  const FavoritesPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final favoritos = ref.watch(favoritesProvider);
    return Scaffold(
      appBar: AppBar(
        title: const Text('Favoritos'),
        actions: const [BotonCarrito(), SizedBox(width: 6)],
      ),
      body: RefreshIndicator(
        onRefresh: () => ref.refresh(favoritesProvider.future),
        child: favoritos.when(
          skipLoadingOnRefresh: true,
          loading: () => const AppLoading(label: 'Cargando favoritos'),
          error: (error, _) => AppErrorState(
            message:
                'No pudimos cargar tus favoritos. '
                'Revisá tu conexión e intentá de nuevo.',
            onRetry: () => ref.invalidate(favoritesProvider),
          ),
          data: (productos) => productos.isEmpty
              ? AppEmptyState(
                  icon: Icons.favorite_border_rounded,
                  title: 'Todavía no tenés favoritos',
                  message:
                      'Guardá productos con el corazón para encontrarlos '
                      'rápido cuando vuelvas.',
                  accion: 'Ver productos',
                  onAccion: () => context.go('/catalog'),
                )
              : CustomScrollView(
                  physics: const AlwaysScrollableScrollPhysics(),
                  slivers: [
                    SliverToBoxAdapter(
                      child: Padding(
                        padding: const EdgeInsets.fromLTRB(
                          Medidas.margen,
                          4,
                          Medidas.margen,
                          14,
                        ),
                        child: Rotulo(
                          productos.length == 1
                              ? '1 guardado'
                              : '${productos.length} guardados',
                        ),
                      ),
                    ),
                    SliverPadding(
                      padding: const EdgeInsets.fromLTRB(
                        Medidas.margen,
                        0,
                        Medidas.margen,
                        28,
                      ),
                      sliver: SliverLayoutBuilder(
                        builder: (context, limites) {
                          final columnas = math.max(
                            2,
                            (limites.crossAxisExtent / 240).floor(),
                          );
                          final ancho =
                              (limites.crossAxisExtent - 12 * (columnas - 1)) /
                              columnas;
                          return SliverGrid.builder(
                            gridDelegate:
                                SliverGridDelegateWithFixedCrossAxisCount(
                                  crossAxisCount: columnas,
                                  crossAxisSpacing: 12,
                                  mainAxisSpacing: 12,
                                  mainAxisExtent:
                                      ancho + altoDeInfoDeTarjeta(context),
                                ),
                            itemCount: productos.length,
                            itemBuilder: (context, indice) => Aparicion(
                              orden: indice % 6,
                              animar: indice < 6,
                              child: TarjetaDeProducto(
                                producto: productos[indice],
                                contexto: 'favoritos',
                              ),
                            ),
                          );
                        },
                      ),
                    ),
                  ],
                ),
        ),
      ),
    );
  }
}
