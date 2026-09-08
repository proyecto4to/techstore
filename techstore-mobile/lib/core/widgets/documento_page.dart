import 'dart:async';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:printing/printing.dart';

import '../di/providers.dart';
import '../errors/app_failure.dart';
import 'async_states.dart';
import 'titulo_de_pantalla.dart';
import 'visor_de_documento.dart';

/// Ver un documento del servidor dentro de la aplicación.
///
/// Es la misma pantalla para una factura, un comprobante de compra o cualquier
/// otro PDF: se descarga una vez y desde ahí se puede leer, compartir o
/// imprimir sin volver a pedirlo. Antes cada lugar armaba su propia vista
/// previa, y ninguna dejaba acercarse a leer.
class DocumentoPage extends ConsumerStatefulWidget {
  const DocumentoPage({
    required this.titulo,
    required this.endpoint,
    required this.nombreDeArchivo,
    super.key,
    this.sePuedeImprimir = true,
  });

  /// Lo que se lee en la barra: «Factura 001-001-0000046».
  final String titulo;

  /// De dónde se baja el PDF.
  final String endpoint;

  /// Con qué nombre se comparte o se guarda.
  final String nombreDeArchivo;

  final bool sePuedeImprimir;

  @override
  ConsumerState<DocumentoPage> createState() => _DocumentoPageState();
}

class _DocumentoPageState extends ConsumerState<DocumentoPage> {
  Uint8List? _pdf;
  final _paginas = <PaginaDeDocumento>[];
  String? _error;
  StreamSubscription<PaginaDeDocumento>? _dibujo;
  bool _terminado = false;

  @override
  void initState() {
    super.initState();
    unawaited(_traer());
  }

  @override
  void dispose() {
    unawaited(_dibujo?.cancel());
    super.dispose();
  }

  Future<void> _traer() async {
    setState(() {
      _error = null;
      _terminado = false;
      _paginas.clear();
    });
    try {
      final pdf = await ref
          .read(documentServiceProvider)
          .bytes(widget.endpoint);
      if (!mounted) return;
      _pdf = pdf;
      // Las páginas se van mostrando a medida que se dibujan: en un documento
      // de varias hojas, esperar a que estén todas es mirar una pantalla en
      // blanco sin saber si algo está pasando.
      _dibujo = dibujarDocumento(pdf).listen(
        (pagina) {
          if (!mounted) return;
          setState(() => _paginas.add(pagina));
        },
        onDone: () {
          if (mounted) setState(() => _terminado = true);
        },
        onError: (Object _) {
          if (!mounted) return;
          setState(() {
            _error = 'No pudimos abrir este documento.';
            _terminado = true;
          });
        },
      );
    } on AppFailure catch (falla) {
      if (mounted) setState(() => _error = falla.message);
    } catch (_) {
      if (mounted) {
        setState(() => _error = 'No pudimos descargar el documento.');
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final pdf = _pdf;
    return Scaffold(
      appBar: AppBar(
        title: TituloDePantalla(widget.titulo),
        actions: [
          if (pdf != null) ...[
            // Las dos abren algo del sistema —el selector de aplicaciones y el
            // diálogo de impresión— y eso manda la app a segundo plano. Se
            // marcan como salida propia para que no se cierre la sesión.
            IconButton(
              tooltip: 'Compartir',
              onPressed: () => unawaited(
                ref
                    .read(appExcursionProvider)
                    .durante(
                      () => Printing.sharePdf(
                        bytes: pdf,
                        filename: widget.nombreDeArchivo,
                      ),
                    ),
              ),
              icon: const Icon(Icons.share_outlined),
            ),
            if (widget.sePuedeImprimir)
              IconButton(
                tooltip: 'Imprimir',
                onPressed: () => unawaited(
                  ref
                      .read(appExcursionProvider)
                      .durante(
                        () => Printing.layoutPdf(onLayout: (_) async => pdf),
                      ),
                ),
                icon: const Icon(Icons.print_outlined),
              ),
          ],
        ],
      ),
      body: _cuerpo(),
    );
  }

  Widget _cuerpo() {
    if (_error != null && _paginas.isEmpty) {
      return AppErrorState(
        message: _error!,
        onRetry: () => unawaited(_traer()),
      );
    }
    if (_paginas.isEmpty) {
      return const AppLoading(label: 'Preparando el documento');
    }
    return Column(
      children: [
        if (!_terminado) const LinearProgressIndicator(),
        Expanded(child: VisorDeDocumento(paginas: List.of(_paginas))),
      ],
    );
  }
}
