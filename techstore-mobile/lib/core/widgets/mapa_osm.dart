import 'package:flutter/material.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:latlong2/latlong.dart';

/// El servicio de tiles exige identificar la aplicación.
///
/// Sin este encabezado el servidor puede contestar 403 y el mapa queda en gris
/// sin decir por qué.
const agenteDelMapa = 'py.com.techstore.app';

/// El dibujo del mapa, igual en todas las pantallas que muestran uno.
///
/// Estaba escrito dos veces, con la URL y el agente repetidos. Si cambia el
/// servidor de tiles —o si hay que agregarle una clave— se cambia acá y vale
/// para la ubicación de la empresa y para las direcciones de entrega.
TileLayer capaDeMapa() => TileLayer(
  urlTemplate: 'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
  userAgentPackageName: agenteDelMapa,
);

/// El crédito a OpenStreetMap, que su licencia pide mostrar.
class CreditoDelMapa extends StatelessWidget {
  const CreditoDelMapa({super.key});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Text(
      'El mapa usa OpenStreetMap, que es de uso libre.',
      textAlign: TextAlign.center,
      style: theme.textTheme.bodySmall?.copyWith(
        color: theme.colorScheme.onSurfaceVariant,
      ),
    );
  }
}

/// El marcador que señala un punto.
///
/// La punta del icono es lo que señala, no su centro: sin la alineación de
/// arriba el marcador queda centrado en el punto y apunta unos metros al norte
/// de lo que se eligió.
Marker marcadorDePunto({
  required LatLng punto,
  required Color color,
  double tamano = 44,
}) => Marker(
  point: punto,
  width: tamano,
  height: tamano,
  alignment: Alignment.topCenter,
  child: Icon(Icons.location_on, size: tamano, color: color),
);
