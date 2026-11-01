import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';

import '../../../app/deep_links.dart';
import '../../../core/animacion/aparicion.dart';
import '../../../core/di/providers.dart';
import '../../../core/diseno/marca_tema.dart';
import '../../../core/diseno/medidas.dart';
import '../../../core/diseno/paleta.dart';
import '../../../core/errors/app_failure.dart';
import '../../../core/widgets/async_states.dart';
import '../../../core/widgets/titulo_de_pantalla.dart';

final notificationsProvider = FutureProvider.autoDispose((ref) async {
  final response = await ref
      .watch(apiClientProvider)
      .get<Object?>(
        '/api/v1/notificaciones',
        queryParameters: {'page': 0, 'size': 50},
      );
  final value = response.data;
  final raw = value is List
      ? value
      : value is Map && value['content'] is List
      ? value['content']! as List
      : const [];
  return raw
      .map((item) => Map<String, Object?>.from(item as Map))
      .toList(growable: false);
});

/// Cuántas quedan sin leer, para el contador de la campana.
///
/// Es una consulta aparte y liviana: la lista trae cincuenta notificaciones con
/// su contenido, y para pintar un número al lado de un icono alcanza con esto.
/// El coordinador de tiempo real la invalida cuando llega un aviso por la cola
/// personal, así que el número se mueve solo, también cuando la persona lee
/// desde otro dispositivo.
final unreadNotificationsProvider = FutureProvider.autoDispose<int>((
  ref,
) async {
  final response = await ref
      .watch(apiClientProvider)
      .get<Map<String, Object?>>('/api/v1/notificaciones/no-leidas');
  return (response.data?['noLeidas'] as num?)?.toInt() ?? 0;
});

/// El ícono y el color de cada tipo de aviso: se reconoce de qué se trata
/// antes de leerlo.
(IconData, Color) iconoDeAviso(BuildContext context, String? tipo) {
  final marca = MarcaTema.of(context);
  return switch (tipo) {
    'PEDIDO_CREADO' ||
    'PEDIDO_CONFIRMADO' => (Icons.receipt_long_outlined, Paleta.azulLuminoso),
    'PEDIDO_CANCELADO' => (Icons.cancel_outlined, marca.error),
    'PAGO_CONFIRMADO' => (Icons.verified_outlined, marca.exito),
    'PAGO_EN_VERIFICACION' => (Icons.fact_check_outlined, Paleta.cian),
    'PAGO_RECHAZADO' => (Icons.report_gmailerrorred_outlined, marca.error),
    'PEDIDO_PREPARADO' => (Icons.inventory_2_outlined, Paleta.azulLuminoso),
    'PEDIDO_LISTO_RETIRO' => (Icons.storefront_outlined, marca.exito),
    'ENVIO_DESPACHADO' ||
    'ENVIO_EN_CAMINO' => (Icons.local_shipping_outlined, Paleta.cian),
    'ENVIO_ENTREGADO' => (Icons.check_circle_outline, marca.exito),
    'MENSAJE_NUEVO' => (Icons.chat_bubble_outline_rounded, Paleta.azulLuminoso),
    'REPARTO_ASIGNADO' ||
    'REPARTO_REASIGNADO' ||
    'REPARTO_ACTUALIZADO' => (Icons.route_outlined, Paleta.cian),
    'TRANSFERENCIA_SOLICITADA' ||
    'TRANSFERENCIA_ACEPTADA' ||
    'TRANSFERENCIA_RECHAZADA' => (
      Icons.account_balance_wallet_outlined,
      marca.aviso,
    ),
    'VEHICULO_ASIGNADO' ||
    'VEHICULO_PROBLEMA' => (Icons.two_wheeler_outlined, marca.aviso),
    _ => (Icons.notifications_outlined, Paleta.textoSecundario),
  };
}

class NotificationsPage extends ConsumerStatefulWidget {
  const NotificationsPage({super.key});

  @override
  ConsumerState<NotificationsPage> createState() => _NotificationsPageState();
}

class _NotificationsPageState extends ConsumerState<NotificationsPage> {
  bool _soloSinLeer = false;

