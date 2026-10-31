import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';

import '../../../core/di/providers.dart';
import '../../../core/diseno/movimiento.dart';
import '../../../core/errors/app_failure.dart';
import '../../../core/native/haptics_service.dart';
import '../../../core/offline/offline_queue_service.dart';
import '../../../core/widgets/async_states.dart';
import '../../../core/widgets/voice_dictation_button.dart';
import '../../auth/application/session_controller.dart';
import '../../auth/domain/auth_user.dart';
import '../data/chat_repository.dart';
import 'new_conversation_page.dart';

final chatRepositoryProvider = Provider<ChatRepository>((ref) {
  final email = ref.watch(authSessionProvider).user?.email ?? 'anonymous';
  final token = ref.read(authSessionProvider.notifier).accessToken;
  return ChatRepository(
    ref.watch(apiClientProvider),
    ref.watch(offlineQueueProvider),
    offlineScopeForSession(token, email),
  );
});

final conversationsProvider = FutureProvider.autoDispose(
  (ref) => ref.watch(chatRepositoryProvider).conversations(),
);

final messagesProvider = FutureProvider.autoDispose.family(
  (ref, int id) => ref.watch(chatRepositoryProvider).messages(id),
);

/// La conversación puntual, para saber con quién se está hablando.
final conversationProvider = FutureProvider.autoDispose.family(
  (ref, int id) => ref.watch(chatRepositoryProvider).conversation(id),
);

/// Cómo se llama la atención de la tienda para quien compra.
///
/// El cliente no tiene por qué saber que del otro lado hay un administrador:
/// eso es organización interna. Ve un canal con la tienda.
const nombreAtencionAlCliente = 'Atención al Cliente';

class ChatPage extends ConsumerWidget {
  const ChatPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final rol = ref.watch(authSessionProvider).user?.role;
    // Quien compra tiene un solo canal con la tienda y no elige con quién
    // habla. El resto —incluido el repartidor— tiene una bandeja y puede
    // empezar conversaciones nuevas dentro de lo que su rol permita.
    return rol == null || rol == AppRole.customer
        ? const _AtencionAlCliente()
        : const _Bandeja();
  }
}

/// Lo que ve el cliente: la conversación con la tienda, directamente.
///
/// Antes «Atención al Cliente» abría una lista con una sola tarjeta, y había
/// que tocarla para llegar al chat: un paso que no decidía nada. Ahora se abre
/// la conversación general; las consultas atadas a un pedido se retoman desde
/// ese pedido, o desde el botón de la barra si hay alguna.
class _AtencionAlCliente extends ConsumerWidget {
  const _AtencionAlCliente();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final conversaciones = ref.watch(conversationsProvider);
    return conversaciones.when(
      skipLoadingOnRefresh: true,
      loading: () => Scaffold(
        appBar: AppBar(title: const Text(nombreAtencionAlCliente)),
        body: const AppLoading(label: 'Abriendo tu conversación'),
      ),
      // Sin poder leer las anteriores igual se puede escribir: el primer
      // mensaje abre el canal, y si ya existía el servidor lo retoma.
      error: (error, _) => const ConversationPage(),
      data: (items) {
        final general = items.where((c) => c['pedidoId'] == null).firstOrNull;
        final porPedido = [
          for (final c in items)
            if (c['pedidoId'] != null) c,
        ];
        final id = (general?['id'] as num?)?.toInt();
        return ConversationPage(
          key: ValueKey(id),
          conversationId: id,
          otherConversations: porPedido,
        );
      },
    );
  }
}

