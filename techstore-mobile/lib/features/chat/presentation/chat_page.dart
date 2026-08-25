import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/di/providers.dart';
import '../../../core/errors/app_failure.dart';
import '../../../core/offline/offline_queue_service.dart';
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

class ChatPage extends ConsumerWidget {
  const ChatPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final conversations = ref.watch(conversationsProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('Conversaciones')),
      body: RefreshIndicator(
        onRefresh: () => ref.refresh(conversationsProvider.future),
        child: conversations.when(
          loading: () => const Center(child: CircularProgressIndicator()),
          error: (error, _) => ListView(
            children: const [
              SizedBox(height: 160),
              Center(child: Text('No se pudieron cargar las conversaciones')),
            ],
          ),
          data: (items) => items.isEmpty
              ? ListView(
                  children: const [
                    SizedBox(height: 160),
                    Center(child: Text('Sin conversaciones')),
                  ],
                )
              : ListView.builder(
                  itemCount: items.length,
                  itemBuilder: (context, index) {
                    final item = items[index];
                    final unread = (item['noLeidos'] as num?)?.toInt() ?? 0;
                    final last = item['ultimoMensaje'] as Map?;
                    return ListTile(
                      leading: Badge(
                        isLabelVisible: unread > 0,
                        label: Text('$unread'),
                        child: const CircleAvatar(
                          child: Icon(Icons.chat_bubble_outline),
                        ),
                      ),
                      title: Text(
                        item['clienteNombre']?.toString() ??
                            'Soporte TechStore',
                      ),
                      subtitle: Text(
                        last?['contenido']?.toString() ?? 'Sin mensajes',
                      ),
                      onTap: () => context.push('/chat/${item['id']}'),
                    );
                  },
                ),
        ),
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => _newConversation(context, ref),
        icon: const Icon(Icons.add_comment_outlined),
        label: const Text('Nueva'),
      ),
    );
  }

  Future<void> _newConversation(BuildContext context, WidgetRef ref) async {
    final result = await showDialog<(String, int?)>(
      context: context,
      builder: (_) => const _NewConversationDialog(),
    );
    if (result == null || !context.mounted) return;
    try {
      final id = await ref
          .read(chatRepositoryProvider)
          .create(message: result.$1, orderId: result.$2);
      ref.invalidate(conversationsProvider);
      if (context.mounted) unawaited(context.push('/chat/$id'));
    } on AppFailure catch (failure) {
      if (context.mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text(failure.message)));
      }
    }
  }
}

class ConversationPage extends ConsumerStatefulWidget {
  const ConversationPage({required this.conversationId, super.key});
  final int conversationId;

  @override
  ConsumerState<ConversationPage> createState() => _ConversationPageState();
}

class _ConversationPageState extends ConsumerState<ConversationPage> {
  final _message = TextEditingController();
  bool _busy = false;

  @override
  void initState() {
    super.initState();
    Future.microtask(
      () => ref.read(chatRepositoryProvider).markRead(widget.conversationId),
    );
  }

  @override
  void dispose() {
    _message.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final messages = ref.watch(messagesProvider(widget.conversationId));
    return Scaffold(
      appBar: AppBar(title: const Text('Chat')),
      body: Column(
        children: [
          Expanded(
            child: messages.when(
              loading: () => const Center(child: CircularProgressIndicator()),
              error: (error, _) => const Center(
                child: Text('No se pudieron cargar los mensajes'),
              ),
              data: (items) => RefreshIndicator(
                onRefresh: () =>
                    ref.refresh(messagesProvider(widget.conversationId).future),
                child: ListView.builder(
                  reverse: true,
                  padding: const EdgeInsets.all(12),
                  itemCount: items.length,
                  itemBuilder: (context, index) {
                    final item = items[items.length - index - 1];
                    return Card(
                      child: ListTile(
                        title: Text(
                          item['remitenteNombre']?.toString() ?? 'Usuario',
                        ),
                        subtitle: Text(item['contenido']?.toString() ?? ''),
                      ),
                    );
                  },
                ),
              ),
            ),
          ),
          SafeArea(
            top: false,
            child: Padding(
              padding: const EdgeInsets.all(12),
              child: Row(
                children: [
                  Expanded(
                    child: TextField(
                      controller: _message,
                      maxLength: 2000,
                      minLines: 1,
                      maxLines: 4,
                      decoration: const InputDecoration(
                        labelText: 'Mensaje',
                        counterText: '',
                      ),
                    ),
                  ),
                  IconButton.filled(
                    onPressed: _busy ? null : _send,
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
    if (_message.text.trim().isEmpty) return;
    setState(() => _busy = true);
    try {
      final result = await ref
          .read(chatRepositoryProvider)
          .send(widget.conversationId, _message.text);
      _message.clear();
      if (result == ChatSendResult.sent) {
        ref.invalidate(messagesProvider(widget.conversationId));
        ref.invalidate(conversationsProvider);
      } else if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Mensaje guardado. Se enviará al reconectar.'),
          ),
        );
      }
    } on AppFailure catch (failure) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text(failure.message)));
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }
}

class _NewConversationDialog extends StatefulWidget {
  const _NewConversationDialog();

  @override
  State<_NewConversationDialog> createState() => _NewConversationDialogState();
}

class _NewConversationDialogState extends State<_NewConversationDialog> {
  final _message = TextEditingController();
  final _order = TextEditingController();

  @override
  void dispose() {
    _message.dispose();
    _order.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => AlertDialog(
    title: const Text('Nueva conversación'),
    content: Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        TextField(
          controller: _message,
          maxLines: 4,
          decoration: const InputDecoration(labelText: 'Mensaje'),
        ),
        TextField(
          controller: _order,
          keyboardType: TextInputType.number,
          decoration: const InputDecoration(labelText: 'ID pedido (opcional)'),
        ),
      ],
    ),
    actions: [
      TextButton(
        onPressed: () => Navigator.pop(context),
        child: const Text('Cancelar'),
      ),
      FilledButton(
        onPressed: () {
          if (_message.text.trim().isEmpty) return;
          Navigator.pop(context, (_message.text, int.tryParse(_order.text)));
        },
        child: const Text('Crear'),
      ),
    ],
  );
}