  @override
  Widget build(BuildContext context) {
    final notifications = ref.watch(notificationsProvider);
    final pendientes = ref.watch(unreadNotificationsProvider).value ?? 0;
    return Scaffold(
      appBar: AppBar(
        title: const TituloDePantalla('Notificaciones'),
        actions: [
          IconButton(
            // Apagado cuando no hay nada pendiente: un botón que se puede
            // apretar y no cambia nada deja pensando que algo falló.
            tooltip: 'Marcar todas como leídas',
            onPressed: pendientes == 0 ? null : _leerTodas,
            icon: const Icon(Icons.done_all_rounded),
          ),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: () async {
          ref
            ..invalidate(unreadNotificationsProvider)
            ..invalidate(notificationsProvider);
          await ref.read(notificationsProvider.future);
        },
        child: notifications.when(
          skipLoadingOnRefresh: true,
          loading: () => const AppLoading(label: 'Cargando tus avisos'),
          error: (error, _) => AppErrorState(
            message: error is AppFailure
                ? error.message
                : 'No pudimos cargar tus notificaciones.',
            onRetry: () => ref.invalidate(notificationsProvider),
          ),
          data: (todos) {
            final items = _soloSinLeer
                ? [
                    for (final a in todos)
                      if (a['leida'] != true) a,
                  ]
                : todos;
            return CustomScrollView(
              slivers: [
                SliverToBoxAdapter(
                  child: Padding(
                    padding: const EdgeInsets.fromLTRB(16, 8, 16, 4),
                    child: Wrap(
                      spacing: 8,
                      children: [
                        ChoiceChip(
                          label: const Text('Todas'),
                          selected: !_soloSinLeer,
                          onSelected: (_) =>
                              setState(() => _soloSinLeer = false),
                        ),
                        ChoiceChip(
                          label: Text(
                            pendientes == 0
                                ? 'Sin leer'
                                : 'Sin leer ($pendientes)',
                          ),
                          selected: _soloSinLeer,
                          onSelected: (_) =>
                              setState(() => _soloSinLeer = true),
                        ),
                      ],
                    ),
                  ),
                ),
                if (items.isEmpty)
                  SliverFillRemaining(
                    hasScrollBody: false,
                    child: AppEmptyState(
                      icon: _soloSinLeer
                          ? Icons.mark_email_read_outlined
                          : Icons.notifications_none,
                      message: _soloSinLeer
                          ? 'Estás al día: no tenés avisos sin leer.'
                          : 'Todavía no hay avisos.\n'
                                'Acá te vamos a contar cómo avanza tu pedido.',
                    ),
                  )
                else
                  SliverPadding(
                    padding: const EdgeInsets.fromLTRB(12, 4, 12, 32),
                    sliver: SliverList.builder(
                      itemCount: items.length,
                      itemBuilder: (context, index) {
                        final aviso = items[index];
                        final anterior = index == 0 ? null : items[index - 1];
                        return Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            if (_otroDia(anterior, aviso))
                              _Dia(fecha: _fecha(aviso)),
                            Aparicion(
                              orden: index.clamp(0, 6),
                              child: _Aviso(
                                aviso: aviso,
                                onAbrir: () => _abrir(aviso),
                                onLeer: () => _marcarLeida(aviso),
                              ),
                            ),
                          ],
                        );
                      },
                    ),
                  ),
              ],
            );
          },
        ),
      ),
    );
  }

  static DateTime? _fecha(Map<String, Object?> aviso) =>
      DateTime.tryParse(aviso['createdAt']?.toString() ?? '')?.toLocal();

  static bool _otroDia(
    Map<String, Object?>? anterior,
    Map<String, Object?> actual,
  ) {
    final a = _fecha(actual);
    if (a == null) return false;
    if (anterior == null) return true;
    final b = _fecha(anterior);
    return b == null ||
        b.year != a.year ||
        b.month != a.month ||
        b.day != a.day;
  }

  Future<void> _leerTodas() async {
    final mensajero = ScaffoldMessenger.of(context);
    try {
      await ref
          .read(apiClientProvider)
          .put<void>('/api/v1/notificaciones/leidas');
    } on AppFailure catch (falla) {
      mensajero
        ..hideCurrentSnackBar()
        ..showSnackBar(SnackBar(content: Text(falla.message)));
      return;
    }
    // El contador también: si no, la campana del inicio sigue mostrando un
    // número que ya no existe hasta que llegue otro aviso.
    ref
      ..invalidate(notificationsProvider)
      ..invalidate(unreadNotificationsProvider);
  }

  Future<bool> _marcarLeida(Map<String, Object?> aviso) async {
    if (aviso['leida'] == true) return true;
    try {
      await ref
          .read(apiClientProvider)
          .put<void>('/api/v1/notificaciones/${aviso['id']}/leida');
      ref
        ..invalidate(notificationsProvider)
        ..invalidate(unreadNotificationsProvider);
      return true;
    } on AppFailure catch (_) {
      // Que no se haya podido marcar no tiene por qué impedir abrir lo que
      // el aviso anuncia; el estado se vuelve a intentar al refrescar.
      return false;
    }
  }

  Future<void> _abrir(Map<String, Object?> aviso) async {
    await _marcarLeida(aviso);
    final enlace = aviso['deepLink']?.toString();
    final destino = enlace == null || enlace.isEmpty
        ? null
        : rutaDeEnlaceTechstore(Uri.parse(enlace));
    // El aviso genérico apunta al propio centro: no tiene sentido apilarlo
    // sobre sí mismo.
    if (destino != null && destino != '/notifications' && mounted) {
      await context.push<void>(destino);
    }
  }
}

