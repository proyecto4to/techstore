import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:printing/printing.dart';

import '../diseno/movimiento.dart';

/// A cuántos puntos por pulgada se dibuja cada página.
///
/// Es lo que decide hasta dónde se puede acercar sin que la letra se deshaga.
/// A 72 —el tamaño natural del PDF— el documento se ve bien mientras entra en
/// la pantalla y se vuelve ilegible al primer acercamiento, que es justo cuando
/// alguien quiere leer el número de timbrado. A 180 una hoja A4 queda en unos
/// 1500 por 2100 puntos: se lee acercada y el PNG pesa poco.
const _puntosPorPulgada = 180.0;

/// Cuánto se puede acercar y alejar.
///
/// El mínimo es la hoja entera en pantalla: alejar más solo deja aire alrededor.
const _acercamientoMinimo = 1.0;
const _acercamientoMaximo = 6.0;

/// Cuánto cambia el acercamiento con cada toque a los botones.
const _paso = 1.6;

/// Una página del documento, ya dibujada.
final class PaginaDeDocumento {
  const PaginaDeDocumento({
    required this.imagen,
    required this.ancho,
    required this.alto,
  });

  /// La página en PNG. Comprimida ocupa poco; Flutter la descomprime solo
  /// cuando está en pantalla y la suelta cuando deja de estarlo, así que un
  /// documento de veinte hojas no se lleva puesta la memoria del teléfono.
  final Uint8List imagen;
  final int ancho;
  final int alto;
}

/// Dibuja las páginas de un PDF.
///
/// Devuelve un flujo para poder ir mostrando las primeras mientras se preparan
/// las que faltan: en un documento largo, esperar a que estén todas es esperar
/// frente a una pantalla en blanco.
Stream<PaginaDeDocumento> dibujarDocumento(
  Uint8List pdf, {
  double dpi = _puntosPorPulgada,
}) async* {
  await for (final pagina in Printing.raster(pdf, dpi: dpi)) {
    yield PaginaDeDocumento(
      imagen: await pagina.toPng(),
      ancho: pagina.width,
      alto: pagina.height,
    );
  }
}

/// Muestra un documento con acercamiento y desplazamiento libres.
///
/// Antes esto era una vista previa que encajaba la hoja en el alto disponible y
/// ahí terminaba: no había manera de acercarse a leer un número ni de correr la
/// hoja para ver una esquina. Acá la hoja entra entera al abrirse y desde ahí se
/// acerca con dos dedos, con la rueda del ratón o con los botones, y una vez
/// acercada se arrastra para cualquier lado sin toparse con el borde del marco.
class VisorDeDocumento extends StatefulWidget {
  const VisorDeDocumento({required this.paginas, super.key});

  final List<PaginaDeDocumento> paginas;

  @override
  State<VisorDeDocumento> createState() => _VisorDeDocumentoState();
}

class _VisorDeDocumentoState extends State<VisorDeDocumento> {
  final _hojas = PageController();
  final _transformaciones = <int, TransformationController>{};
  int _pagina = 0;
  double _acercamiento = 1;
  Size _marco = Size.zero;

  @override
  void dispose() {
    _hojas.dispose();
    for (final controlador in _transformaciones.values) {
      controlador.dispose();
    }
    super.dispose();
  }

