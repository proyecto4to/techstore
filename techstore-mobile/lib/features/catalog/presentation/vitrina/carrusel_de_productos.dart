import 'package:flutter/material.dart';

import '../../../../core/animacion/aparicion.dart';
import '../../../../core/diseno/medidas.dart';
import '../../../../core/widgets/marca/rotulo.dart';
import '../../domain/product.dart';
import 'tarjetas_de_producto.dart';

/// Una fila de tarjetas que se desliza de costado, con su rótulo arriba.
class CarruselDeProductos extends StatelessWidget {
  const CarruselDeProductos({
    required this.rotulo,
    required this.productos,
    required this.contexto,
    this.titulo,
    this.accion,
    this.onAccion,
    super.key,
  });

  final String rotulo;
  final String? titulo;
  final List<Product> productos;

  /// Prefijo de los nombres de Hero de esta fila; tiene que ser único en la
  /// pantalla.
  final String contexto;
  final String? accion;
  final VoidCallback? onAccion;

  static const _anchoTarjeta = 196.0;

  @override
  Widget build(BuildContext context) {
    if (productos.isEmpty) return const SizedBox.shrink();
    final alto = _anchoTarjeta + altoDeInfoDeTarjeta(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      mainAxisSize: MainAxisSize.min,
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
                    Rotulo(rotulo),
                    if (titulo != null) ...[
                      const SizedBox(height: 4),
                      Text(
                        titulo!,
                        style: Theme.of(context).textTheme.titleLarge,
                      ),
                    ],
                  ],
                ),
              ),
              if (accion != null && onAccion != null)
                TextButton(onPressed: onAccion, child: Text(accion!)),
            ],
          ),
        ),
        SizedBox(
          height: alto,
          child: ListView.separated(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.symmetric(horizontal: Medidas.margen),
            itemCount: productos.length,
            separatorBuilder: (_, _) => const SizedBox(width: 12),
            itemBuilder: (context, indice) => SizedBox(
              width: _anchoTarjeta,
              child: Aparicion(
                clave: '$contexto-${productos[indice].id}',
                orden: indice.clamp(0, 4),
                animar: indice < 3,
                desplazamiento: 0,
                escala: 0.94,
                child: TarjetaDeProducto(
                  producto: productos[indice],
                  contexto: contexto,
                ),
              ),
            ),
          ),
        ),
      ],
    );
  }
}