/// La bandeja de quien trabaja en la tienda.
///
/// Mezcla dos cosas que el servidor ya distingue: las consultas de clientes
/// —que atiende el mostrador— y las conversaciones privadas de esa persona.
/// El título y el rol vienen resueltos de la API, así que acá no se decide qué
/// se le muestra a quién.
class _Bandeja extends ConsumerWidget {
  const _Bandeja();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final conversaciones = ref.watch(conversationsProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('Conversaciones')),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => context.push('/chat/destinatarios'),
        icon: const Icon(Icons.edit_outlined),
        label: const Text('Nueva conversación'),
      ),
      body: RefreshIndicator(
        onRefresh: () => ref.refresh(conversationsProvider.future),
        child: conversaciones.when(
          loading: () => const AppLoading(label: 'Cargando conversaciones'),
          error: (error, _) => AppErrorState(
            message: error is AppFailure
                ? error.message
                : 'No pudimos cargar las conversaciones.',
            onRetry: () => ref.invalidate(conversationsProvider),
          ),
          data: (items) => items.isEmpty
              ? const _BandejaVacia()
              : _BandejaLlena(items: items),
        ),
      ),
    );
  }
}

class _BandejaVacia extends StatelessWidget {
  const _BandejaVacia();

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return ListView(
      children: [
        const SizedBox(height: 140),
        Center(
          child: Padding(
            padding: const EdgeInsets.all(32),
            child: Column(
              children: [
                ExcludeSemantics(
                  child: Icon(
                    Icons.forum_outlined,
                    size: 52,
                    color: theme.colorScheme.onSurfaceVariant,
                  ),
                ),
                const SizedBox(height: 12),
                Text(
                  'Todavía no hay conversaciones',
                  style: theme.textTheme.titleSmall,
                ),
                const SizedBox(height: 6),
                Text(
                  'Cuando un cliente escriba, aparece acá. También podés '
                  'empezar una vos.',
                  textAlign: TextAlign.center,
                  style: theme.textTheme.bodyMedium?.copyWith(
                    color: theme.colorScheme.onSurfaceVariant,
                  ),
                ),
              ],
            ),
          ),
        ),
      ],
    );
  }
}

class _BandejaLlena extends StatelessWidget {
  const _BandejaLlena({required this.items});

  final List<Map<String, Object?>> items;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return ListView.separated(
      // Espacio al final para que el botón flotante no tape la última fila.
      padding: const EdgeInsets.only(bottom: 88),
      itemCount: items.length,
      separatorBuilder: (_, _) => const Divider(height: 1),
      itemBuilder: (context, index) {
        final item = items[index];
        final sinLeer = (item['noLeidos'] as num?)?.toInt() ?? 0;
        final ultimo = item['ultimoMensaje'] as Map?;
        final pedido = (item['pedidoNumero'] as num?)?.toInt();
        final esSoporte = item['tipo']?.toString() == 'SOPORTE';
        final rol = item['rolContraparte']?.toString();
        return ListTile(
          leading: Badge(
            isLabelVisible: sinLeer > 0,
            label: Text('$sinLeer'),
            child: CircleAvatar(
              backgroundColor: esSoporte
                  ? theme.colorScheme.secondaryContainer
                  : theme.colorScheme.primaryContainer,
              child: Icon(
                esSoporte ? Icons.support_agent_outlined : Icons.person_outline,
                color: theme.colorScheme.onPrimaryContainer,
              ),
            ),
          ),
          title: Row(
            children: [
              Expanded(
                child: Text(
                  item['titulo']?.toString() ?? 'Conversación',
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
              ),
              if (rol != null && rol.isNotEmpty)
                Padding(
                  padding: const EdgeInsets.only(left: 8),
                  child: Text(
                    etiquetaDeRol(rol),
                    style: theme.textTheme.labelSmall?.copyWith(
                      color: theme.colorScheme.onSurfaceVariant,
                    ),
                  ),
                ),
            ],
          ),
          subtitle: Text(
            [
              if (esSoporte) 'Consulta de cliente',
              if (pedido != null) 'Pedido N.º $pedido',
              ultimo?['contenido']?.toString() ?? 'Sin mensajes',
            ].join(' · '),
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
          ),
          onTap: () => context.push('/chat/${item['id']}'),
        );
      },
    );
  }
}

class ConversationPage extends ConsumerStatefulWidget {
  const ConversationPage({
    super.key,
    this.conversationId,
    this.recipientId,
    this.orderId,
    this.otherConversations = const [],
  });

  /// El pedido por el que se consulta, cuando la conversación todavía no
  /// existe: el primer mensaje la abre atada a ese pedido.
  final int? orderId;

