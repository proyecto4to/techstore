import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/di/providers.dart';
import '../../../core/errors/user_message.dart';
import '../../../core/native/haptics_service.dart';
import '../../auth/application/session_controller.dart';
import '../domain/product.dart';
import 'catalog_providers.dart';

/// Los favoritos que la persona acaba de tocar y el servidor todavía no
/// confirmó: el corazón cambia en el acto y no espera a la red.
final favoritosEnCursoProvider =
    NotifierProvider<FavoritosEnCurso, Map<int, bool>>(FavoritosEnCurso.new);

class FavoritosEnCurso extends Notifier<Map<int, bool>> {
  @override
  Map<int, bool> build() => const {};

  void marcar(int id, bool favorito) => state = {...state, id: favorito};

  void soltar(int id) => state = {...state}..remove(id);
}

/// Dónde está la persona, para volver ahí después de ingresar. Fuera de un
/// router (una pantalla suelta en una prueba) vuelve al catálogo.
String _rutaActual(BuildContext context) {
  try {
    return GoRouterState.of(context).uri.toString();
  } on Object {
    return '/catalog';
  }
}

/// Si este producto es favorito, contando lo que está en camino al servidor.
bool esFavorito(WidgetRef ref, int id) {
  final enCurso = ref.watch(favoritosEnCursoProvider.select((m) => m[id]));
  if (enCurso != null) return enCurso;
  if (!ref.watch(authSessionProvider.select((s) => s.isAuthenticated))) {
    return false;
  }
  return ref.watch(
    favoritesProvider.select(
      (lista) => lista.value?.any((p) => p.id == id) ?? false,
    ),
  );
}

/// Guarda o quita un favorito.
///
/// Antes, si fallaba la red, el error se perdía: el corazón no cambiaba y nadie
/// decía por qué. Ahora cambia en el acto, y si el servidor no lo confirma
/// vuelve a como estaba y lo avisa.
Future<void> alternarFavorito(
  BuildContext context,
  WidgetRef ref,
  Product producto,
) async {
  final mensajero = ScaffoldMessenger.maybeOf(context);
  if (!ref.read(authSessionProvider).isAuthenticated) {
    final ruta = _rutaActual(context);
    mensajero
      ?..hideCurrentSnackBar()
      ..showSnackBar(
        SnackBar(
          content: const Text('Ingresá para guardar favoritos.'),
          action: SnackBarAction(
            label: 'Ingresar',
            onPressed: () => context.push(
              '/login?returnTo=${Uri.encodeComponent(ruta)}',
            ),
          ),
        ),
      );
    return;
  }

  final antes = ref.read(favoritosEnCursoProvider)[producto.id] ??
      (ref.read(favoritesProvider).value?.any((p) => p.id == producto.id) ??
          false);
  final ahora = !antes;
  final enCurso = ref.read(favoritosEnCursoProvider.notifier)
    ..marcar(producto.id, ahora);
  unawaited(
    ref
        .read(hapticsServiceProvider)
        .play(ahora ? HapticCue.selection : HapticCue.tap),
  );

  final repositorio = ref.read(favoritesRepositoryProvider);
  try {
    if (ahora) {
      await repositorio.add(producto.id);
    } else {
      await repositorio.remove(producto.id);
    }
    // Se suelta la marca recién cuando llegó la lista nueva: soltarla antes
    // haría parpadear el corazón al estado viejo por un instante.
    try {
      ref.invalidate(favoritesProvider);
      await ref.read(favoritesProvider.future);
    } on Object {
      // Si la lista no vuelve, igual el servidor ya lo guardó.
    }
    enCurso.soltar(producto.id);
  } on Object catch (error) {
    enCurso.soltar(producto.id);
    mensajero
      ?..hideCurrentSnackBar()
      ..showSnackBar(
        SnackBar(
          content: Text(
            userMessageFor(
              error,
              fallback: ahora
                  ? 'No pudimos guardarlo en favoritos. Revisá tu conexión.'
                  : 'No pudimos quitarlo de favoritos. Revisá tu conexión.',
            ),
          ),
        ),
      );
  }
}