  TransformationController _transformacion(int indice) =>
      _transformaciones.putIfAbsent(indice, () {
        final controlador = TransformationController();
        controlador.addListener(() {
          if (indice != _pagina) return;
          final escala = controlador.value.getMaxScaleOnAxis();
          if ((escala - _acercamiento).abs() < 0.001) return;
          setState(() => _acercamiento = escala);
        });
        return controlador;
      });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final acercado = _acercamiento > 1.01;
    return Column(
      children: [
        Expanded(
          child: ColoredBox(
            color: theme.colorScheme.surfaceContainerHighest,
            child: LayoutBuilder(
              builder: (context, restricciones) {
                _marco = Size(restricciones.maxWidth, restricciones.maxHeight);
                return PageView.builder(
                  controller: _hojas,
                  // Con la hoja acercada, arrastrar es mover la hoja, no pasar
                  // de página: si no, cualquier intento de mirar el margen
                  // derecho salta a la hoja siguiente.
                  physics: acercado
                      ? const NeverScrollableScrollPhysics()
                      : const PageScrollPhysics(),
                  onPageChanged: _alCambiarDePagina,
                  itemCount: widget.paginas.length,
                  itemBuilder: (context, indice) => InteractiveViewer(
                    transformationController: _transformacion(indice),
                    minScale: _acercamientoMinimo,
                    maxScale: _acercamientoMaximo,
                    // Sin límites: acercada, la hoja se corre para donde haga
                    // falta y no queda presa del tamaño del marco.
                    boundaryMargin: const EdgeInsets.all(double.infinity),
                    // Sin acercar entra entera, así que arrastrar solo serviría
                    // para tirarla fuera de la pantalla.
                    panEnabled: indice == _pagina && acercado,
                    child: Center(
                      // La hoja es papel: el PDF no trae fondo propio, y sobre
                      // la superficie oscura de la app el texto negro de la
                      // factura no se leía.
                      child: ColoredBox(
                        color: Colors.white,
                        child: Image.memory(
                          widget.paginas[indice].imagen,
                          fit: BoxFit.contain,
                          filterQuality: FilterQuality.medium,
                          semanticLabel:
                              'Página ${indice + 1} de ${widget.paginas.length}',
                        ),
                      ),
                    ),
                  ),
                );
              },
            ),
          ),
        ),
        _Controles(
          pagina: _pagina,
          paginas: widget.paginas.length,
          acercamiento: _acercamiento,
          onAnterior: _pagina == 0 ? null : () => _irA(_pagina - 1),
          onSiguiente: _pagina >= widget.paginas.length - 1
              ? null
              : () => _irA(_pagina + 1),
          onAcercar: _acercamiento >= _acercamientoMaximo
              ? null
              : () => _cambiarAcercamiento(_paso),
          onAlejar: acercado ? () => _cambiarAcercamiento(1 / _paso) : null,
          onEncajar: acercado ? _encajar : null,
        ),
      ],
    );
  }

  void _alCambiarDePagina(int indice) {
    // Cada hoja se abre entera. Llegar a la siguiente ya acercada y corrida a
    // una esquina deja mirando un pedazo de blanco sin saber dónde se está.
    _transformacion(_pagina).value = Matrix4.identity();
    setState(() {
      _pagina = indice;
      _acercamiento = _transformacion(indice).value.getMaxScaleOnAxis();
    });
  }

  void _irA(int indice) {
    // Sin animaciones pedidas por el sistema, la hoja cambia de un salto.
    if (Movimiento.reducido(context)) {
      _hojas.jumpToPage(indice);
      return;
    }
    _hojas.animateToPage(
      indice,
      duration: const Duration(milliseconds: 250),
      curve: Curves.easeOut,
    );
  }

  void _encajar() {
    _transformacion(_pagina).value = Matrix4.identity();
    setState(() => _acercamiento = 1);
  }

  /// Acerca o aleja sin perder de vista lo que estaba en el medio.
  ///
  /// Escalar sobre el origen manda el contenido hacia arriba y a la izquierda:
  /// se aprieta «acercar» mirando un número y el número se va de la pantalla.
  void _cambiarAcercamiento(double factor) {
    final controlador = _transformacion(_pagina);
    final actual = controlador.value.getMaxScaleOnAxis();
    final objetivo = (actual * factor).clamp(
      _acercamientoMinimo,
      _acercamientoMaximo,
    );
    final centro = Offset(_marco.width / 2, _marco.height / 2);
    final fijo = controlador.toScene(centro);
    controlador.value = Matrix4.identity()
      ..setEntry(0, 0, objetivo)
      ..setEntry(1, 1, objetivo)
      ..setEntry(0, 3, centro.dx - objetivo * fijo.dx)
      ..setEntry(1, 3, centro.dy - objetivo * fijo.dy);
    setState(() => _acercamiento = objetivo);
  }
}

class _Controles extends StatelessWidget {
  const _Controles({
    required this.pagina,
    required this.paginas,
    required this.acercamiento,
    required this.onAnterior,
    required this.onSiguiente,
    required this.onAcercar,
    required this.onAlejar,
    required this.onEncajar,
  });

  final int pagina;
  final int paginas;
  final double acercamiento;
  final VoidCallback? onAnterior;
  final VoidCallback? onSiguiente;
  final VoidCallback? onAcercar;
  final VoidCallback? onAlejar;
  final VoidCallback? onEncajar;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Material(
      color: theme.colorScheme.surface,
      elevation: 3,
      child: SafeArea(
        top: false,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 2),
          child: Row(
            children: [
              IconButton(
                tooltip: 'Página anterior',
                onPressed: onAnterior,
                icon: const Icon(Icons.chevron_left),
              ),
              Text(
                paginas == 1 ? '1 página' : '${pagina + 1} de $paginas',
                style: theme.textTheme.labelLarge,
              ),
              IconButton(
                tooltip: 'Página siguiente',
                onPressed: onSiguiente,
                icon: const Icon(Icons.chevron_right),
              ),
              const Spacer(),
              IconButton(
                tooltip: 'Alejar',
                onPressed: onAlejar,
                icon: const Icon(Icons.zoom_out),
              ),
              Text(
                '${(acercamiento * 100).round()}%',
                style: theme.textTheme.labelLarge,
              ),
              IconButton(
                tooltip: 'Acercar',
                onPressed: onAcercar,
                icon: const Icon(Icons.zoom_in),
              ),
              IconButton(
                tooltip: 'Ver la hoja entera',
                onPressed: onEncajar,
                icon: const Icon(Icons.fit_screen_outlined),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