  /// Las otras consultas del cliente (las atadas a un pedido), para poder
  /// saltar a ellas desde la barra.
  final List<Map<String, Object?>> otherConversations;

  /// `null` cuando todavía no se escribió: el primer mensaje abre la
  /// conversación. Así no hay que pedir un texto en un diálogo antes de
  /// mostrar la pantalla.
  final int? conversationId;

  /// Con quién se va a hablar, cuando la conversación todavía no existe.
  final int? recipientId;

  @override
  ConsumerState<ConversationPage> createState() => _ConversationPageState();
}

class _ConversationPageState extends ConsumerState<ConversationPage> {
  final _message = TextEditingController();
  final _scroll = ScrollController();
  bool _busy = false;
  int? _id;

  /// Si en esta pantalla quedó algún mensaje en la cola sin conexión. Recién
  /// ahí se mira la cola: abrir la base local cada vez que se entra al chat,
  /// para mostrar un aviso que casi nunca aplica, era trabajo de más.
  bool _hayCola = false;

  @override
  void initState() {
    super.initState();
    _id = widget.conversationId;
    final abierta = _id;
    if (abierta != null) {
      Future.microtask(
        () => ref.read(chatRepositoryProvider).markRead(abierta),
      );
    }
  }

  @override
  void dispose() {
    _message.dispose();
    _scroll.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final usuario = ref.watch(authSessionProvider).user;
    final esCliente = usuario?.role == AppRole.customer;
    final abierta = _id;
    final repositorio = ref.watch(chatRepositoryProvider);
    final pedidoAsociado = abierta == null
        ? widget.orderId
        : (ref.watch(conversationProvider(abierta)).value?['pedidoNumero']
                  as num?)
              ?.toInt();
    return Scaffold(
      appBar: AppBar(
        title: _Titulo(conversationId: abierta, esCliente: esCliente),
        actions: [
          if (widget.otherConversations.isNotEmpty)
            IconButton(
              tooltip: 'Consultas por pedidos',
              onPressed: () => _otrasConsultas(context),
              icon: Badge(
                isLabelVisible: widget.otherConversations.any(
                  (c) => ((c['noLeidos'] as num?)?.toInt() ?? 0) > 0,
                ),
                child: const Icon(Icons.forum_outlined),
              ),
            ),
        ],
      ),
      body: Column(
        children: [
          if (pedidoAsociado != null)
            Material(
              color: Theme.of(context).colorScheme.surfaceContainerHighest,
              child: ListTile(
                dense: true,
                leading: const Icon(Icons.receipt_long_outlined, size: 20),
                title: Text(
                  abierta == null
                      ? 'Consulta sobre tu pedido'
                      : 'Consulta sobre el pedido N.º $pedidoAsociado',
                ),
              ),
            ),
          // Los mensajes que se escribieron sin conexión quedan en cola: se
          // dice cuántos son y se pueden reintentar, en vez de que desaparezcan
          // en silencio hasta que vuelva la red.
          if (_hayCola)
            StreamBuilder<int>(
              stream: repositorio.pendingCount(),
              builder: (context, snapshot) {
                final pendientes = snapshot.data ?? 0;
                if (pendientes == 0) return const SizedBox.shrink();
                return Material(
                  color: Theme.of(context).colorScheme.secondaryContainer,
                  child: ListTile(
                    dense: true,
                    leading: const Icon(Icons.cloud_off_outlined, size: 20),
                    title: Text(
                      pendientes == 1
                          ? '1 mensaje sin enviar'
                          : '$pendientes mensajes sin enviar',
                    ),
                    subtitle: const Text(
                      'Se mandan solos cuando vuelva la conexión.',
                    ),
                    trailing: TextButton(
                      onPressed: () async {
                        await repositorio.retryPending();
                        final id = _id;
                        if (id != null) ref.invalidate(messagesProvider(id));
                      },
                      child: const Text('Reintentar'),
                    ),
                  ),
                );
              },
            ),
          Expanded(
            child: abierta == null
                ? _Vacio(esCliente: esCliente)
                : _Historial(
                    conversationId: abierta,
                    emailPropio: usuario?.email ?? '',
                    scroll: _scroll,
                  ),
          ),
          SafeArea(
            top: false,
            child: Padding(
              padding: const EdgeInsets.all(12),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  Expanded(
                    child: TextField(
                      controller: _message,
                      maxLength: 2000,
                      minLines: 1,
                      maxLines: 4,
                      textInputAction: TextInputAction.newline,
                      autofocus: abierta == null,
                      decoration: const InputDecoration(
                        labelText: 'Mensaje',
                        counterText: '',
                      ),
                      // Habilita el botón en cuanto hay algo escrito.
                      onChanged: (_) => setState(() {}),
                    ),
                  ),
                  VoiceDictationButton(
                    controller: _message,
                    tooltip: 'Dictar mensaje',
                    append: true,
                    dictation: true,
                  ),
                  IconButton.filled(
                    tooltip: 'Enviar',
                    onPressed: _busy || _message.text.trim().isEmpty
                        ? null
                        : _send,
                    icon: _busy
                        ? const SizedBox(
                            width: 20,
                            height: 20,
                            child: CircularProgressIndicator(strokeWidth: 2),
                          )
                        : const Icon(Icons.send),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Future<void> _send() async {
    final texto = _message.text.trim();
    if (texto.isEmpty) return;
    setState(() => _busy = true);
    final haptics = ref.read(hapticsServiceProvider);
    try {
      final repositorio = ref.read(chatRepositoryProvider);
      final abierta = _id;
      if (abierta == null) {
        // Todavía no hay conversación: este mensaje la abre.
        final creada = await repositorio.create(
          message: texto,
          recipientId: widget.recipientId,
          orderId: widget.orderId,
        );
        _message.clear();
        await haptics.play(HapticCue.success);
        ref.invalidate(conversationsProvider);
        if (mounted) setState(() => _id = creada);
        return;
      }
      final resultado = await repositorio.send(abierta, texto);
      _message.clear();
      if (resultado == ChatSendResult.sent) {
        await haptics.play(HapticCue.success);
        ref.invalidate(messagesProvider(abierta));
        ref.invalidate(conversationsProvider);
        _alUltimo();
      } else {
        await haptics.play(HapticCue.warning);
        if (mounted) setState(() => _hayCola = true);
        _avisar('Mensaje guardado. Se enviará al reconectar.');
      }
    } on AppFailure catch (falla) {
      await haptics.play(HapticCue.error);
      _avisar(falla.message);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _otrasConsultas(BuildContext context) =>
      showModalBottomSheet<void>(
        context: context,
        showDragHandle: true,
        builder: (sheet) => SafeArea(
          child: ListView(
            shrinkWrap: true,
            children: [
              Padding(
                padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
                child: Text(
                  'Tus consultas por pedidos',
                  style: Theme.of(sheet).textTheme.titleMedium,
                ),
              ),
              for (final c in widget.otherConversations)
                ListTile(
                  leading: Badge(
                    isLabelVisible: ((c['noLeidos'] as num?)?.toInt() ?? 0) > 0,
                    child: const Icon(Icons.receipt_long_outlined),
                  ),
                  title: Text('Pedido N.º ${c['pedidoNumero'] ?? ''}'),
                  subtitle: Text(
                    (c['ultimoMensaje'] as Map?)?['contenido']?.toString() ??
                        '',
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                  onTap: () {
                    Navigator.pop(sheet);
                    context.push('/chat/${c['id']}');
                  },
                ),
            ],
          ),
        ),
      );

  /// Lleva la vista al último mensaje.
  ///
  /// La lista se dibuja invertida, así que el final es el desplazamiento cero.
  void _alUltimo() {
    if (!_scroll.hasClients) return;
    // Sin animaciones pedidas por el sistema, salta directo al final.
    if (Movimiento.reducido(context)) {
      _scroll.jumpTo(0);
      return;
    }
    _scroll.animateTo(
      0,
      duration: const Duration(milliseconds: 250),
      curve: Curves.easeOut,
    );
  }

  void _avisar(String mensaje) {
    if (!mounted) return;
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(mensaje)));
  }
}

/// El encabezado: con quién se está hablando y, si corresponde, su rol.
///
/// Al cliente le dice siempre «Atención al Cliente», porque el servidor le
/// manda ese título y no el nombre de quien atiende.
class _Titulo extends ConsumerWidget {
  const _Titulo({required this.conversationId, required this.esCliente});

  final int? conversationId;
  final bool esCliente;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final theme = Theme.of(context);
    final id = conversationId;
    if (id == null) {
      return Text(esCliente ? nombreAtencionAlCliente : 'Nueva conversación');
    }
    final conversacion = ref.watch(conversationProvider(id));
    return conversacion.maybeWhen(
      data: (datos) {
        final titulo =
            datos['titulo']?.toString() ??
            (esCliente ? nombreAtencionAlCliente : 'Conversación');
        final rol = datos['rolContraparte']?.toString();
        if (rol == null || rol.isEmpty) return Text(titulo);
        return Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Text(titulo, style: theme.textTheme.titleMedium),
            Text(
              etiquetaDeRol(rol),
              style: theme.textTheme.labelSmall?.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
              ),
            ),
          ],
        );
      },
      orElse: () => Text(esCliente ? nombreAtencionAlCliente : 'Conversación'),
    );
  }
}

class _Vacio extends StatelessWidget {
  const _Vacio({required this.esCliente});

  final bool esCliente;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            ExcludeSemantics(
              child: Icon(
                Icons.support_agent_outlined,
                size: 56,
                color: theme.colorScheme.onSurfaceVariant,
              ),
            ),
            const SizedBox(height: 12),
            Text(
              esCliente
                  ? 'Contanos en qué te podemos ayudar'
                  : 'Todavía no hay mensajes',
              textAlign: TextAlign.center,
              style: theme.textTheme.titleSmall,
            ),
            const SizedBox(height: 6),
            Text(
              'Escribí abajo y te respondemos.',
              textAlign: TextAlign.center,
              style: theme.textTheme.bodyMedium?.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// El historial, con los mensajes propios diferenciados de los recibidos.
class _Historial extends ConsumerWidget {
  const _Historial({
    required this.conversationId,
    required this.emailPropio,
    required this.scroll,
  });

  final int conversationId;
  final String emailPropio;
  final ScrollController scroll;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final mensajes = ref.watch(messagesProvider(conversationId));
    return mensajes.when(
      loading: () => const AppLoading(label: 'Cargando mensajes'),
      error: (error, _) => AppErrorState(
        message: error is AppFailure
            ? error.message
            : 'No pudimos cargar los mensajes.',
        onRetry: () => ref.invalidate(messagesProvider(conversationId)),
      ),
      data: (items) => items.isEmpty
          ? const _Vacio(esCliente: true)
          : RefreshIndicator(
              onRefresh: () =>
                  ref.refresh(messagesProvider(conversationId).future),
              // `reverse` deja el último mensaje a la vista sin tener que
              // animar un scroll después de construir.
              child: ListView.builder(
                controller: scroll,
                reverse: true,
                padding: const EdgeInsets.fromLTRB(12, 12, 12, 4),
                itemCount: items.length,
                itemBuilder: (context, index) {
                  // Con `reverse`, el índice 0 se dibuja abajo, y el servidor
                  // manda los mensajes del más nuevo al más viejo. Así que el
                  // índice va derecho: dar vuelta la lista además ponía el
                  // mensaje más viejo al final de la conversación.
                  final mensaje = items[index];
                  final propio =
                      emailPropio.isNotEmpty &&
                      mensaje['remitenteEmail']?.toString().toLowerCase() ==
                          emailPropio.toLowerCase();
                  // El anterior en el tiempo, para saber si arriba de este
                  // mensaje va la fecha.
                  final anterior = index + 1 < items.length
                      ? items[index + 1]
                      : null;
                  return Column(
                    children: [
                      if (_cambiaElDia(anterior, mensaje))
                        _SeparadorDeDia(mensaje: mensaje),
                      _Burbuja(mensaje: mensaje, propio: propio),
                    ],
                  );
                },
              ),
            ),
    );
  }

  /// Si entre un mensaje y el anterior cambió la fecha, para separarlos.
  static bool _cambiaElDia(
    Map<String, Object?>? anterior,
    Map<String, Object?> actual,
  ) {
    final fechaActual = _fecha(actual);
    if (fechaActual == null) return false;
    if (anterior == null) return true;
    final fechaAnterior = _fecha(anterior);
    if (fechaAnterior == null) return true;
    return fechaAnterior.year != fechaActual.year ||
        fechaAnterior.month != fechaActual.month ||
        fechaAnterior.day != fechaActual.day;
  }

  static DateTime? _fecha(Map<String, Object?> mensaje) =>
      DateTime.tryParse(mensaje['enviadoEn']?.toString() ?? '')?.toLocal();
}

class _SeparadorDeDia extends StatelessWidget {
  const _SeparadorDeDia({required this.mensaje});

  final Map<String, Object?> mensaje;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final fecha = _Historial._fecha(mensaje);
    if (fecha == null) return const SizedBox.shrink();
    final hoy = DateTime.now();
    final esHoy =
        fecha.year == hoy.year &&
        fecha.month == hoy.month &&
        fecha.day == hoy.day;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 10),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
        decoration: BoxDecoration(
          color: theme.colorScheme.surfaceContainerHighest,
          borderRadius: BorderRadius.circular(20),
        ),
        child: Text(
          esHoy ? 'Hoy' : DateFormat('d MMM y', 'es_PY').format(fecha),
          style: theme.textTheme.labelSmall?.copyWith(
            color: theme.colorScheme.onSurfaceVariant,
          ),
        ),
      ),
    );
  }
}

class _Burbuja extends StatelessWidget {
  const _Burbuja({required this.mensaje, required this.propio});

