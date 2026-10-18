import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../../core/di/providers.dart';
import '../../../core/diseno/paleta.dart';
import '../application/courier_providers.dart';

class CourierRoutePage extends ConsumerStatefulWidget {
  const CourierRoutePage({super.key});

  @override
  ConsumerState<CourierRoutePage> createState() => _CourierRoutePageState();
}

class _CourierRoutePageState extends ConsumerState<CourierRoutePage> {
  int? _selected;

  @override
  Widget build(BuildContext context) {
    final route = ref.watch(courierRouteProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('Ruta optimizada')),
      body: RefreshIndicator(
        onRefresh: () => ref.refresh(courierRouteProvider.future),
        child: route.when(
          loading: () => const Center(child: CircularProgressIndicator()),
          error: (error, _) => ListView(
            children: [
              const SizedBox(height: 180),
              const Center(child: Text('No se pudo calcular la ruta')),
              Center(
                child: FilledButton(
                  onPressed: () => ref.invalidate(courierRouteProvider),
                  child: const Text('Reintentar'),
                ),
              ),
            ],
          ),
          data: (data) {
            final points = (data['puntos'] as List? ?? const [])
                .map((item) => Map<String, Object?>.from(item as Map))
                .toList(growable: false);
            final withoutLocation =
                data['asignacionesSinUbicacion'] as List? ?? const [];
            if (points.isEmpty) {
              return ListView(
                children: const [
                  SizedBox(height: 180),
                  Center(child: Text('No hay entregas activas con ubicación')),
                ],
              );
            }
            final selected = _selected == null
                ? points.first
                : points.firstWhere(
                    (point) => point['asignacionId'] == _selected,
                    orElse: () => points.first,
                  );
            return ListView(
              padding: const EdgeInsets.all(16),
              children: [
                Row(
                  children: [
                    Expanded(
                      child: _Summary(
                        label: 'Distancia estimada',
                        value: '${data['distanciaTotalKm'] ?? 0} km',
                      ),
                    ),
                    const SizedBox(width: 10),
                    Expanded(
                      child: _Summary(
                        label: 'Tiempo estimado',
                        value: '${data['tiempoEstimadoMinutos'] ?? 0} min',
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 12),
                Card(
                  clipBehavior: Clip.antiAlias,
                  child: SizedBox(
                    height: 260,
                    child: CustomPaint(
                      painter: _RoutePainter(
                        points: points,
                        selected: selected['asignacionId'] as int?,
                      ),
                      child: const Align(
                        alignment: Alignment.topLeft,
                        child: Padding(
                          padding: EdgeInsets.all(12),
                          child: Chip(
                            avatar: Icon(Icons.storefront_outlined, size: 16),
                            label: Text('Salida del local'),
                          ),
                        ),
                      ),
                    ),
                  ),
                ),
                const SizedBox(height: 8),
                Wrap(
                  spacing: 7,
                  children: [
                    for (final point in points)
                      ChoiceChip(
                        label: Text('Punto ${point['orden']}'),
                        selected:
                            selected['asignacionId'] == point['asignacionId'],
                        onSelected: (_) => setState(
                          () => _selected = point['asignacionId'] as int?,
                        ),
                      ),
                  ],
                ),
                const SizedBox(height: 12),
                Card(
                  child: Column(
                    children: [
                      ListTile(
                        leading: CircleAvatar(
                          child: Text('${selected['orden']}'),
                        ),
                        title: Text('Pedido #${selected['pedidoNumero']}'),
                        subtitle: Text(
                          '${selected['cliente']}\n${selected['distanciaDesdeAnteriorKm']} km desde el punto anterior',
                        ),
                      ),
                      Padding(
                        padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
                        child: Row(
                          children: [
                            Expanded(
                              child: OutlinedButton.icon(
                                onPressed: () => context.push(
                                  '/courier/deliveries/${selected['asignacionId']}',
                                ),
                                icon: const Icon(Icons.info_outline),
                                label: const Text('Detalle'),
                              ),
                            ),
                            const SizedBox(width: 8),
                            Expanded(
                              child: FilledButton.icon(
                                onPressed: () => _navigate(selected),
                                icon: const Icon(Icons.navigation_outlined),
                                label: const Text('Navegar'),
                              ),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
                const Divider(height: 28),
                Text(
                  'Orden sugerido',
                  style: Theme.of(context).textTheme.titleMedium,
                ),
                for (final point in points)
                  ListTile(
                    leading: CircleAvatar(child: Text('${point['orden']}')),
                    title: Text(
                      'Pedido #${point['pedidoNumero']} · ${point['cliente']}',
                    ),
                    subtitle: Text('${point['distanciaDesdeAnteriorKm']} km'),
                    selected: selected['asignacionId'] == point['asignacionId'],
                    onTap: () => setState(
                      () => _selected = point['asignacionId'] as int?,
                    ),
                  ),
                if (withoutLocation.isNotEmpty)
                  Card(
                    child: ListTile(
                      leading: const Icon(Icons.location_off_outlined),
                      title: Text(
                        '${withoutLocation.length} entrega(s) sin coordenadas',
                      ),
                      subtitle: const Text(
                        'Revisalas en el listado; no se incluyeron en el cálculo.',
                      ),
                    ),
                  ),
              ],
            );
          },
        ),
      ),
    );
  }

  Future<void> _navigate(Map<String, Object?> point) async {
    final uri = Uri.https('www.google.com', '/maps/dir/', {
      'api': '1',
      'destination': '${point['latitud']},${point['longitud']}',
    });
    // Igual que en la lista de entregas: abrir el mapa es un paso del
    // reparto, no una salida de la aplicación.
    await ref
        .read(appExcursionProvider)
        .durante(() => launchUrl(uri, mode: LaunchMode.externalApplication));
  }
}

class _Summary extends StatelessWidget {
  const _Summary({required this.label, required this.value});
  final String label;
  final String value;
  @override
  Widget build(BuildContext context) => Card(
    child: Padding(
      padding: const EdgeInsets.all(12),
      child: Column(
        children: [
          Text(value, style: Theme.of(context).textTheme.titleLarge),
          Text(label, textAlign: TextAlign.center),
        ],
      ),
    ),
  );
}

class _RoutePainter extends CustomPainter {
  const _RoutePainter({required this.points, required this.selected});
  final List<Map<String, Object?>> points;
  final int? selected;

  @override
  void paint(Canvas canvas, Size size) {
    final coordinates = points
        .map((point) {
          final lat = (point['latitud'] as num).toDouble();
          final lng = (point['longitud'] as num).toDouble();
          return (lat, lng);
        })
        .toList(growable: false);
    final minLat = coordinates.map((p) => p.$1).reduce(math.min);
    final maxLat = coordinates.map((p) => p.$1).reduce(math.max);
    final minLng = coordinates.map((p) => p.$2).reduce(math.min);
    final maxLng = coordinates.map((p) => p.$2).reduce(math.max);
    const padding = 34.0;
    Offset project((double, double) value) {
      final x =
          padding +
          (value.$2 - minLng) /
              math.max(maxLng - minLng, .00001) *
              (size.width - padding * 2);
      final y =
          padding +
          (maxLat - value.$1) /
              math.max(maxLat - minLat, .00001) *
              (size.height - padding * 2);
      return Offset(x, y);
    }

    final projected = coordinates.map(project).toList(growable: false);
    final line = Paint()
      ..color = Paleta.azulPrincipal.withValues(alpha: 0.7)
      ..strokeWidth = 3
      ..style = PaintingStyle.stroke;
    if (projected.length > 1) {
      final path = Path()..moveTo(projected.first.dx, projected.first.dy);
      for (final point in projected.skip(1)) {
        path.lineTo(point.dx, point.dy);
      }
      canvas.drawPath(path, line);
    }
    for (var index = 0; index < projected.length; index++) {
      final active = points[index]['asignacionId'] == selected;
      canvas.drawCircle(
        projected[index],
        active ? 15 : 12,
        Paint()..color = active ? Paleta.cian : Paleta.azulElectrico,
      );
      final text = TextPainter(
        text: TextSpan(
          text: '${points[index]['orden']}',
          style: TextStyle(
            // Sobre el cian, el blanco no se lee (2,1:1): ahí va tinta.
            color: active ? Paleta.tinta : Paleta.texto,
            fontWeight: FontWeight.bold,
          ),
        ),
        textDirection: TextDirection.ltr,
      )..layout();
      text.paint(
        canvas,
        projected[index] - Offset(text.width / 2, text.height / 2),
      );
    }
  }

  @override
  bool shouldRepaint(covariant _RoutePainter oldDelegate) =>
      oldDelegate.selected != selected || oldDelegate.points != points;
}
