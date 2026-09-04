import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';

import '../../../core/di/providers.dart';
import '../../../core/errors/app_failure.dart';
import '../../../core/native/haptics_service.dart';
import '../../../core/offline/offline_queue_service.dart';
import '../../../core/widgets/async_states.dart';
import '../../../core/widgets/voice_dictation_button.dart';
import '../../auth/application/session_controller.dart';
import '../data/chat_repository.dart';

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
    // Quien opera la tienda contesta las conversaciones que le llegan; quien
    // compra tiene una sola, con la tienda.
    return rol != null && rol.canOperateStore
        ? const _BandejaDeLaTienda()
        : const _AtencionAlCliente();
  }
}

/// Lo que ve el cliente: un solo canal, sin elegir con quién habla.
class _AtencionAlCliente extends ConsumerWidget {
  const _AtencionAlCliente();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final theme = Theme.of(context);
    final conversaciones = ref.watch(conversationsProvider);
    return Scaffold(
      appBar: AppBar(title: const Text(nombreAtencionAlCliente)),
      body: RefreshIndicator(
        onRefresh: () => ref.refresh(conversationsProvider.future),
        child: conversaciones.when(
          loading: () => const AppLoading(label: 'Abriendo tu conversación'),
          // Sin conversación previa el error no bloquea: igual se puede
          // escribir, y el primer mensaje abre el canal.
          error: (error, _) => _lista(context, ref, null, theme),
          data: (items) =>
              _lista(context, ref, items.isEmpty ? null : items.first, theme),
        ),
      ),
    );
  }

  Widget _lista(
    BuildContext context,
    WidgetRef ref,
    Map<String, Object?>? conversacion,
    ThemeData theme,
  ) {
    final sinLeer = (conversacion?['noLeidos'] as num?)?.toInt() ?? 0;
    final ultimo = conversacion?['ultimoMensaje'] as Map?;
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        Card(
          child: ListTile(
            contentPadding: const EdgeInsets.symmetric(
              horizontal: 16,
              vertical: 8,
            ),
            leading: Badge(
              isLabelVisible: sinLeer > 0,
              label: Text('$sinLeer'),
              child: CircleAvatar(
                backgroundColor: theme.colorScheme.primaryContainer,
                child: Icon(
                  Icons.support_agent_outlined,
                  color: theme.colorScheme.onPrimaryContainer,
                ),
              ),
            ),
            title: const Text(nombreAtencionAlCliente),
            subtitle: Text(
              ultimo?['contenido']?.toString() ??
                  'Escribinos y te respondemos.',
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
            ),
            trailing: const Icon(Icons.chevron_right),
            onTap: () => context.push(
              conversacion == null
                  ? '/chat/nuevo'
                  : '/chat/${conversacion['id']}',
            ),
          ),
        ),
        const SizedBox(height: 12),
        Text(
          'Te contestamos en el horario de la tienda.',
          textAlign: TextAlign.center,
          style: theme.textTheme.bodySmall?.copyWith(
            color: theme.colorScheme.onSurfaceVariant,
          ),
        ),
      ],
    );
  }
}

/// Lo que ve la tienda: las conversaciones de sus clientes.
///
/// No hay botón de «Nueva»: el servidor solo deja que la conversación la inicie
/// el cliente, así que ese botón únicamente podía terminar en un error.
class _BandejaDeLaTienda extends ConsumerWidget {
  const _BandejaDeLaTienda();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final conversaciones = ref.watch(conversationsProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('Conversaciones')),
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
              ? ListView(
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
                                color: Theme.of(context)
                                    .colorScheme
                                    .onSurfaceVariant,
                              ),
                            ),
                            const SizedBox(height: 12),
                            const Text('Ningún cliente escribió todavía'),
                          ],
                        ),
                      ),
                    ),
                  ],
                )
              : ListView.builder(
                  itemCount: items.length,
                  itemBuilder: (context, index) {
                    final item = items[index];
                    final sinLeer = (item['noLeidos'] as num?)?.toInt() ?? 0;
                    final ultimo = item['ultimoMensaje'] as Map?;
                    final pedido = (item['pedidoNumero'] as num?)?.toInt();
                    return ListTile(
                      leading: Badge(
                        isLabelVisible: sinLeer > 0,
                        label: Text('$sinLeer'),
                        child: const CircleAvatar(
                          child: Icon(Icons.person_outline),
                        ),
                      ),
                      title: Text(
                        item['clienteNombre']?.toString() ?? 'Cliente',
                      ),
                      subtitle: Text(
                        [
                          if (pedido != null) 'Pedido N.º $pedido',
                          ultimo?['contenido']?.toString() ?? 'Sin mensajes',
                        ].join(' · '),
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                      ),
                      onTap: () => context.push('/chat/${item['id']}'),
                    );
                  },
                ),
        ),
      ),
    );
  }
}

class ConversationPage extends ConsumerStatefulWidget {
  const ConversationPage({super.key, this.conversationId});

  /// `null` cuando el cliente todavía no escribió: el primer mensaje abre la
  /// conversación. Así no hay que pedirle un texto en un diálogo antes de
  /// mostrarle la pantalla.
  final int? conversationId;

  @override
  ConsumerState<ConversationPage> createState() => _ConversationPageState();
}

class _ConversationPageState extends ConsumerState<ConversationPage> {
  final _message = TextEditingController();
  bool _busy = false;
  int? _id;

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
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final usuario = ref.watch(authSessionProvider).user;
    final esCliente = !(usuario?.role.canOperateStore ?? false);
    final abierta = _id;
    return Scaffold(
      appBar: AppBar(
        title: Text(esCliente ? nombreAtencionAlCliente : 'Conversación'),
      ),
      body: Column(
        children: [
          Expanded(
            child: abierta == null
                ? _Vacio(esCliente: esCliente)
                : _Historial(
                    conversationId: abierta,
                    emailPropio: usuario?.email ?? '',
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
                    icon: const Icon(Icons.send),
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
        final creada = await repositorio.create(message: texto);
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
      } else {
        await haptics.play(HapticCue.warning);
        _avisar('Mensaje guardado. Se enviará al reconectar.');
      }
    } on AppFailure catch (falla) {
      await haptics.play(HapticCue.error);
      _avisar(falla.message);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  void _avisar(String mensaje) {
    if (!mounted) return;
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(mensaje)));
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
  const _Historial({required this.conversationId, required this.emailPropio});

  final int conversationId;
  final String emailPropio;

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
                reverse: true,
                padding: const EdgeInsets.fromLTRB(12, 12, 12, 4),
                itemCount: items.length,
                itemBuilder: (context, index) {
                  final mensaje = items[items.length - index - 1];
                  final propio =
                      emailPropio.isNotEmpty &&
                      mensaje['remitenteEmail']?.toString().toLowerCase() ==
                          emailPropio.toLowerCase();
                  return _Burbuja(mensaje: mensaje, propio: propio);
                },
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
                child: Text(
                  DateFormat.Hm('es_PY').format(enviado.toLocal()),
                  style: theme.textTheme.labelSmall?.copyWith(
                    color: theme.colorScheme.onSurfaceVariant,
                  ),
                ),
              ),
          ],
        ),
      ),
    );
  }
}
