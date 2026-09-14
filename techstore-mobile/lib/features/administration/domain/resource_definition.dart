import 'package:flutter/material.dart';

import '../../security/domain/app_permissions.dart';

final class ResourceDefinition {
  const ResourceDefinition({
    required this.key,
    required this.title,
    required this.path,
    required this.icon,
    this.modulo,
    this.query = const {},
    this.primaryFields = const [
      'nombre',
      'numero',
      'razonSocial',
      'email',
      'estado',
    ],
    this.secondaryFields = const ['descripcion', 'fecha', 'total', 'activo'],
  });

  final String key;
  final String title;
  final String path;
  final IconData icon;

  /// Sobre qué módulo de Seguridad trabaja esta ficha.
  ///
  /// Con esto, quitarle un permiso a un rol le saca también la baldosa del
  /// menú y le cierra la pantalla, sin tocar el router ni el hub. Nulo
  /// significa que el backend no la gobierna por módulo y queda como estaba.
  final String? modulo;
  final Map<String, Object?> query;
  final List<String> primaryFields;
  final List<String> secondaryFields;
}

const resources = <String, ResourceDefinition>{
  'dashboard': ResourceDefinition(
    key: 'dashboard',
    modulo: Modulos.reportes,
    title: 'Dashboard',
    path: '/api/v1/dashboard',
    icon: Icons.dashboard_outlined,
  ),
  'replenishment': ResourceDefinition(
    key: 'replenishment',
    modulo: Modulos.productos,
    title: 'Reposición',
    path: '/api/v1/productos/reposicion',
    icon: Icons.inventory_outlined,
  ),
  'all-orders': ResourceDefinition(
    key: 'all-orders',
    modulo: Modulos.pedidos,
    title: 'Pedidos',
    path: '/api/v1/pedidos',
    icon: Icons.receipt_long_outlined,
    query: {'page': 0, 'size': 50, 'sort': 'fecha,DESC'},
  ),
  'purchases': ResourceDefinition(
    key: 'purchases',
    modulo: Modulos.compras,
    title: 'Compras',
    path: '/api/v1/compras',
    icon: Icons.shopping_bag_outlined,
    query: {'page': 0, 'size': 50},
  ),
  'suppliers': ResourceDefinition(
    key: 'suppliers',
    modulo: Modulos.proveedores,
    title: 'Proveedores',
    path: '/api/v1/proveedores',
    icon: Icons.local_shipping_outlined,
    query: {'page': 0, 'size': 50},
  ),
  'cash': ResourceDefinition(
    key: 'cash',
    modulo: Modulos.caja,
    title: 'Resumen de caja',
    path: '/api/v1/caja/resumen',
    icon: Icons.point_of_sale_outlined,
  ),
  'cash-movements': ResourceDefinition(
    key: 'cash-movements',
    modulo: Modulos.caja,
    title: 'Movimientos de caja',
    path: '/api/v1/caja/movimientos',
    icon: Icons.swap_vert,
    query: {'page': 0, 'size': 50},
  ),
  'cash-closures': ResourceDefinition(
    key: 'cash-closures',
    modulo: Modulos.caja,
    title: 'Cierres de caja',
    path: '/api/v1/caja/cierres',
    icon: Icons.lock_clock_outlined,
    query: {'page': 0, 'size': 50},
  ),
  'invoices': ResourceDefinition(
    key: 'invoices',
    modulo: Modulos.facturas,
    title: 'Facturas',
    path: '/api/v1/facturas',
    icon: Icons.description_outlined,
    query: {'page': 0, 'size': 50},
  ),
  'credit-notes': ResourceDefinition(
    key: 'credit-notes',
    modulo: Modulos.facturas,
    title: 'Notas de crédito',
    path: '/api/v1/notas-credito',
    icon: Icons.assignment_return_outlined,
    query: {'page': 0, 'size': 50},
  ),
  'sales-report': ResourceDefinition(
    key: 'sales-report',
    modulo: Modulos.reportes,
    title: 'Reporte de ventas',
    path: '/api/v1/reportes/ventas',
    icon: Icons.bar_chart_outlined,
  ),
  'low-stock-report': ResourceDefinition(
    key: 'low-stock-report',
    modulo: Modulos.reportes,
    title: 'Stock bajo',
    path: '/api/v1/reportes/stock-bajo',
    icon: Icons.warning_amber_outlined,
  ),
  'mail': ResourceDefinition(
    key: 'mail',
    modulo: Modulos.configuracion,
    title: 'Correo',
    path: '/api/v1/correos',
    icon: Icons.mail_outline,
    query: {'page': 0, 'size': 50},
  ),
  'notifications': ResourceDefinition(
    key: 'notifications',
    title: 'Notificaciones',
    path: '/api/v1/notificaciones',
    icon: Icons.notifications_outlined,
    query: {'page': 0, 'size': 50},
  ),
  'conversations': ResourceDefinition(
    key: 'conversations',
    modulo: Modulos.conversaciones,
    title: 'Conversaciones',
    path: '/api/v1/conversaciones',
    icon: Icons.chat_bubble_outline,
    query: {'page': 0, 'size': 50},
  ),
  'users': ResourceDefinition(
    key: 'users',
    modulo: Modulos.usuarios,
    title: 'Usuarios',
    path: '/api/v1/usuarios',
    icon: Icons.people_outline,
    query: {'page': 0, 'size': 50},
  ),
  'products-admin': ResourceDefinition(
    key: 'products-admin',
    modulo: Modulos.productos,
    title: 'Productos',
    path: '/api/v1/productos/admin',
    icon: Icons.inventory_2_outlined,
    query: {'page': 0, 'size': 50},
  ),
  'company': ResourceDefinition(
    key: 'company',
    modulo: Modulos.empresa,
    title: 'Empresa',
    path: '/api/v1/empresa',
    icon: Icons.business_outlined,
  ),
  'expedition-points': ResourceDefinition(
    key: 'expedition-points',
    modulo: Modulos.empresa,
    title: 'Puntos de expedición',
    path: '/api/v1/empresa/puntos-expedicion',
    icon: Icons.store_outlined,
  ),
  'shipping-rates': ResourceDefinition(
    key: 'shipping-rates',
    modulo: Modulos.envios,
    title: 'Tarifas de envío',
    path: '/api/v1/envios/tarifas',
    icon: Icons.route_outlined,
  ),
  'audit': ResourceDefinition(
    key: 'audit',
    modulo: Modulos.auditoria,
    title: 'Auditoría',
    path: '/api/v1/auditoria',
    icon: Icons.manage_search_outlined,
    query: {'page': 0, 'size': 50},
  ),
  'smtp-config': ResourceDefinition(
    key: 'smtp-config',
    modulo: Modulos.configuracion,
    title: 'Configuración SMTP',
    path: '/api/v1/correos/config',
    icon: Icons.outgoing_mail,
  ),
};

/// Lo que ve un cajero. El resumen de caja no esta acá a proposito: es el
/// arqueo del periodo, y su neto de efectivo es el monto esperado del turno.
/// El servidor tambien lo niega, esta lista solo evita ofrecer una pantalla
/// que responderia 403.
const operatorResourceKeys = [
  'dashboard',
  'replenishment',
  'all-orders',
  'purchases',
  'suppliers',
  'cash-movements',
  'cash-closures',
  'invoices',
  'credit-notes',
  'sales-report',
  'low-stock-report',
  'mail',
  'notifications',
  'conversations',
];

const adminResourceKeys = [
  'cash',
  'users',
  'products-admin',
  'company',
  'expedition-points',
  'shipping-rates',
  'audit',
  'smtp-config',
];