  final Map<String, Object?> mensaje;
  final bool propio;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final enviado = DateTime.tryParse(mensaje['enviadoEn']?.toString() ?? '');
    final leido = mensaje['leidoEn'] != null;
    return Align(
      alignment: propio ? Alignment.centerRight : Alignment.centerLeft,
      child: Container(
        constraints: const BoxConstraints(maxWidth: 320),
        margin: const EdgeInsets.only(bottom: 8),
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
        decoration: BoxDecoration(
          color: propio
              ? theme.colorScheme.primaryContainer
              : theme.colorScheme.surfaceContainerHighest,
          borderRadius: BorderRadius.only(
            topLeft: const Radius.circular(14),
            topRight: const Radius.circular(14),
            bottomLeft: Radius.circular(propio ? 14 : 4),
            bottomRight: Radius.circular(propio ? 4 : 14),
          ),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            if (!propio)
              Text(
                mensaje['remitenteNombre']?.toString() ?? 'La tienda',
                style: theme.textTheme.labelSmall?.copyWith(
                  color: theme.colorScheme.primary,
                ),
              ),
            Text(
              mensaje['contenido']?.toString() ?? '',
              style: theme.textTheme.bodyMedium?.copyWith(
                color: propio ? theme.colorScheme.onPrimaryContainer : null,
              ),
            ),
            if (enviado != null)
              Padding(
                padding: const EdgeInsets.only(top: 4),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Text(
                      DateFormat.Hm('es_PY').format(enviado.toLocal()),
                      style: theme.textTheme.labelSmall?.copyWith(
                        color: theme.colorScheme.onSurfaceVariant,
                      ),
                    ),
                    if (propio)
                      Padding(
                        padding: const EdgeInsets.only(left: 4),
                        child: Icon(
                          leido ? Icons.done_all : Icons.done,
                          size: 14,
                          color: leido
                              ? theme.colorScheme.primary
                              : theme.colorScheme.onSurfaceVariant,
                          semanticLabel: leido ? 'Leído' : 'Enviado',
                        ),
                      ),
                  ],
                ),
              ),
          ],
        ),
      ),
    );
  }
}
