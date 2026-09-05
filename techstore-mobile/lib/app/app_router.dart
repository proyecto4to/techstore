import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../core/di/providers.dart';
import '../features/administration/domain/resource_definition.dart';
import '../features/administration/presentation/audit_page.dart';
import '../features/administration/presentation/business_hub_page.dart';
import '../features/administration/presentation/document_center_page.dart';
import '../features/administration/presentation/resource_page.dart';
import '../features/administration/presentation/security_page.dart';
import '../features/auth/application/auth_state.dart';
import '../features/auth/application/session_controller.dart';
import '../features/auth/presentation/account_page.dart';
import '../features/auth/presentation/login_page.dart';
import '../features/auth/presentation/password_recovery_page.dart';
import '../features/auth/presentation/register_page.dart';
import '../features/cart/presentation/cart_page.dart';
import '../features/cash/presentation/cash_actions_page.dart';
import '../features/catalog/presentation/catalog_page.dart';
import '../features/catalog/presentation/favorites_page.dart';
import '../features/chat/presentation/chat_page.dart';
import '../features/chat/presentation/new_conversation_page.dart';
import '../features/checkout/presentation/address_page.dart';
import '../features/checkout/presentation/checkout_page.dart';
import '../features/courier/presentation/courier_cash_page.dart';
import '../features/courier/presentation/courier_deliveries_page.dart';
import '../features/courier/presentation/courier_route_page.dart';
import '../features/courier/presentation/courier_vehicles_page.dart';
import '../features/invoices/presentation/invoices_page.dart';
import '../features/notifications/presentation/notifications_page.dart';
import '../features/orders/presentation/orders_page.dart';
import '../features/purchases/presentation/purchase_form_page.dart';
import '../features/sales/presentation/direct_sale_page.dart';
import 'deep_links.dart';
import 'home_page.dart';

