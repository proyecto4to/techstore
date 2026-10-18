import 'package:flutter/material.dart';

import '../diseno/marca_tema.dart';
import '../diseno/paleta.dart';
import 'marca/isotipo.dart';

/// Cargando: el cursor del isotipo late mientras dura la espera.
///
/// El texto se muestra solo si hay lugar; el lector de pantalla lo anuncia
/// siempre.
class AppLoading extends StatelessWidget {
  const AppLoading({super.key, this.label = 'Cargando'});

  final String label;

  @override
  Widget build(BuildContext context) => Semantics(
    container: true,
    label: label,
    liveRegion: true,
    child: ExcludeSemantics(
      child: LayoutBuilder(
        builder: (context, limites) {
          final conTexto = limites.maxHeight > 140 && limites.maxWidth > 160;
          return Center(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                const IsotipoAnimado(ancho: 34, repetir: true),
                if (conTexto) ...[
                  const SizedBox(height: 14),
                  Text(
                    label,
                    textAlign: TextAlign.center,
                    style: Theme.of(context).textTheme.bodySmall,
                  ),
                ],
              ],
            ),
          );
        },
      ),
    ),
  );
}

/// Nada para mostrar todavía. Con [accion], ofrece el paso siguiente: un
/// estado vacío sin salida deja a la persona sin saber qué hacer.
class AppEmptyState extends StatelessWidget {
  const AppEmptyState({
    required this.message,
    super.key,
    this.icon = Icons.inbox_outlined,
    this.title,
    this.accion,
    this.onAccion,
  });

  final String message;
  final IconData icon;
  final String? title;
  final String? accion;
  final VoidCallback? onAccion;

  @override
  Widget build(BuildContext context) => Semantics(
    container: true,
    label: title == null ? message : '$title. $message',
    child: _Composicion(
      icono: icon,
      colorIcono: Paleta.azulLuminoso,
      titulo: title,
      mensaje: message,
      boton: accion != null && onAccion != null
          ? FilledButton(onPressed: onAccion, child: Text(accion!))
          : null,
    ),
  );
}

class AppErrorState extends StatelessWidget {
  const AppErrorState({required this.message, super.key, this.onRetry});

  final String message;
  final VoidCallback? onRetry;

  @override
  Widget build(BuildContext context) => Semantics(
    container: true,
    label: message,
    liveRegion: true,
    child: _Composicion(
      icono: Icons.wifi_tethering_error_rounded,
      colorIcono: MarcaTema.of(context).error,
      mensaje: message,
      boton: onRetry == null
          ? null
          : FilledButton(onPressed: onRetry, child: const Text('Reintentar')),
    ),
  );
}

/// La vitrina vacía: un marco con el ícono, el texto y, si hay, la salida.
///
/// Desplazable a propósito: con la letra al doble en un teléfono chico, el
/// bloque no entra y no puede desbordar. Y desplazable siempre, aunque entre:
/// varias pantallas lo ponen dentro de un «tirar para actualizar», que solo
/// responde si hay algo que se pueda arrastrar.
class _Composicion extends StatelessWidget {
  const _Composicion({
    required this.icono,
    required this.colorIcono,
    required this.mensaje,
    this.titulo,
    this.boton,
  });

  final IconData icono;
  final Color colorIcono;
  final String mensaje;
  final String? titulo;
  final Widget? boton;

  @override
  Widget build(BuildContext context) {
    final textos = Theme.of(context).textTheme;
    return LayoutBuilder(
      builder: (context, limites) => SingleChildScrollView(
        physics: const AlwaysScrollableScrollPhysics(),
        child: ConstrainedBox(
          constraints: BoxConstraints(
            minHeight: limites.maxHeight.isFinite ? limites.maxHeight : 0,
          ),
          child: Center(
            child: Padding(
              padding: const EdgeInsets.all(28),
              child: ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 420),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    ExcludeSemantics(
                      child: Container(
                        width: 72,
                        height: 72,
                        decoration: BoxDecoration(
                          color: Paleta.negroAzulado,
                          borderRadius: BorderRadius.circular(22),
                          border: Border.all(color: Paleta.borde),
                          boxShadow: [
                            BoxShadow(
                              color: colorIcono.withValues(alpha: 0.18),
                              blurRadius: 32,
                              spreadRadius: -4,
                            ),
                          ],
                        ),
                        child: Icon(icono, size: 32, color: colorIcono),
                      ),
                    ),
                    const SizedBox(height: 20),
                    if (titulo != null) ...[
                      ExcludeSemantics(
                        child: Text(
                          titulo!,
                          textAlign: TextAlign.center,
                          style: textos.titleLarge,
                        ),
                      ),
                      const SizedBox(height: 8),
                    ],
                    ExcludeSemantics(
                      child: Text(
                        mensaje,
                        textAlign: TextAlign.center,
                        style: textos.bodyMedium?.copyWith(
                          color: titulo == null
                              ? Paleta.texto
                              : Paleta.textoSecundario,
                        ),
                      ),
                    ),
                    if (boton != null) ...[
                      const SizedBox(height: 24),
                      boton!,
                    ],
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
