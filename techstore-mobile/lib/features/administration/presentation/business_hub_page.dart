import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../security/application/security_providers.dart';
import '../../security/domain/app_permissions.dart';
import '../domain/resource_definition.dart';

/// Una baldosa del hub: a dónde lleva y qué permiso hace falta para verla.
final class _Baldosa {
  const _Baldosa({
    required this.icono,
    required this.titulo,
    required this.ruta,
    this.modulo,
    this.accion = Acciones.ver,
  });

  final IconData icono;
  final String titulo;
  final String ruta;
  final String? modulo;
  final String accion;
}

/// El menú de trabajo, armado con lo que esta persona puede hacer.
///
/// Antes la grilla dependía solo del rol. Ahora cada baldosa declara su módulo
/// y se dibuja si el servidor dijo que sí: quitarle «Compras» a un cajero desde
/// Seguridad le saca la baldosa acá, sin publicar una versión nueva de la app.
///
/// Ocultarla no es lo que protege nada. Quien llegue igual a la pantalla se va
/// a encontrar con que el backend rechaza la operación, que es donde la
/// restricción es real.
class BusinessHubPage extends ConsumerWidget {
  const BusinessHubPage({required this.admin, super.key});

  final bool admin;

  static const _fijasAdmin = [
    _Baldosa(
      icono: Icons.shield_outlined,
      titulo: 'Seguridad',
      ruta: '/admin/seguridad',
      modulo: Modulos.seguridad,
    ),
    _Baldosa(
      icono: Icons.local_offer_outlined,
      titulo: 'Promociones',
      ruta: '/admin/promociones',
      modulo: Modulos.productos,
    ),
    // Sin estos datos no hay transferencia, y sin transferencia no hay envío
    // a domicilio.
    _Baldosa(
      icono: Icons.account_balance_outlined,
      titulo: 'Cobro por transferencia',
      ruta: '/admin/transferencia',
    ),
    _Baldosa(
      icono: Icons.fact_check_outlined,
      titulo: 'Verificar pagos',
      ruta: '/pagos',
      modulo: Modulos.pedidos,
    ),
    _Baldosa(
      icono: Icons.archive_outlined,
      titulo: 'Documentos y exportaciones',
      ruta: '/documents',
    ),
  ];

  static const _fijasOperacion = [
    _Baldosa(
      icono: Icons.shopping_cart_checkout,
      titulo: 'Venta directa',
      ruta: '/sales/new',
      modulo: Modulos.facturas,
      accion: Acciones.crear,
    ),
    _Baldosa(
      icono: Icons.add_business_outlined,
      titulo: 'Nueva compra',
      ruta: '/purchases/new',
      modulo: Modulos.compras,
      accion: Acciones.crear,
    ),
    _Baldosa(
      icono: Icons.point_of_sale,
      titulo: 'Operar caja',
      ruta: '/cash/actions',
      modulo: Modulos.caja,
    ),
  ];

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final permisos = ref.watch(permisosProvider);
    final actuales = permisos.value;

    // Mientras no se sabe qué puede hacer, no se adivina: se espera. Dibujar la
    // grilla completa y recortarla medio segundo después es peor que esperar.
    if (actuales == null && permisos.isLoading) {
      return Scaffold(
        appBar: AppBar(title: Text(admin ? 'Administración' : 'Operaciones')),
        body: const Center(child: CircularProgressIndicator()),
      );
    }
    // Si la consulta falló, se muestra lo de siempre y que decida el servidor:
    // dejar a un administrador sin menú por un error de red sería peor.
    final permitidas = _baldosas(actuales);

    return Scaffold(
      appBar: AppBar(title: Text(admin ? 'Administración' : 'Operaciones')),
      body: permitidas.isEmpty
          ? const Center(
              child: Padding(
                padding: EdgeInsets.all(24),
                child: Text(
                  'No tenés permisos para ninguna de estas pantallas. '
                  'Pedíselo a un administrador.',
                  textAlign: TextAlign.center,
                ),
              ),
            )
          : GridView.builder(
              padding: const EdgeInsets.all(16),
              gridDelegate: const SliverGridDelegateWithMaxCrossAxisExtent(
                maxCrossAxisExtent: 260,
                childAspectRatio: 1.2,
                mainAxisSpacing: 12,
                crossAxisSpacing: 12,
              ),
              itemCount: permitidas.length,
              itemBuilder: (context, index) {
                final baldosa = permitidas[index];
                return Card(
                  clipBehavior: Clip.antiAlias,
                  child: InkWell(
                    onTap: () => context.push(baldosa.ruta),
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Icon(baldosa.icono, size: 38),
                        const SizedBox(height: 10),
                        Padding(
                          padding: const EdgeInsets.symmetric(horizontal: 8),
                          child: Text(
                            baldosa.titulo,
                            textAlign: TextAlign.center,
                          ),
                        ),
                      ],
                    ),
                  ),
                );
              },
            ),
    );
  }

  List<_Baldosa> _baldosas(AppPermissions? permisos) {
    final fijas = admin ? _fijasAdmin : _fijasOperacion;
    final claves = admin ? adminResourceKeys : operatorResourceKeys;
    final deRecursos = [
      for (final clave in claves)
        if (resources[clave] case final definicion?)
          _Baldosa(
            icono: definicion.icon,
            titulo: definicion.title,
            ruta: '/resources/${definicion.key}',
            modulo: definicion.modulo,
          ),
    ];
    final todas = [...fijas, ...deRecursos];
    if (permisos == null) return todas;
    return todas
        .where((b) => b.modulo == null || permisos.puede(b.modulo!, b.accion))
        .toList(growable: false);
  }
}
