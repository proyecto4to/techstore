# Inventario de controles interactivos (app móvil)

Generado del código con `node docs/transformacion/herramientas/inventario-de-controles.mjs techstore-mobile/lib docs/transformacion/inventario-de-controles.md`. «Sin manejador directo» no es un botón roto: son filas informativas sin `onTap`, campos que trabajan con su controlador o ítems de menú cuya acción está en el menú padre. Cada uno se revisó a mano en la auditoría.

Total de controles: 320

- features/courier/presentation/courier_deliveries_page.dart: 22
- features/courier/presentation/courier_vehicles_page.dart: 19
- features/administration/presentation/smtp_config_page.dart: 18
- features/administration/presentation/resource_page.dart: 17
- features/cash/presentation/cash_actions_page.dart: 16
- features/auth/presentation/account_page.dart: 12
- features/administration/presentation/audit_page.dart: 11
- features/administration/presentation/promotions_page.dart: 11
- features/courier/presentation/courier_cash_page.dart: 11
- features/orders/presentation/orders_page.dart: 11
- features/administration/presentation/security_page.dart: 10
- features/auth/presentation/password_recovery_page.dart: 10
- features/checkout/presentation/checkout_page.dart: 10
- features/purchases/presentation/purchase_form_page.dart: 10
- features/checkout/presentation/address_map_page.dart: 9
- features/courier/presentation/courier_dashboard_page.dart: 9
- features/catalog/presentation/catalog_page.dart: 8
- features/chat/presentation/chat_page.dart: 8
- features/checkout/presentation/address_page.dart: 8
- features/courier/presentation/courier_route_page.dart: 8
- features/sales/presentation/direct_sale_page.dart: 8
- features/auth/presentation/login_page.dart: 7
- features/invoices/presentation/invoices_page.dart: 7
- features/cart/presentation/cart_page.dart: 6
- core/widgets/visor_de_documento.dart: 5
- app/update_progress_dialog.dart: 4
- core/auth/operation_confirm.dart: 4
- features/auth/presentation/register_page.dart: 4
- features/catalog/presentation/favorites_page.dart: 4
- features/chat/presentation/new_conversation_page.dart: 4
- features/orders/presentation/payment_receipts.dart: 4
- app/app_update_gate.dart: 3
- features/administration/presentation/company_location_page.dart: 3
- features/notifications/presentation/notifications_page.dart: 3
- app/home_page.dart: 2
- core/native/biometric_gate.dart: 2
- core/widgets/add_to_cart_button.dart: 2
- core/widgets/documento_page.dart: 2
- core/widgets/image_origin_sheet.dart: 2
- core/widgets/async_states.dart: 1
- core/widgets/money_field.dart: 1
- core/widgets/quantity_stepper.dart: 1
- core/widgets/voice_dictation_button.dart: 1
- features/administration/presentation/business_hub_page.dart: 1
- features/administration/presentation/document_center_page.dart: 1

