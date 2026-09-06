import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/di/providers.dart';
import '../../../core/errors/user_message.dart';
import '../../../core/native/document_service.dart';
import '../../../core/widgets/titulo_de_pantalla.dart';

class DocumentCenterPage extends ConsumerStatefulWidget {
  const DocumentCenterPage({super.key});

  @override
  ConsumerState<DocumentCenterPage> createState() => _DocumentCenterPageState();
}

class _DocumentCenterPageState extends ConsumerState<DocumentCenterPage> {
  late int _year;
  late int _month;
  bool _busy = false;

  @override
  void initState() {
    super.initState();
    final now = DateTime.now();
    _year = now.year;
    _month = now.month;
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const TituloDePantalla('Documentos y exportaciones')),
    body: ListView(
      padding: const EdgeInsets.all(16),
      children: [
        Text(
          'Registro de comprobantes DNIT',
          style: Theme.of(context).textTheme.titleLarge,
        ),
        const SizedBox(height: 8),
        const Text(
          'Genera el archivo ZIP oficial del período desde los datos centrales.',
        ),
        const SizedBox(height: 16),
        Row(
          children: [
            Expanded(
              child: DropdownButtonFormField<int>(
                initialValue: _month,
                decoration: const InputDecoration(labelText: 'Mes'),
                items: [
                  for (var month = 1; month <= 12; month++)
                    DropdownMenuItem(value: month, child: Text('$month')),
                ],
                onChanged: _busy
                    ? null
                    : (value) => setState(() => _month = value ?? _month),
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: DropdownButtonFormField<int>(
                initialValue: _year,
                decoration: const InputDecoration(labelText: 'Año'),
                items: [
                  for (
                    var year = DateTime.now().year;
                    year >= DateTime.now().year - 6;
                    year--
                  )
                    DropdownMenuItem(value: year, child: Text('$year')),
                ],
                onChanged: _busy
                    ? null
                    : (value) => setState(() => _year = value ?? _year),
              ),
            ),
          ],
        ),
        const SizedBox(height: 16),
        FilledButton.icon(
          onPressed: _busy ? null : _export,
          icon: const Icon(Icons.archive_outlined),
          label: const Text('Descargar y compartir ZIP'),
        ),
      ],
    ),
  );

  Future<void> _export() async {
    setState(() => _busy = true);
    try {
      final endpoint =
          '/api/v1/contabilidad/registro-comprobantes'
          '?anio=$_year&mes=$_month&formato=TXT&identificador=V0001&renta=IRE';
      await ref
          .read(documentServiceProvider)
          .handle(
            endpoint,
            'registro-comprobantes-$_year-${_month.toString().padLeft(2, '0')}.zip',
            DocumentAction.share,
          );
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              userMessageFor(
                error,
                fallback:
                    'No pudimos exportar el documento. '
                    'Revisá tu conexión e intentá de nuevo.',
              ),
            ),
          ),
        );
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }
}
