import 'package:flutter/material.dart';

final class ResourceDefinition {
  const ResourceDefinition({
    required this.key,
    required this.title,
    required this.path,
    required this.icon,
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
  final Map<String, Object?> query;
  final List<String> primaryFields;
  final List<String> secondaryFields;
}

const resources = <String, ResourceDefinition>{
  'dashboard': ResourceDefinition(
    key: 'dashboard',
    title: 'Dashboard',
    path: '/api/v1/dashboard',
    icon: Icons.dashboard_outlined,
  ),
  'replenishment': ResourceDefinition(
    key: 'replenishment',
    title: 'Reposición',
    path: '/api/v1/productos/reposicion',
    icon: Icons.inventory_outlined,
  ),
  'all-orders': ResourceDefinition(
    key: 'all-orders',
    title: 'Pedidos',
    path: '/api/v1/pedidos',
    icon: Icons.receipt_long_outlined,
    query: {'page': 0, 'size': 50, 'sort': 'fecha,DESC'},
  ),
  'purchases': ResourceDefinition(
    key: 'purchases',
    title: 'Compras',
    path: '/api/v1/compras',
    icon: Icons.shopping_bag_outlined,
    query: {'page': 0, 'size': 50},
  ),
  'suppliers': ResourceDefinition(
    key: 'suppliers',
    title: 'Proveedores',
    path: '/api/v1/proveedores',
    icon: Icons.local_shipping_outlined,
    query: {'page': 0, 'size': 50},
  ),
  'cash': ResourceDefinition(
    key: 'cash',
    title: 'Resumen de caja',
    path: '/api/v1/caja/resumen',
    icon: Icons.point_of_sale_outlined,
  ),
  'cash-movements': ResourceDefinition(
    key: 'cash-movements',
    title: 'Movimientos de caja',
    path: '/api/v1/caja/movimientos',
    icon: Icons.swap_vert,
    query: {'page': 0, 'size': 50},
  ),
  'cash-closures': ResourceDefinition(
    key: 'cash-closures',
    title: 'Cierres de caja',
    path: '/api/v1/caja/cierres',
    icon: Icons.lock_clock_outlined,
    query: {'page': 0, 'size': 50},
  ),
  'invoices': ResourceDefinition(
    key: 'invoices',
    title: 'Facturas',
    path: '/api/v1/facturas',
    icon: Icons.description_outlined,
    query: {'page': 0, 'size': 50},
  ),
  'credit-notes': ResourceDefinition(
    key: 'credit-notes',
    title: 'Notas de crédito',
    path: '/api/v1/notas-credito',
    icon: Icons.assignment_return_outlined,
    query: {'page': 0, 'size': 50},
  ),
  'sales-report': ResourceDefinition(
    key: 'sales-report',
    title: 'Reporte de ventas',
    path: '/api/v1/reportes/ventas',
    icon: Icons.bar_chart_outlined,
  ),
  'low-stock-report': ResourceDefinition(
    key: 'low-stock-report',
    title: 'Stock bajo',
    path: '/api/v1/reportes/stock-bajo',
    icon: Icons.warning_amber_outlined,
  ),
  'mail': ResourceDefinition(
    key: 'mail',
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
    title: 'Conversaciones',
    path: '/api/v1/conversaciones',
    icon: Icons.chat_bubble_outline,
    query: {'page': 0, 'size': 50},
  ),
  'users': ResourceDefinition(
    key: 'users',
    title: 'Usuarios',
    path: '/api/v1/usuarios',
    icon: Icons.people_outline,
    query: {'page': 0, 'size': 50},
  ),
  'products-admin': ResourceDefinition(
    key: 'products-admin',
    title: 'Productos',
    path: '/api/v1/productos/admin',
    icon: Icons.inventory_2_outlined,
    query: {'page': 0, 'size': 50},
  ),
  'company': ResourceDefinition(
    key: 'company',
    title: 'Empresa',
    path: '/api/v1/empresa',
    icon: Icons.business_outlined,
  ),
  'expedition-points': ResourceDefinition(
    key: 'expedition-points',
    title: 'Puntos de expedición',
    path: '/api/v1/empresa/puntos-expedicion',
    icon: Icons.store_outlined,
  ),
  'shipping-rates': ResourceDefinition(
    key: 'shipping-rates',
    title: 'Tarifas de envío',
    path: '/api/v1/envios/tarifas',
    icon: Icons.route_outlined,
  ),
  'audit': ResourceDefinition(
    key: 'audit',
    title: 'Auditoría',
    path: '/api/v1/auditoria',
    icon: Icons.manage_search_outlined,
    query: {'page': 0, 'size': 50},
  ),
  'smtp-config': ResourceDefinition(
    key: 'smtp-config',
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