class _Dia extends StatelessWidget {
  const _Dia({required this.fecha});

  final DateTime? fecha;

  @override
  Widget build(BuildContext context) {
    final f = fecha;
    if (f == null) return const SizedBox.shrink();
    final hoy = DateTime.now();
    final ayer = hoy.subtract(const Duration(days: 1));
    bool mismo(DateTime a, DateTime b) =>
        a.year == b.year && a.month == b.month && a.day == b.day;
    final texto = mismo(f, hoy)
        ? 'Hoy'
        : mismo(f, ayer)
        ? 'Ayer'
        : DateFormat("EEEE d 'de' MMMM", 'es_PY').format(f);
    return Padding(
      padding: const EdgeInsets.fromLTRB(6, 16, 6, 8),
      child: Text(
        texto[0].toUpperCase() + texto.substring(1),
        style: MarcaTema.of(context).rotulo
            .copyWith(color: Paleta.textoSecundario, fontSize: 12.5),
      ),
    );
  }
}

class _Aviso extends StatelessWidget {
  const _Aviso({
    required this.aviso,
    required this.onAbrir,
    required this.onLeer,
  });

  final Map<String, Object?> aviso;
  final VoidCallback onAbrir;
  final Future<bool> Function() onLeer;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final leida = aviso['leida'] == true;
    final titulo = aviso['titulo']?.toString() ?? 'Notificación';
    final mensaje = aviso['mensaje']?.toString() ?? '';
    final fecha = DateTime.tryParse(aviso['createdAt']?.toString() ?? '')
        ?.toLocal();
    final (icono, color) = iconoDeAviso(context, aviso['tipo']?.toString());
    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: Semantics(
        label: leida ? null : 'Sin leer',
        child: Material(
          // Sin leer se marca de tres maneras a la vez —el fondo, la negrita y
          // el punto— porque con el color solo no se distingue con la pantalla
          // al sol.
          color: leida ? Paleta.superficie : Paleta.azulProfundo,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(Medidas.radioControl),
            side: BorderSide(
              color: leida
                  ? Paleta.borde
                  : Paleta.azulPrincipal.withValues(alpha: .5),
            ),
          ),
          child: InkWell(
            borderRadius: BorderRadius.circular(Medidas.radioControl),
            onTap: onAbrir,
            child: Padding(
              padding: const EdgeInsets.fromLTRB(12, 12, 4, 12),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Container(
                    width: 40,
                    height: 40,
                    decoration: BoxDecoration(
                      color: color.withValues(alpha: leida ? .10 : .20),
                      shape: BoxShape.circle,
                    ),
                    child: Icon(
                      icono,
                      size: 20,
                      color: leida ? color.withValues(alpha: .7) : color,
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          children: [
                            Expanded(
                              child: Text(
                                titulo,
                                maxLines: 2,
                                overflow: TextOverflow.ellipsis,
                                style: theme.textTheme.titleSmall?.copyWith(
                                  fontWeight: leida
                                      ? FontWeight.w500
                                      : FontWeight.w800,
                                ),
                              ),
                            ),
                            if (fecha != null)
                              Padding(
                                padding: const EdgeInsets.only(left: 8),
                                child: Text(
                                  DateFormat.Hm('es_PY').format(fecha),
                                  style: theme.textTheme.labelSmall?.copyWith(
                                    color: Paleta.textoSecundario,
                                  ),
                                ),
                              ),
                          ],
                        ),
                        if (mensaje.isNotEmpty) ...[
                          const SizedBox(height: 3),
                          Text(
                            mensaje,
                            maxLines: 3,
                            overflow: TextOverflow.ellipsis,
                            style: theme.textTheme.bodySmall?.copyWith(
                              color: leida
                                  ? Paleta.textoSecundario
                                  : Paleta.texto,
                            ),
                          ),
                        ],
                      ],
                    ),
                  ),
                  if (leida)
                    const SizedBox(width: 12)
                  else
                    IconButton(
                      tooltip: 'Marcar como leída',
                      visualDensity: VisualDensity.compact,
                      onPressed: onLeer,
                      icon: const Icon(
                        Icons.circle,
                        size: 11,
                        color: Paleta.azulLuminoso,
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