| Archivo | Línea | Control | Etiqueta | Acción |
|---|---|---|---|---|
| app/app_update_gate.dart | 54 | TextButton | 'Más tarde' | onPressed: () => Navigator.pop(context, false) |
| app/app_update_gate.dart | 58 | FilledButton | 'Actualizar' | onPressed: () => Navigator.pop(context, true) |
| app/app_update_gate.dart | 97 | FilledButton.icon | 'Actualizar con Google Play' | onPressed: _installing ? null : () => _install(policy) |
| app/home_page.dart | 207 | IconButton | 'Cerrar sesión' | onPressed: ref.read(authSessionProvider.notifier).logout |
| app/home_page.dart | 305 | InkWell | label, textAlign: TextAlign.center, maxLines: 2 | onTap: onTap |
| app/update_progress_dialog.dart | 71 | TextButton | 'Permiso necesario' | onPressed: () => Navigator.pop(context) |
| app/update_progress_dialog.dart | 76 | FilledButton | 'Permiso necesario' | onPressed: onReintentar, child: const Text('Reintentar')) |
| app/update_progress_dialog.dart | 95 | TextButton | 'Ahora no' | onPressed: () => Navigator.pop(context, false) |
| app/update_progress_dialog.dart | 99 | FilledButton | 'Conceder permiso' | onPressed: () => Navigator.pop(context, true) |
| core/auth/operation_confirm.dart | 114 | TextFormField | _oculta ? 'Mostrar' : 'Ocultar' | onPressed: () => setState(() => _oculta = !_oculta) |
| core/auth/operation_confirm.dart | 123 | IconButton | _oculta ? 'Mostrar' : 'Ocultar' | onPressed: () => setState(() => _oculta = !_oculta) |
| core/auth/operation_confirm.dart | 147 | TextButton | 'Cancelar' | onPressed: _verificando ? null : () => Navigator.pop(context, false) |
| core/auth/operation_confirm.dart | 151 | FilledButton | 'Confirmar' | onPressed: _verificando ? null : _confirmar |
| core/native/biometric_gate.dart | 125 | FilledButton.icon | 'Desbloquear' | onPressed: _authenticating ? null : _unlock |
| core/native/biometric_gate.dart | 130 | TextButton.icon | 'Usar PIN del dispositivo' | onPressed: _authenticating |
| core/widgets/add_to_cart_button.dart | 77 | IconButton.filledTonal | enabled ? widget.label : widget.disabledLabel | onPressed: onTap |
| core/widgets/add_to_cart_button.dart | 89 | FilledButton.icon | !enabled | onPressed: onTap |
| core/widgets/async_states.dart | 69 | FilledButton | 'Reintentar' | onPressed: onRetry, child: const Text('Reintentar')) |
| core/widgets/documento_page.dart | 114 | IconButton | 'Compartir' | onPressed: () => unawaited( |
| core/widgets/documento_page.dart | 129 | IconButton | 'Imprimir' | onPressed: () => unawaited( |
| core/widgets/image_origin_sheet.dart | 20 | ListTile | cameraLabel | onTap: () => Navigator.pop(context, ImageOrigin.camera) |
| core/widgets/image_origin_sheet.dart | 25 | ListTile | galleryLabel | onTap: () => Navigator.pop(context, ImageOrigin.gallery) |
| core/widgets/money_field.dart | 65 | TextFormField | — | onChanged: onChanged == null |
| core/widgets/quantity_stepper.dart | 121 | IconButton.outlined | tooltip | onPressed: onPressed |
| core/widgets/visor_de_documento.dart | 245 | IconButton | 'Página anterior' | onPressed: onAnterior |
| core/widgets/visor_de_documento.dart | 254 | IconButton | 'Página siguiente' | onPressed: onSiguiente |
| core/widgets/visor_de_documento.dart | 260 | IconButton | 'Alejar' | onPressed: onAlejar |
| core/widgets/visor_de_documento.dart | 269 | IconButton | 'Acercar' | onPressed: onAcercar |
| core/widgets/visor_de_documento.dart | 274 | IconButton | 'Ver la hoja entera' | onPressed: onEncajar |
| core/widgets/voice_dictation_button.dart | 65 | IconButton | _listening ? 'Detener dictado' : widget.tooltip | onPressed: _toggle |
| features/administration/presentation/audit_page.dart | 114 | IconButton | 'Filtros' | onPressed: _abrirFiltros |
| features/administration/presentation/audit_page.dart | 129 | RefreshIndicator | — | onRefresh: () => ref.refresh(auditoriaProvider(_filtro).future) |
| features/administration/presentation/audit_page.dart | 226 | InputChip | '$accion · ${humanizarCodigo(entidad)}' | sin manejador directo |
| features/administration/presentation/audit_page.dart | 245 | ListTile | '$accion · ${humanizarCodigo(entidad)}' | sin manejador directo |
| features/administration/presentation/audit_page.dart | 538 | FilterChip | 'Acción' | onSelected: (elegido) => setState(() { |
| features/administration/presentation/audit_page.dart | 558 | FilterChip | 'Rol de quien lo hizo' | onSelected: (elegido) => setState(() { |
| features/administration/presentation/audit_page.dart | 582 | FilterChip | 'Fechas' | onSelected: (elegido) => setState(() { |
| features/administration/presentation/audit_page.dart | 598 | OutlinedButton.icon | — | onPressed: () => _elegirFecha(desde: true) |
| features/administration/presentation/audit_page.dart | 610 | OutlinedButton.icon | 'Limpiar todo' | onPressed: () => _elegirFecha(desde: false) |
| features/administration/presentation/audit_page.dart | 627 | TextButton | 'Limpiar todo' | onPressed: () => Navigator.pop( |
| features/administration/presentation/audit_page.dart | 637 | FilledButton | 'Aplicar' | onPressed: () => Navigator.pop(context, _filtro) |
| features/administration/presentation/business_hub_page.dart | 126 | InkWell | baldosa.titulo, | onTap: () => context.push(baldosa.ruta) |
| features/administration/presentation/company_location_page.dart | 119 | FilledButton.icon | — | onPressed: () => _editar(context, ref, punto, referencia) |
| features/administration/presentation/company_location_page.dart | 388 | TextField | 'Referencia (opcional)' | onPressed: _guardando ? null : _guardar |
| features/administration/presentation/company_location_page.dart | 400 | FilledButton.icon | 'Confirmar ubicación' | onPressed: _guardando ? null : _guardar |
| features/administration/presentation/document_center_page.dart | 80 | FilledButton.icon | 'Descargar y compartir ZIP' | onPressed: _busy ? null : _export |
| features/administration/presentation/promotions_page.dart | 39 | FloatingActionButton.extended | 'Nueva' | onPressed: () => _abrirFormulario(context, ref) |
| features/administration/presentation/promotions_page.dart | 58 | RefreshIndicator | — | onRefresh: () async => ref.invalidate(promocionesProvider) |
| features/administration/presentation/promotions_page.dart | 121 | Switch | — | onChanged: (valor) => _cambiarEstado(context, ref, valor) |
| features/administration/presentation/promotions_page.dart | 255 | TextFormField | 'Nombre' | sin manejador directo |
| features/administration/presentation/promotions_page.dart | 267 | TextFormField | 'Descuento (%)' | sin manejador directo |
| features/administration/presentation/promotions_page.dart | 360 | TextButton | 'Cancelar' | onPressed: _guardando |
| features/administration/presentation/promotions_page.dart | 367 | FilledButton | _guardando ? 'Guardando…' : 'Crear' | onPressed: _guardando ? null : _guardar |
| features/administration/presentation/promotions_page.dart | 476 | TextField | 'Buscar' | onPressed: _buscando ? null : _buscar |
| features/administration/presentation/promotions_page.dart | 483 | IconButton | 'Buscar' | onPressed: _buscando ? null : _buscar |
| features/administration/presentation/promotions_page.dart | 498 | ListTile | producto['nombre']?.toString( | onTap: () { |
| features/administration/presentation/promotions_page.dart | 531 | ListTile | etiqueta | onTap: () async { |
| features/administration/presentation/resource_page.dart | 40 | IconButton | 'Editar' | onPressed: () => Navigator.of(context).push( |
| features/administration/presentation/resource_page.dart | 48 | IconButton | 'Ubicación' | onPressed: () => Navigator.of(context).push( |
| features/administration/presentation/resource_page.dart | 58 | IconButton | 'Documento' | onPressed: () => _runDocument(context, ref, document) |
| features/administration/presentation/resource_page.dart | 65 | RefreshIndicator | error is AppFailure | onRefresh: () => ref.refresh(resourceProvider(definition).future) |
| features/administration/presentation/resource_page.dart | 86 | FilledButton | 'Reintentar' | onPressed: () => ref.invalidate(resourceProvider(definition)) |
| features/administration/presentation/resource_page.dart | 131 | FloatingActionButton.extended | 'Crear' | onPressed: () => _runCreate(context, ref) |
| features/administration/presentation/resource_page.dart | 547 | ListTile | 'Abrir' | onTap: () => Navigator.pop(context, DocumentAction.open) |
| features/administration/presentation/resource_page.dart | 552 | ListTile | 'Compartir' | onTap: () => Navigator.pop(context, DocumentAction.share) |
| features/administration/presentation/resource_page.dart | 558 | ListTile | 'Imprimir' | onTap: () => Navigator.pop(context, DocumentAction.print) |
| features/administration/presentation/resource_page.dart | 650 | TextButton | 'Volver' | onPressed: () => Navigator.pop(context, false) |
| features/administration/presentation/resource_page.dart | 654 | FilledButton | 'Confirmar' | onPressed: () => Navigator.pop(context, true) |
| features/administration/presentation/resource_page.dart | 733 | PopupMenuItem | action.$2 | sin manejador directo |
| features/administration/presentation/resource_page.dart | 789 | TextButton | field.label | onPressed: () => Navigator.pop(context) |
| features/administration/presentation/resource_page.dart | 793 | FilledButton | field.label | onPressed: () { |
| features/administration/presentation/resource_page.dart | 806 | SwitchListTile | field.label | onChanged: (value) => setState(() => _values[field.name] = value) |
| features/administration/presentation/resource_page.dart | 829 | TextFormField | — | sin manejador directo |
| features/administration/presentation/resource_page.dart | 850 | TextFormField | — | sin manejador directo |
| features/administration/presentation/security_page.dart | 109 | ChoiceChip | — | onSelected: (_) => setState(() => _rol = rol) |
| features/administration/presentation/security_page.dart | 222 | SwitchListTile | _nombreDePantalla(pantalla['pantalla']?.toString( | onChanged: (valor) => onGuardar(rol, { |
| features/administration/presentation/security_page.dart | 268 | FilterChip | — | onSelected: (valor) => |
| features/administration/presentation/security_page.dart | 329 | TextField | 'Buscar por nombre o correo' | onSubmitted: (valor) => setState(() => _busqueda = valor) |
| features/administration/presentation/security_page.dart | 362 | ListTile | nombre.isEmpty ? '—' : nombre | onTap: () => setState(() { |
| features/administration/presentation/security_page.dart | 416 | ListTile | 'Motivo de la excepción (opcional)' | onPressed: widget.onVolver) |
| features/administration/presentation/security_page.dart | 426 | TextField | 'Motivo de la excepción (opcional)' | sin manejador directo |
| features/administration/presentation/security_page.dart | 613 | TextButton | 'Conceder' | onPressed: () => onConceder(clave) |
| features/administration/presentation/security_page.dart | 618 | TextButton | 'Restringir' | onPressed: () => onRestringir(clave) |
| features/administration/presentation/security_page.dart | 623 | TextButton | 'Heredar' | onPressed: () => onHeredar(clave) |
| features/administration/presentation/smtp_config_page.dart | 122 | TextFormField | 'Servidor SMTP' | sin manejador directo |
| features/administration/presentation/smtp_config_page.dart | 133 | TextFormField | 'Puerto' | sin manejador directo |
| features/administration/presentation/smtp_config_page.dart | 185 | TextFormField | 'Usuario' | onPressed: _guardando |
| features/administration/presentation/smtp_config_page.dart | 193 | ListTile | 'Contraseña del servidor' | onPressed: _guardando |
| features/administration/presentation/smtp_config_page.dart | 198 | TextButton | _ocultaPassword ? 'Mostrar' : 'Ocultar' | onPressed: _guardando |
| features/administration/presentation/smtp_config_page.dart | 206 | TextFormField | _ocultaPassword ? 'Mostrar' : 'Ocultar' | onPressed: () => |
| features/administration/presentation/smtp_config_page.dart | 215 | IconButton | _ocultaPassword ? 'Mostrar' : 'Ocultar' | onPressed: () => |
| features/administration/presentation/smtp_config_page.dart | 230 | TextFormField | 'Correo remitente' | sin manejador directo |
| features/administration/presentation/smtp_config_page.dart | 245 | TextFormField | 'Guardar configuración' | onPressed: _guardando ? null : _guardar |
| features/administration/presentation/smtp_config_page.dart | 255 | FilledButton.icon | 'Guardar configuración' | onPressed: _guardando ? null : _guardar |
| features/administration/presentation/smtp_config_page.dart | 267 | OutlinedButton.icon | 'Probar configuración' | onPressed: _guardando \|\| !configurado ? null : _probar |
| features/administration/presentation/smtp_config_page.dart | 343 | TextField | 'Tu contraseña' | onSubmitted: (valor) => Navigator.pop(context, valor) |
| features/administration/presentation/smtp_config_page.dart | 353 | TextButton | 'Cancelar' | onPressed: () => Navigator.pop(context) |
| features/administration/presentation/smtp_config_page.dart | 357 | FilledButton | 'Continuar' | onPressed: () => Navigator.pop(context, controlador.text) |
| features/administration/presentation/smtp_config_page.dart | 413 | TextField | 'Destinatario' | onSubmitted: (valor) => Navigator.pop(context, valor) |
| features/administration/presentation/smtp_config_page.dart | 423 | TextButton | ok ? 'Envío exitoso' | onPressed: () => Navigator.pop(context) |
| features/administration/presentation/smtp_config_page.dart | 427 | FilledButton | ok ? 'Envío exitoso' : 'No se pudo enviar' | onPressed: () => Navigator.pop(context, controlador.text.trim()) |
| features/administration/presentation/smtp_config_page.dart | 452 | FilledButton | 'Entendido' | onPressed: () => Navigator.pop(context) |
| features/auth/presentation/account_page.dart | 57 | ListTile | 'RUC' | sin manejador directo |
| features/auth/presentation/account_page.dart | 68 | TextField | 'RUC' | onPressed: _busy ? null : _saveTaxData |
| features/auth/presentation/account_page.dart | 73 | TextField | 'Razón social' | onPressed: _busy ? null : _saveTaxData |
| features/auth/presentation/account_page.dart | 78 | FilledButton.tonal | 'Contraseña actual' | onPressed: _busy ? null : _saveTaxData |
| features/auth/presentation/account_page.dart | 87 | TextField | 'Contraseña actual' | onPressed: _busy ? null : _changePassword |
| features/auth/presentation/account_page.dart | 93 | TextField | 'Nueva contraseña' | onPressed: _busy ? null : _changePassword |
| features/auth/presentation/account_page.dart | 99 | TextField | 'Confirmar nueva contraseña' | onPressed: _busy ? null : _changePassword |
| features/auth/presentation/account_page.dart | 109 | OutlinedButton | 'Bloqueo biométrico' | onPressed: _busy ? null : _changePassword |
| features/auth/presentation/account_page.dart | 116 | SwitchListTile | 'Bloqueo biométrico' | onChanged: _busy ? null : _setBiometric |
| features/auth/presentation/account_page.dart | 125 | ListTile | 'Bloqueo biométrico no disponible' | sin manejador directo |
| features/auth/presentation/account_page.dart | 137 | SwitchListTile | 'Confirmar operaciones sensibles' | onChanged: _busy ? null : _setOperationGuard |
| features/auth/presentation/account_page.dart | 151 | SwitchListTile | 'Vibración al confirmar' | onChanged: _busy ? null : _setHaptics |
| features/auth/presentation/login_page.dart | 138 | TextFormField | — | sin manejador directo |
| features/auth/presentation/login_page.dart | 158 | TextFormField | _obscure | sin manejador directo |
| features/auth/presentation/login_page.dart | 169 | IconButton | _obscure | onPressed: () => setState( |
| features/auth/presentation/login_page.dart | 208 | FilledButton.icon | — | onPressed: auth.isBusy ? null : _submit |
| features/auth/presentation/login_page.dart | 230 | TextButton | 'Olvidé mi contraseña' | onPressed: () => |
| features/auth/presentation/login_page.dart | 238 | OutlinedButton.icon | 'Ver catálogo sin iniciar sesión' | onPressed: () => context.go('/catalog') |
| features/auth/presentation/login_page.dart | 253 | TextButton | 'Crear una cuenta' | onPressed: () => |
| features/auth/presentation/password_recovery_page.dart | 76 | FilledButton | 'Volver al ingreso' | onPressed: () => context.go('/login') |
| features/auth/presentation/password_recovery_page.dart | 81 | TextButton | 'Usar otro correo' | onPressed: () => setState(() => _sent = false) |
| features/auth/presentation/password_recovery_page.dart | 100 | TextField | 'Correo electrónico' | onChanged: _error == null |
| features/auth/presentation/password_recovery_page.dart | 115 | FilledButton | _sending ? 'Enviando...' : 'Enviar enlace' | onPressed: _sending ? null : _send |
| features/auth/presentation/password_recovery_page.dart | 224 | FilledButton | 'Iniciar sesión' | onPressed: () => context.go('/login') |
| features/auth/presentation/password_recovery_page.dart | 246 | FilledButton | 'Pedir un enlace nuevo' | onPressed: () => context.go('/password/forgot') |
| features/auth/presentation/password_recovery_page.dart | 286 | TextField | 'Nueva contraseña' | onChanged: (_) => setState(() => _error = null) |
| features/auth/presentation/password_recovery_page.dart | 298 | TextField | 'Repetila para confirmar' | onChanged: (_) => setState(() => _error = null) |
| features/auth/presentation/password_recovery_page.dart | 312 | TextButton.icon | _sending ? 'Guardando...' : 'Guardar contraseña nueva' | onPressed: () => setState(() => _reveal = !_reveal) |
| features/auth/presentation/password_recovery_page.dart | 322 | FilledButton | _sending ? 'Guardando...' : 'Guardar contraseña nueva' | onPressed: _sending \|\| !_canSave ? null : _reset |
| features/auth/presentation/register_page.dart | 48 | TextFormField | — | sin manejador directo |
| features/auth/presentation/register_page.dart | 67 | TextFormField | _obscure | onPressed: () => setState(() => _obscure = !_obscure) |
| features/auth/presentation/register_page.dart | 76 | IconButton | _obscure | onPressed: () => setState(() => _obscure = !_obscure) |
| features/auth/presentation/register_page.dart | 102 | FilledButton | 'Crear cuenta' | onPressed: auth.isBusy |
| features/cart/presentation/cart_page.dart | 86 | RefreshIndicator | — | onRefresh: _refreshAvailability |
| features/cart/presentation/cart_page.dart | 128 | TextButton | 'Volver' | onPressed: () => Navigator.pop(context, false) |
| features/cart/presentation/cart_page.dart | 132 | FilledButton | 'Quitar del carrito' | onPressed: () => Navigator.pop(context, true) |
| features/cart/presentation/cart_page.dart | 210 | IconButton | 'Quitar ${product.name} del carrito' | onPressed: onRemove |
| features/cart/presentation/cart_page.dart | 390 | FilledButton.icon | 'Continuar con la compra' | onPressed: blocked ? null : onContinue |
| features/cart/presentation/cart_page.dart | 435 | FilledButton.icon | 'Ver productos' | onPressed: onBrowse |
| features/cash/presentation/cash_actions_page.dart | 39 | RefreshIndicator | — | onRefresh: () => ref.refresh(cajaAbiertaProvider.future) |
| features/cash/presentation/cash_actions_page.dart | 99 | FilledButton.icon | 'Abrir caja' | onPressed: _enviando ? null : _abrir |
| features/cash/presentation/cash_actions_page.dart | 345 | ListTile | title, | onTap: onTap |
| features/cash/presentation/cash_actions_page.dart | 402 | TextFormField | 'Notas (opcional)' | onPressed: () => Navigator.pop(context) |
| features/cash/presentation/cash_actions_page.dart | 413 | TextButton | 'Cancelar' | onPressed: () => Navigator.pop(context) |
| features/cash/presentation/cash_actions_page.dart | 417 | FilledButton | 'Abrir caja' | onPressed: () { |
| features/cash/presentation/cash_actions_page.dart | 510 | TextFormField | 'Concepto' | onPressed: () => Navigator.pop(context) |
| features/cash/presentation/cash_actions_page.dart | 518 | TextFormField | 'Referencia (opcional)' | onPressed: () => Navigator.pop(context) |
| features/cash/presentation/cash_actions_page.dart | 530 | TextButton | 'Cancelar' | onPressed: () => Navigator.pop(context) |
| features/cash/presentation/cash_actions_page.dart | 534 | FilledButton | 'Registrar' | onPressed: () { |
| features/cash/presentation/cash_actions_page.dart | 667 | TextFormField | 'Comprobante (opcional)' | onPressed: () => Navigator.pop(context) |
| features/cash/presentation/cash_actions_page.dart | 680 | TextButton | 'Cancelar' | onPressed: () => Navigator.pop(context) |
| features/cash/presentation/cash_actions_page.dart | 685 | FilledButton | 'Registrar pago' | onPressed: () { |
| features/cash/presentation/cash_actions_page.dart | 782 | TextField | '0' | onChanged: (valor) => setState( |
| features/cash/presentation/cash_actions_page.dart | 833 | TextButton | 'Cancelar' | onPressed: () => Navigator.pop(context) |
| features/cash/presentation/cash_actions_page.dart | 837 | FilledButton | 'Cerrar caja' | onPressed: unidades == 0 ? null : () => Navigator.pop(context, total) |
| features/catalog/presentation/catalog_page.dart | 133 | RefreshIndicator | 'Limpiar búsqueda' | onRefresh: () => ref.refresh(catalogProvider(query).future) |
| features/catalog/presentation/catalog_page.dart | 140 | SearchBar | 'Limpiar búsqueda' | onSubmitted: _applySearch |
| features/catalog/presentation/catalog_page.dart | 147 | IconButton | 'Limpiar búsqueda' | onPressed: () { |
| features/catalog/presentation/catalog_page.dart | 190 | TextButton | 'Limpiar' | onPressed: _clearFilters |
| features/catalog/presentation/catalog_page.dart | 272 | IconButton | count == 0 | onPressed: () => context.push('/cart') |
| features/catalog/presentation/catalog_page.dart | 314 | FilledButton.tonal | 'Limpiar filtros' | onPressed: onClear |
| features/catalog/presentation/catalog_page.dart | 504 | OutlinedButton.icon | — | onPressed: () async { |
| features/catalog/presentation/catalog_page.dart | 539 | InkWell | — | onTap: onOpen |
| features/catalog/presentation/favorites_page.dart | 23 | RefreshIndicator | — | onRefresh: () => ref.refresh(favoritesProvider.future) |
| features/catalog/presentation/favorites_page.dart | 66 | ListTile | product.name | onTap: () => context.push('/products/${product.id}') |
| features/catalog/presentation/favorites_page.dart | 97 | IconButton | 'Quitar de favoritos' | onPressed: () async { |
| features/catalog/presentation/favorites_page.dart | 149 | FilledButton.icon | 'Ver productos' | onPressed: onBrowse |
| features/chat/presentation/chat_page.dart | 71 | RefreshIndicator | — | onRefresh: () => ref.refresh(conversationsProvider.future) |
| features/chat/presentation/chat_page.dart | 97 | ListTile | '$sinLeer' | sin manejador directo |
| features/chat/presentation/chat_page.dart | 155 | FloatingActionButton.extended | 'Nueva conversación' | onPressed: () => context.push('/chat/destinatarios') |
| features/chat/presentation/chat_page.dart | 160 | RefreshIndicator | — | onRefresh: () => ref.refresh(conversationsProvider.future) |
| features/chat/presentation/chat_page.dart | 242 | ListTile | '$sinLeer' | sin manejador directo |
| features/chat/presentation/chat_page.dart | 359 | TextField | 'Dictar mensaje' | onChanged: (_) => setState(() {}) |
| features/chat/presentation/chat_page.dart | 380 | IconButton.filled | 'Enviar' | onPressed: _busy \|\| _message.text.trim().isEmpty |
| features/chat/presentation/chat_page.dart | 574 | RefreshIndicator | — | onRefresh: () => |
| features/chat/presentation/new_conversation_page.dart | 105 | TextField | 'Limpiar' | onChanged: _buscar |
| features/chat/presentation/new_conversation_page.dart | 115 | IconButton | 'Limpiar' | onPressed: () { |
| features/chat/presentation/new_conversation_page.dart | 197 | FilterChip | _iniciales(nombre | onSelected: (_) => onTap() |
| features/chat/presentation/new_conversation_page.dart | 220 | ListTile | nombre | sin manejador directo |
| features/checkout/presentation/address_map_page.dart | 462 | TextField | 'Buscar una calle o un lugar' | onChanged: onEscribir |
| features/checkout/presentation/address_map_page.dart | 494 | ListTile | lugar.nombre, maxLines: 2 | onTap: () => onElegir(lugar) |
| features/checkout/presentation/address_map_page.dart | 555 | TextFormField | 'Cómo le decís a este lugar' | onPressed: () => descripcion.text = sugerencia |
| features/checkout/presentation/address_map_page.dart | 571 | ActionChip | 'Referencia para encontrarlo (opcional)' | onPressed: () => descripcion.text = sugerencia |
| features/checkout/presentation/address_map_page.dart | 578 | TextFormField | 'Referencia para encontrarlo (opcional)' | sin manejador directo |
| features/checkout/presentation/address_map_page.dart | 593 | TextFormField | 'Nombre de quien recibe' | sin manejador directo |
| features/checkout/presentation/address_map_page.dart | 607 | TextFormField | 'Teléfono de contacto' | onChanged: onPrincipal |
| features/checkout/presentation/address_map_page.dart | 622 | SwitchListTile | 'Usar como dirección principal' | onChanged: onPrincipal |
| features/checkout/presentation/address_map_page.dart | 630 | FilledButton.icon | — | onPressed: guardando ? null : () => onGuardar() |
| features/checkout/presentation/address_page.dart | 45 | FloatingActionButton.extended | 'Agregar' | onPressed: () => _abrirMapa(context, ref) |
| features/checkout/presentation/address_page.dart | 82 | TextButton | 'Cancelar' | onPressed: () => Navigator.pop(dialogo, false) |
| features/checkout/presentation/address_page.dart | 86 | FilledButton | 'Borrar' | onPressed: () => Navigator.pop(dialogo, true) |
| features/checkout/presentation/address_page.dart | 139 | InkWell | direccion.label, | onTap: onEditar |
| features/checkout/presentation/address_page.dart | 200 | MenuItemButton | 'Opciones de ${direccion.label}' | onPressed: onEditar |
| features/checkout/presentation/address_page.dart | 205 | MenuItemButton | 'Opciones de ${direccion.label}' | onPressed: onEliminar |
| features/checkout/presentation/address_page.dart | 211 | IconButton | 'Opciones de ${direccion.label}' | onPressed: () => controlador.isOpen |
| features/checkout/presentation/address_page.dart | 286 | FilledButton.icon | 'Marcar mi dirección' | onPressed: onAgregar |
| features/checkout/presentation/checkout_page.dart | 207 | ListTile | quote.name | onTap: () => Navigator.pop(context, quote) |
| features/checkout/presentation/checkout_page.dart | 399 | FilledButton.tonalIcon | 'Agregar dirección' | onPressed: () => context.push('/addresses') |
| features/checkout/presentation/checkout_page.dart | 431 | OutlinedButton.icon | 'Elegir forma de envío' | onPressed: onPickQuote |
| features/checkout/presentation/checkout_page.dart | 439 | ListTile | quote!.name | onTap: onPickQuote |
| features/checkout/presentation/checkout_page.dart | 494 | TextField | 'Aclaraciones para el pedido' | sin manejador directo |
| features/checkout/presentation/checkout_page.dart | 649 | ListTile | title | sin manejador directo |
| features/checkout/presentation/checkout_page.dart | 688 | InkWell | — | onTap: onTap |
| features/checkout/presentation/checkout_page.dart | 795 | OutlinedButton | step == 0 ? 'Volver al carrito' : 'Atrás', | onPressed: onBack |
| features/checkout/presentation/checkout_page.dart | 812 | FilledButton.icon | — | onPressed: busy \|\| !canContinue ? null : onNext |
| features/checkout/presentation/checkout_page.dart | 877 | FilledButton.icon | 'Ver productos' | onPressed: () => context.go('/catalog') |
| features/courier/presentation/courier_cash_page.dart | 25 | RefreshIndicator | 'No se pudo consultar el saldo' | onRefresh: _refresh |
| features/courier/presentation/courier_cash_page.dart | 65 | FilledButton.icon | 'Vaciar caja' | onPressed: _busy \|\| vacia |
| features/courier/presentation/courier_cash_page.dart | 94 | ListTile | 'No se pudieron cargar las solicitudes' | sin manejador directo |
| features/courier/presentation/courier_cash_page.dart | 98 | ListTile | 'Todavía no realizaste transferencias' | sin manejador directo |
| features/courier/presentation/courier_cash_page.dart | 111 | ListTile | 'No se pudieron cargar los movimientos' | sin manejador directo |
| features/courier/presentation/courier_cash_page.dart | 115 | ListTile | 'Sin movimientos registrados' | sin manejador directo |
| features/courier/presentation/courier_cash_page.dart | 175 | TextField | 'Observación opcional' | onPressed: () => Navigator.pop(context) |
| features/courier/presentation/courier_cash_page.dart | 187 | TextButton | 'Cancelar' | onPressed: () => Navigator.pop(context) |
| features/courier/presentation/courier_cash_page.dart | 191 | FilledButton | 'Confirmar solicitud' | onPressed: selected == null |
| features/courier/presentation/courier_cash_page.dart | 251 | ListTile | _money(item['monto'], '${item['moneda']}' | sin manejador directo |
| features/courier/presentation/courier_cash_page.dart | 290 | ListTile | '${item[' | sin manejador directo |
| features/courier/presentation/courier_dashboard_page.dart | 21 | IconButton | 'Notificaciones' | onPressed: () => context.push('/notifications') |
| features/courier/presentation/courier_dashboard_page.dart | 26 | IconButton | 'Cerrar sesión' | onPressed: ref.read(authSessionProvider.notifier).logout |
| features/courier/presentation/courier_dashboard_page.dart | 33 | RefreshIndicator | 'Hola, ${user.firstName}' | onRefresh: () async { |
| features/courier/presentation/courier_dashboard_page.dart | 102 | ListTile | 'Saldo en mi caja' | onTap: () => context.push('/courier/cash') |
| features/courier/presentation/courier_dashboard_page.dart | 119 | ListTile | 'Consultando vehículo' | onTap: () => context.push('/courier/vehicles') |
| features/courier/presentation/courier_dashboard_page.dart | 123 | ListTile | 'Vehículos' | onTap: () => context.push('/courier/vehicles') |
| features/courier/presentation/courier_dashboard_page.dart | 129 | ListTile | use?['vehiculo']?.toString( | onTap: () => context.push('/courier/vehicles') |
| features/courier/presentation/courier_dashboard_page.dart | 225 | ListTile | title | onTap: onTap |
| features/courier/presentation/courier_dashboard_page.dart | 268 | FilledButton.tonal | 'Reintentar' | onPressed: onPressed |
| features/courier/presentation/courier_deliveries_page.dart | 39 | RefreshIndicator | — | onRefresh: () => ref.refresh(courierDeliveriesProvider.future) |
| features/courier/presentation/courier_deliveries_page.dart | 81 | RefreshIndicator | — | onRefresh: () => ref.refresh(courierClientsProvider.future) |
| features/courier/presentation/courier_deliveries_page.dart | 111 | ListTile | 'Pedido #${item[' | onTap: () => context.push( |
| features/courier/presentation/courier_deliveries_page.dart | 141 | ListTile | 'Pedido #${item[' | onTap: () => context.push('/courier/deliveries/${item['id']}') |
| features/courier/presentation/courier_deliveries_page.dart | 217 | ListTile | '${item[' | sin manejador directo |
| features/courier/presentation/courier_deliveries_page.dart | 225 | ListTile | '${item[' | onTap: item['telefono'] == null |
| features/courier/presentation/courier_deliveries_page.dart | 240 | ListTile | '${item[' | sin manejador directo |
| features/courier/presentation/courier_deliveries_page.dart | 255 | ListTile | 'Importe' | sin manejador directo |
| features/courier/presentation/courier_deliveries_page.dart | 261 | ListTile | 'Forma de pago' | onPressed: _busy ? null : () => _navigate(item) |
| features/courier/presentation/courier_deliveries_page.dart | 274 | ListTile | 'Abrir navegación' | onPressed: _busy ? null : () => _navigate(item) |
| features/courier/presentation/courier_deliveries_page.dart | 281 | FilledButton.tonalIcon | 'Abrir navegación' | onPressed: _busy ? null : () => _navigate(item) |
| features/courier/presentation/courier_deliveries_page.dart | 290 | FilledButton.icon | 'Registrar cobro de ${_money(item[' | onPressed: _busy ? null : () => _collect(item) |
| features/courier/presentation/courier_deliveries_page.dart | 299 | ListTile | 'Cobro registrado' | sin manejador directo |
| features/courier/presentation/courier_deliveries_page.dart | 365 | ListTile | 'Esta entrega ya finalizó' | sin manejador directo |
| features/courier/presentation/courier_deliveries_page.dart | 403 | TextButton | '¿No se pudo entregar?' | onPressed: () => Navigator.pop(context, false) |
| features/courier/presentation/courier_deliveries_page.dart | 407 | FilledButton | '¿No se pudo entregar?' | onPressed: () => Navigator.pop(context, true) |
| features/courier/presentation/courier_deliveries_page.dart | 438 | TextField | 'Motivo' | onChanged: error == null |
| features/courier/presentation/courier_deliveries_page.dart | 457 | TextButton | 'Volver' | onPressed: () => Navigator.pop(context) |
| features/courier/presentation/courier_deliveries_page.dart | 461 | FilledButton | 'Registrar el problema' | onPressed: () { |
| features/courier/presentation/courier_deliveries_page.dart | 602 | FilledButton | 'Reintentar' | onPressed: retry, child: const Text('Reintentar')) |
| features/courier/presentation/courier_deliveries_page.dart | 629 | FilledButton.icon | — | onPressed: busy ? null : onPressed |
| features/courier/presentation/courier_deliveries_page.dart | 661 | OutlinedButton | label | onPressed: busy ? null : onPressed |
| features/courier/presentation/courier_route_page.dart | 26 | RefreshIndicator | 'No se pudo calcular la ruta' | onRefresh: () => ref.refresh(courierRouteProvider.future) |
| features/courier/presentation/courier_route_page.dart | 35 | FilledButton | 'Reintentar' | onPressed: () => ref.invalidate(courierRouteProvider) |
| features/courier/presentation/courier_route_page.dart | 110 | ChoiceChip | 'Punto ${point[' | onSelected: (_) => setState( |
| features/courier/presentation/courier_route_page.dart | 124 | ListTile | 'Pedido #${selected[' | onPressed: () => context.push( |
| features/courier/presentation/courier_route_page.dart | 138 | OutlinedButton.icon | 'Detalle' | onPressed: () => context.push( |
| features/courier/presentation/courier_route_page.dart | 148 | FilledButton.icon | 'Navegar' | onPressed: () => _navigate(selected) |
| features/courier/presentation/courier_route_page.dart | 166 | ListTile | 'Pedido #${point[' | onTap: () => setState( |
| features/courier/presentation/courier_route_page.dart | 179 | ListTile | '${withoutLocation.length} entrega(s) sin coordenadas' | sin manejador directo |
| features/courier/presentation/courier_vehicles_page.dart | 25 | RefreshIndicator | 'No se pudo consultar el uso actual' | onRefresh: _refresh |
| features/courier/presentation/courier_vehicles_page.dart | 32 | ListTile | 'No se pudo consultar el uso actual' | sin manejador directo |
| features/courier/presentation/courier_vehicles_page.dart | 37 | ListTile | 'No tenés un vehículo activo' | sin manejador directo |
| features/courier/presentation/courier_vehicles_page.dart | 61 | ListTile | 'No se pudo cargar la flota' | sin manejador directo |
| features/courier/presentation/courier_vehicles_page.dart | 63 | ListTile | 'No hay vehículos registrados' | sin manejador directo |
| features/courier/presentation/courier_vehicles_page.dart | 68 | ListTile | '${item[' | sin manejador directo |
| features/courier/presentation/courier_vehicles_page.dart | 82 | FilledButton | 'Usar' | onPressed: _busy \|\| current.value != null |
| features/courier/presentation/courier_vehicles_page.dart | 159 | TextField | — | onChanged: mileageError == null |
| features/courier/presentation/courier_vehicles_page.dart | 177 | TextField | 'Combustible (0 a 100 %)' | onChanged: fuelError == null |
| features/courier/presentation/courier_vehicles_page.dart | 191 | TextField | 'Observación opcional' | onPressed: () => Navigator.pop(context) |
| features/courier/presentation/courier_vehicles_page.dart | 203 | TextButton | 'Cancelar' | onPressed: () => Navigator.pop(context) |
| features/courier/presentation/courier_vehicles_page.dart | 207 | FilledButton | — | onPressed: () { |
| features/courier/presentation/courier_vehicles_page.dart | 328 | OutlinedButton.icon | 'Agregar evento' | onPressed: busy ? null : onEvent |
| features/courier/presentation/courier_vehicles_page.dart | 333 | FilledButton.icon | 'DEJAR VEHÍCULO' | onPressed: busy ? null : onFinish |
| features/courier/presentation/courier_vehicles_page.dart | 459 | SwitchListTile | 'Puede continuar' | onChanged: (value) => setState(() => _canContinue = value) |
| features/courier/presentation/courier_vehicles_page.dart | 469 | SwitchListTile | 'Enviar vehículo a mantenimiento' | onChanged: (value) => setState(() => _maintenance = value) |
| features/courier/presentation/courier_vehicles_page.dart | 481 | TextButton | 'Cancelar' | onPressed: () => Navigator.pop(context) |
| features/courier/presentation/courier_vehicles_page.dart | 485 | FilledButton | 'Registrar evento' | onPressed: _submit, child: const Text('Registrar evento')) |
| features/courier/presentation/courier_vehicles_page.dart | 496 | TextField | — | sin manejador directo |
| features/invoices/presentation/invoices_page.dart | 28 | RefreshIndicator | — | onRefresh: () => ref.refresh(invoicesProvider(_page).future) |
| features/invoices/presentation/invoices_page.dart | 164 | FilledButton.icon | 'Ver factura PDF' | onPressed: () => _showDocumentActions(context, ref, value) |
| features/invoices/presentation/invoices_page.dart | 190 | ListTile | item.$3 | onTap: () => Navigator.pop(context, item.$1) |
| features/invoices/presentation/invoices_page.dart | 254 | InkWell | 'Factura ${invoice.number}' | onTap: onTap |
| features/invoices/presentation/invoices_page.dart | 390 | IconButton.filledTonal | 'Página anterior' | onPressed: onPrevious |
| features/invoices/presentation/invoices_page.dart | 396 | IconButton.filledTonal | 'Página siguiente' | onPressed: onNext |
| features/invoices/presentation/invoices_page.dart | 430 | FilledButton.tonal | actionLabel! | onPressed: onAction |
| features/notifications/presentation/notifications_page.dart | 56 | TextButton | 'Leer todas' | onPressed: pendientes == 0 ? null : () => _leerTodas(context, ref) |
| features/notifications/presentation/notifications_page.dart | 64 | RefreshIndicator | — | onRefresh: () => ref.refresh(notificationsProvider.future) |
| features/notifications/presentation/notifications_page.dart | 129 | ListTile | titulo, | onTap: () => _abrir(context, leida: leida) |
| features/orders/presentation/orders_page.dart | 33 | RefreshIndicator | — | onRefresh: () => ref.refresh(ordersProvider.future) |
| features/orders/presentation/orders_page.dart | 52 | ListTile | 'Pedido N.º ${order.number}' | sin manejador directo |
| features/orders/presentation/orders_page.dart | 124 | FilledButton.icon | 'Ver productos' | onPressed: onBrowse |
| features/orders/presentation/orders_page.dart | 188 | ListTile | line['productoNombre']?.toString( | sin manejador directo |
| features/orders/presentation/orders_page.dart | 203 | ListTile | 'Total' | sin manejador directo |
| features/orders/presentation/orders_page.dart | 239 | ListTile | shipping.label | sin manejador directo |
| features/orders/presentation/orders_page.dart | 261 | FilledButton.tonalIcon | 'Descargar factura' | onPressed: () => _invoice(context, ref) |
| features/orders/presentation/orders_page.dart | 268 | OutlinedButton.icon | 'Cancelar pedido' | onPressed: () => _cancel(context, ref, data['numero']) |
| features/orders/presentation/orders_page.dart | 300 | TextButton | 'Volver' | onPressed: () => Navigator.pop(context, false) |
| features/orders/presentation/orders_page.dart | 304 | FilledButton | 'Cancelar pedido' | onPressed: () => Navigator.pop(context, true) |
| features/orders/presentation/orders_page.dart | 354 | ListTile | item.$3 | onTap: () => Navigator.pop(context, item.$1) |
| features/orders/presentation/payment_receipts.dart | 106 | FilledButton.tonalIcon | — | onPressed: _ocupado \|\| completo ? null : _adjuntar |
| features/orders/presentation/payment_receipts.dart | 156 | TextButton | 'Volver' | onPressed: () => Navigator.pop(context, false) |
| features/orders/presentation/payment_receipts.dart | 160 | FilledButton | 'Quitar' | onPressed: () => Navigator.pop(context, true) |
| features/orders/presentation/payment_receipts.dart | 235 | IconButton.filledTonal | 'Quitar comprobante' | onPressed: onQuitar |
| features/purchases/presentation/purchase_form_page.dart | 106 | TextField | 'Buscar producto por voz' | sin manejador directo |
| features/purchases/presentation/purchase_form_page.dart | 127 | ListTile | 'Eliminar línea' | onPressed: () => |
| features/purchases/presentation/purchase_form_page.dart | 129 | IconButton | 'Eliminar línea' | onPressed: () => |
| features/purchases/presentation/purchase_form_page.dart | 139 | TextField | 'Cantidad' | sin manejador directo |
| features/purchases/presentation/purchase_form_page.dart | 161 | TextField | 'Comparar proveedores' | onPressed: _busy \|\| _lines.isEmpty ? null : _compare |
| features/purchases/presentation/purchase_form_page.dart | 167 | OutlinedButton.icon | 'Comparar proveedores' | onPressed: _busy \|\| _lines.isEmpty ? null : _compare |
| features/purchases/presentation/purchase_form_page.dart | 172 | FilledButton.icon | 'Registrar compra' | onPressed: _busy \|\| _lines.isEmpty \|\| _supplierId == null |
| features/purchases/presentation/purchase_form_page.dart | 212 | FilledButton | 'Cerrar' | onPressed: () => Navigator.pop(context) |
| features/purchases/presentation/purchase_form_page.dart | 285 | TextButton | 'Fotografiar' | onPressed: () => Navigator.pop(context, false) |
| features/purchases/presentation/purchase_form_page.dart | 289 | FilledButton.icon | 'Fotografiar' | onPressed: () => Navigator.pop(context, true) |
| features/sales/presentation/direct_sale_page.dart | 115 | TextField | 'Buscar producto por voz' | onChanged: _onSearchChanged |
| features/sales/presentation/direct_sale_page.dart | 164 | ListTile | 'Agregar ${product.name}' | onPressed: () => _add(product) |
| features/sales/presentation/direct_sale_page.dart | 176 | IconButton.filledTonal | 'Agregar ${product.name}' | onPressed: () => _add(product) |
| features/sales/presentation/direct_sale_page.dart | 199 | ListTile | entry.value.$1.name | onChanged: (value) => _setQuantity(entry.key, value) |
| features/sales/presentation/direct_sale_page.dart | 228 | TextButton.icon | 'Agregar medio de pago' | onPressed: () => setState(() => _payments.add(_PaymentLine())) |
| features/sales/presentation/direct_sale_page.dart | 234 | FilledButton.icon | 'Confirmar venta y facturar' | onPressed: _busy \|\| _items.isEmpty ? null : _submit |
| features/sales/presentation/direct_sale_page.dart | 388 | TextField | 'Eliminar línea' | onPressed: widget.onDelete |
| features/sales/presentation/direct_sale_page.dart | 397 | IconButton | 'Eliminar línea' | onPressed: widget.onDelete |