final routerProvider = Provider<GoRouter>((ref) {
  final environment = ref.watch(environmentProvider);
  final refresh = _RouterRefresh();
  ref.listen<AuthState>(
    authSessionProvider,
    (previous, next) => refresh.notify(),
  );

  final router = GoRouter(
    initialLocation: '/splash',
    refreshListenable: refresh,
    redirect: (context, state) {
      final auth = ref.read(authSessionProvider);
      final destinoEnlace = rutaDeEnlaceTechstore(state.uri);
      if (destinoEnlace != null) return destinoEnlace;
      final path = state.uri.path;
      if (auth.isBusy) return path == '/splash' ? null : '/splash';

      final authPath =
          path == '/login' ||
          path == '/register' ||
          path == '/password/forgot' ||
          path == '/password/reset';
      final publicPath =
          authPath ||
          path == '/catalog' ||
          path == '/cart' ||
          path.startsWith('/products/');
      if (!auth.isAuthenticated) {
        return publicPath
            ? null
            : '/login?returnTo=${Uri.encodeComponent(state.uri.toString())}';
      }
      if (authPath || path == '/splash') {
        return state.uri.queryParameters['returnTo'] ?? '/';
      }
      // El repartidor se queda en lo suyo. `/chat` entra en la lista porque
      // ahora puede escribirle a administracion: sin esto el boton de su
      // pantalla lo devolveria al inicio, y la conversacion privada que el
      // servidor le permite abrir seria inalcanzable desde la app.
      if (auth.user!.role.isCourier &&
          path != '/' &&
          path != '/account' &&
          path != '/notifications' &&
          path != '/chat' &&
          !path.startsWith('/chat/') &&
          !path.startsWith('/courier/')) {
        return '/';
      }
      if (path.startsWith('/admin') && !auth.user!.role.canAdminister) {
        return '/';
      }
      if (path.startsWith('/courier') && !auth.user!.role.isCourier) return '/';
      if (path == '/documents' && !auth.user!.role.canAdminister) return '/';
      if ((path.startsWith('/operations') ||
              path.startsWith('/cash/') ||
              path.startsWith('/sales/') ||
              path.startsWith('/purchases/')) &&
          !auth.user!.role.canOperateStore) {
        return '/';
      }
      if (path.startsWith('/resources/')) {
        if (!auth.user!.role.canOperateStore) return '/';
        final key = state.pathParameters['key'];
        if (key != null &&
            adminResourceKeys.contains(key) &&
            !auth.user!.role.canAdminister) {
          return '/';
        }
      }
      return null;
    },
    routes: [
      GoRoute(
        path: '/splash',
        builder: (context, state) =>
            const Scaffold(body: Center(child: CircularProgressIndicator())),
      ),
      GoRoute(path: '/login', builder: (context, state) => const LoginPage()),
      GoRoute(
        path: '/register',
        builder: (context, state) => const RegisterPage(),
      ),
      GoRoute(
        path: '/password/forgot',
        builder: (context, state) => const PasswordRecoveryPage(),
      ),
      GoRoute(
        path: '/password/reset',
        builder: (context, state) =>
            PasswordResetPage(token: state.uri.queryParameters['token'] ?? ''),
      ),
      GoRoute(
        path: '/',
        name: 'home',
        builder: (context, state) =>
            HomePage(environmentLabel: environment.label),
      ),
      GoRoute(
        path: '/catalog',
        builder: (context, state) => const CatalogPage(),
      ),
      GoRoute(
        path: '/favorites',
        builder: (context, state) => const FavoritesPage(),
      ),
      GoRoute(
        path: '/products/:id',
        builder: (context, state) => ProductDetailPage(
          productId: int.parse(state.pathParameters['id']!),
        ),
      ),
      GoRoute(path: '/cart', builder: (context, state) => const CartPage()),
      GoRoute(
        path: '/checkout',
        builder: (context, state) => const CheckoutPage(),
      ),
      GoRoute(
        path: '/addresses',
        builder: (context, state) => const AddressPage(),
      ),
      GoRoute(path: '/orders', builder: (context, state) => const OrdersPage()),
      GoRoute(
        path: '/account',
        builder: (context, state) => const AccountPage(),
      ),
      GoRoute(
        path: '/invoices',
        builder: (context, state) => const InvoicesPage(),
      ),
      GoRoute(
        path: '/invoices/:id',
        builder: (context, state) => InvoiceDetailPage(
          invoiceId: int.parse(state.pathParameters['id']!),
        ),
      ),
      GoRoute(
        path: '/notifications',
        builder: (context, state) => const NotificationsPage(),
      ),
      GoRoute(
        path: '/courier/deliveries',
        builder: (context, state) => const CourierDeliveriesPage(),
      ),
      GoRoute(
        path: '/courier/deliveries/:id',
        builder: (context, state) => CourierDeliveryDetailPage(
          assignmentId: int.parse(state.pathParameters['id']!),
        ),
      ),
      GoRoute(
        path: '/courier/route',
        builder: (context, state) => const CourierRoutePage(),
      ),
      GoRoute(
        path: '/courier/cash',
        builder: (context, state) => const CourierCashPage(),
      ),
      GoRoute(
        path: '/courier/vehicles',
        builder: (context, state) => const CourierVehiclesPage(),
      ),
      GoRoute(
        path: '/admin/seguridad',
        builder: (context, state) => const SecurityPage(),
      ),
      GoRoute(path: '/chat', builder: (context, state) => const ChatPage()),
      GoRoute(
        path: '/chat/destinatarios',
        builder: (context, state) => const NewConversationPage(),
      ),
      GoRoute(
        path: '/chat/:id',
        builder: (context, state) => ConversationPage(
          // «nuevo» es la conversacion que todavia no existe: se abre la
          // pantalla y el primer mensaje la crea. tryParse y no parse, porque
          // un id invalido en la ruta no puede tirar la app.
          conversationId: int.tryParse(state.pathParameters['id'] ?? ''),
          // Con quien se va a hablar cuando la conversacion todavia no existe.
          recipientId: int.tryParse(
            state.uri.queryParameters['destinatario'] ?? '',
          ),
        ),
      ),
      GoRoute(
        path: '/orders/:id',
        builder: (context, state) =>
            OrderDetailPage(orderId: int.parse(state.pathParameters['id']!)),
      ),
      GoRoute(
        path: '/admin',
        builder: (context, state) => const BusinessHubPage(admin: true),
      ),
      GoRoute(
        path: '/documents',
        builder: (context, state) => const DocumentCenterPage(),
      ),
      GoRoute(
        path: '/operations',
        builder: (context, state) => const BusinessHubPage(admin: false),
      ),
      GoRoute(
        path: '/cash/actions',
        builder: (context, state) => const CashActionsPage(),
      ),
      GoRoute(
        path: '/sales/new',
        builder: (context, state) => const DirectSalePage(),
      ),
      GoRoute(
        path: '/purchases/new',
        builder: (context, state) => const PurchaseFormPage(),
      ),
      GoRoute(
        path: '/resources/:key',
        builder: (context, state) {
          final key = state.pathParameters['key'];
          // La auditoría tiene pantalla propia: necesita filtros y un detalle
          // que muestre el antes y el después, cosa que la ficha genérica no
          // sabe hacer.
          if (key == 'audit') return const AuditPage();
          final definition = resources[key];
          if (definition == null) {
            return const Scaffold(
              body: Center(child: Text('Módulo no disponible')),
            );
          }
          return ResourcePage(definition: definition);
        },
      ),
    ],
    errorBuilder: (context, state) => Scaffold(
      body: Center(child: Text('Ruta no encontrada: ${state.uri.path}')),
    ),
  );
  ref.onDispose(() {
    refresh.dispose();
    router.dispose();
  });
  return router;
});

final class _RouterRefresh extends ChangeNotifier {
  void notify() => notifyListeners();
}
