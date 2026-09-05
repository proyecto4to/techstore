import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:intl/date_symbol_data_local.dart';
import 'package:techstore_mobile/core/di/providers.dart';
import 'package:techstore_mobile/features/chat/presentation/chat_page.dart';

import '../../support/sesion_de_prueba.dart';

/// El último mensaje va abajo.
///
/// Parece trivial y estuvo mal mucho tiempo sin que nadie lo notara: la lista
/// se dibuja invertida —para que el final quede a la vista sin animar un
/// scroll— y además se le daba vuelta el índice, así que las dos inversiones se
/// cancelaban y la conversación se leía al revés. La respuesta quedaba arriba
/// de la pregunta.
///
/// No lo agarró ningún test porque todos miraban que los mensajes estuvieran,
/// no en qué orden. Salió de abrir un chat en el emulador y leerlo.
void main() {
  setUpAll(() async {
    await initializeDateFormatting('es_PY');
  });

  // Como los manda el servidor: del más nuevo al más viejo.
  final mensajes = [
    {
      'id': 3,
      'contenido': 'El tercero',
      'remitenteEmail': 'otro@techstore.py',
      'remitenteNombre': 'Otra Persona',
      'enviadoEn': '2026-09-05T13:46:00',
    },
    {
      'id': 2,
      'contenido': 'El segundo',
      'remitenteEmail': 'persona@techstore.py',
      'remitenteNombre': 'Persona De Prueba',
      'enviadoEn': '2026-09-05T13:45:00',
    },
    {
      'id': 1,
      'contenido': 'El primero',
      'remitenteEmail': 'persona@techstore.py',
      'remitenteNombre': 'Persona De Prueba',
      'enviadoEn': '2026-09-05T13:44:00',
    },
  ];

  testWidgets('el más reciente queda abajo de todo', (tester) async {
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          apiClientProvider.overrideWithValue(apiClientDeRol('Repartidor')),
          secureStoreProvider.overrideWithValue(StoreConSesion()),
          messagesProvider.overrideWith((ref, id) async => mensajes),
          conversationProvider.overrideWith(
            (ref, id) async => const {'titulo': 'Otra Persona'},
          ),
        ],
        child: const MaterialApp(home: ConversationPage(conversationId: 7)),
      ),
    );
    await tester.pumpAndSettle();

    final primero = tester.getCenter(find.text('El primero')).dy;
    final segundo = tester.getCenter(find.text('El segundo')).dy;
    final tercero = tester.getCenter(find.text('El tercero')).dy;

    // Más abajo en pantalla es mayor `dy`.
    expect(
      primero,
      lessThan(segundo),
      reason: 'el primer mensaje tiene que quedar más arriba que el segundo',
    );
    expect(
      segundo,
      lessThan(tercero),
      reason: 'el último mensaje tiene que quedar al final de la conversación',
    );
  });
}
