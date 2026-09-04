import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../domain/resource_definition.dart';

class BusinessHubPage extends StatelessWidget {
  const BusinessHubPage({required this.admin, super.key});

  final bool admin;

  @override
  Widget build(BuildContext context) {
    final keys = admin ? adminResourceKeys : operatorResourceKeys;
    return Scaffold(
      appBar: AppBar(title: Text(admin ? 'Administración' : 'Operaciones')),
      body: GridView.builder(
        padding: const EdgeInsets.all(16),
        gridDelegate: const SliverGridDelegateWithMaxCrossAxisExtent(
          maxCrossAxisExtent: 260,
          childAspectRatio: 1.2,
          mainAxisSpacing: 12,
          crossAxisSpacing: 12,
        ),
        itemCount: keys.length + (admin ? 2 : 3),
        itemBuilder: (context, index) {
          if (admin && index == 0) {
            return Card(
              clipBehavior: Clip.antiAlias,
              child: InkWell(
                onTap: () => context.push('/admin/seguridad'),
                child: const Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Icon(Icons.shield_outlined, size: 38),
                    SizedBox(height: 10),
                    Text('Seguridad', textAlign: TextAlign.center),
                  ],
                ),
              ),
            );
          }
          if (admin && index == 1) {
            return Card(
              clipBehavior: Clip.antiAlias,
              child: InkWell(
                onTap: () => context.push('/documents'),
                child: const Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Icon(Icons.archive_outlined, size: 38),
                    SizedBox(height: 10),
                    Text(
                      'Documentos y exportaciones',
                      textAlign: TextAlign.center,
                    ),
                  ],
                ),
              ),
            );
          }
          if (!admin && index == 0) {
            return Card(
              clipBehavior: Clip.antiAlias,
              child: InkWell(
                onTap: () => context.push('/sales/new'),
                child: const Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Icon(Icons.shopping_cart_checkout, size: 38),
                    SizedBox(height: 10),
                    Text('Venta directa'),
                  ],
                ),
              ),
            );
          }
          if (!admin && index == 1) {
            return Card(
              clipBehavior: Clip.antiAlias,
              child: InkWell(
                onTap: () => context.push('/purchases/new'),
                child: const Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Icon(Icons.add_business_outlined, size: 38),
                    SizedBox(height: 10),
                    Text('Nueva compra'),
                  ],
                ),
              ),
            );
          }
          if (!admin && index == 2) {
            return Card(
              clipBehavior: Clip.antiAlias,
              child: InkWell(
                onTap: () => context.push('/cash/actions'),
                child: const Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Icon(Icons.point_of_sale, size: 38),
                    SizedBox(height: 10),
                    Text('Operar caja'),
                  ],
                ),
              ),
            );
          }
          final resourceIndex = admin ? index - 2 : index - 3;
          final definition = resources[keys[resourceIndex]]!;
          return Card(
            clipBehavior: Clip.antiAlias,
            child: InkWell(
              onTap: () => context.push('/resources/${definition.key}'),
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Icon(definition.icon, size: 38),
                  const SizedBox(height: 10),
                  Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 8),
                    child: Text(definition.title, textAlign: TextAlign.center),
                  ),
                ],
              ),
            ),
          );
        },
      ),
    );
  }
}
