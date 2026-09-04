/// Muestra el contenido de un registro como campos legibles.
///
/// Todo el módulo administrativo se resolvía con un `JsonEncoder` volcado en un
/// `SelectableText`: para saber el total de una compra había que leer llaves,
/// comillas y nombres de columna. Acá cada clave se traduce a su nombre en
/// castellano, los importes se muestran con separador de miles, las fechas en
/// formato local y los booleanos como Sí/No.
///
/// Es genérico a propósito: el listado administrativo es una sola pantalla que
/// sirve a más de veinte recursos, así que una vista distinta por recurso
/// significaría veinte pantallas que se desincronizan. Los recursos que sí
/// necesitan una pantalla propia la tienen aparte.
library;

import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

import '../../../core/format/money.dart';

/// Claves que son importes. Se muestran con separador de miles y símbolo.
const _clavesDeDinero = {
  'total',
  'subtotal',
  'monto',
  'montoTotal',
  'precio',
  'precioUnitario',
  'costo',
  'costoUnitario',
  'saldo',
  'importe',
  'tarifa',
  'efectivo',
  'vuelto',
  'descuento',
  'totalIva',
  'totalGravado',
  'totalExento',
  'gravado',
  'exento',
  'montoEsperado',
  'montoContado',
  'montoDeclarado',
  'totalVentas',
  'totalCompras',
  'saldoPendiente',
};

/// Claves cuyo valor es una fecha u hora.
bool _esFecha(String clave) {
  final baja = clave.toLowerCase();
  return baja.startsWith('fecha') ||
      baja.endsWith('fecha') ||
      baja.endsWith('en') && baja.length > 4 && baja.contains('actualizad') ||
      baja == 'creadoen' ||
      baja == 'ultimologin' ||
      baja == 'apertura' ||
      baja == 'cierre';
}

/// Claves internas: sirven para operar, no para leer.
///
/// No se ocultan del todo —a veces el administrador necesita el identificador
/// para reportar un problema—; van a la sección de información técnica.
bool _esTecnica(String clave) =>
    clave == 'id' ||
    clave.endsWith('Id') ||
    clave.endsWith('Ids') ||
    clave == 'version' ||
    clave == 'tenantId';

/// El nombre que ve la persona para cada clave del servidor.
///
/// Lo que no está en el diccionario se humaniza igual: `razonSocial` pasa a
/// «Razón social». Así una clave nueva del backend no aparece como código.
const _etiquetas = <String, String>{
  'razonSocial': 'Razón social',
  'nombreFantasia': 'Nombre de fantasía',
  'ruc': 'RUC',
  'email': 'Correo',
  'telefono': 'Teléfono',
  'direccion': 'Dirección',
  'ciudad': 'Ciudad',
  'pais': 'País',
  'nombre': 'Nombre',
  'apellido': 'Apellido',
  'rol': 'Rol',
  'activo': 'Activo',
  'estado': 'Estado',
  'descripcion': 'Descripción',
  'numero': 'Número',
  'fecha': 'Fecha',
  'fechaEmision': 'Fecha de emisión',
  'fechaVencimiento': 'Vencimiento',
  'total': 'Total',
  'subtotal': 'Subtotal',
  'monto': 'Monto',
  'precio': 'Precio',
  'costo': 'Costo',
  'iva': 'IVA',
  'stockReal': 'Stock real',
  'stockDisponible': 'Stock disponible',
  'stockMinimo': 'Stock mínimo',
  'stockReservado': 'Stock reservado',
  'marca': 'Marca',
  'modelo': 'Modelo',
  'categoria': 'Categoría',
  'codigo': 'Código',
  'imagenUrl': 'Imagen',
  'ultimoLogin': 'Último acceso',
  'puntoExpedicion': 'Punto de expedición',
  'transportista': 'Transportista',
  'codigoSeguimiento': 'Código de seguimiento',
  'observaciones': 'Observaciones',
  'usuarioEmail': 'Usuario',
  'condicion': 'Condición',
  'timbrado': 'Timbrado',
  'prioridad': 'Prioridad',
  'diasEstimadosMin': 'Días mínimos',
  'diasEstimadosMax': 'Días máximos',
  'pesoKg': 'Peso (kg)',
  'ocultarSinStock': 'Ocultar sin stock',
  'moneda': 'Moneda',
  'accion': 'Acción',
  'modulo': 'Módulo',
  'resultado': 'Resultado',
  'host': 'Servidor',
  'port': 'Puerto',
  'remitente': 'Remitente',
  'latitud': 'Latitud',
  'longitud': 'Longitud',
};

/// Convierte `costoActualizadoEn` en «Costo actualizado en».
String etiquetaDeCampo(String clave) {
  final conocida = _etiquetas[clave];
  if (conocida != null) return conocida;
  final separada = clave
      .replaceAllMapped(RegExp('([a-z0-9])([A-Z])'), (m) => '${m[1]} ${m[2]}')
      .replaceAll('_', ' ')
      .trim()
      .toLowerCase();
  if (separada.isEmpty) return clave;
  return separada[0].toUpperCase() + separada.substring(1);
}

/// Un código del servidor dicho en palabras: `LISTO_PARA_ENVIO` -> «Listo para
/// envío» hasta donde se puede, sin inventar acentos que no correspondan.
String humanizarCodigo(String crudo) {
  final limpio = crudo.replaceAll('_', ' ').trim();
  if (limpio.isEmpty) return crudo;
  final baja = limpio.toLowerCase();
  return baja[0].toUpperCase() + baja.substring(1);
}

/// El valor de un campo, ya formateado para leerse.
String formatearValor(String clave, Object? valor, {String moneda = 'PYG'}) {
  if (valor == null) return '—';
  if (valor is bool) return valor ? 'Sí' : 'No';
  if (valor is num && _clavesDeDinero.contains(clave)) {
    return formatMoney(valor.toDouble(), moneda);
  }
  if (valor is num) {
    return valor is int || valor == valor.roundToDouble()
        ? NumberFormat.decimalPattern('es_PY').format(valor)
        : valor.toString();
  }
  final texto = valor.toString().trim();
  if (texto.isEmpty) return '—';
  if (_esFecha(clave)) {
    final fecha = DateTime.tryParse(texto);
    if (fecha != null) {
      return DateFormat.yMMMd('es_PY').add_Hm().format(fecha.toLocal());
    }
  }
  if (_esCodigoDeEnum(clave, texto)) return humanizarCodigo(texto);
  return texto;
}

/// Campos cuyo contenido es un codigo del backend y no texto que cargo alguien.
///
/// Hace falta la lista porque por forma no se distinguen: `PENDIENTE` es un
/// enum y `ACME` es una razon social, y las dos son una palabra en mayusculas.
/// Sin esto, una razon social se mostraba como «Acme».
const _clavesDeCodigo = {
  'estado',
  'estadoPedido',
  'estadoEnvio',
  'estadoReparto',
  'estadoVehiculo',
  'accion',
  'resultado',
  'condicion',
  'canal',
  'tipo',
  'tipoEvento',
  'metodoPago',
  'metodoEntrega',
  'modulo',
};

bool _esCodigoDeEnum(String clave, String texto) {
  if (!RegExp(r'^[A-Z][A-Z0-9_]*$').hasMatch(texto)) return false;
  // Con guion bajo no hay duda: nadie escribe LISTO_PARA_ENVIO a mano.
  if (texto.contains('_')) return true;
  return _clavesDeCodigo.contains(clave);
}

/// Las filas legibles de un registro, separadas de las técnicas.
({
  List<MapEntry<String, Object?>> visibles,
  List<MapEntry<String, Object?>> tecnicas,
})
separarCampos(Map<String, Object?> registro) {
  final visibles = <MapEntry<String, Object?>>[];
  final tecnicas = <MapEntry<String, Object?>>[];
  for (final entrada in registro.entries) {
    if (_esTecnica(entrada.key)) {
      tecnicas.add(entrada);
    } else {
      visibles.add(entrada);
    }
  }
  return (visibles: visibles, tecnicas: tecnicas);
}

/// Lista de campos de un registro, con las claves internas al final.
class DetailFields extends StatelessWidget {
  const DetailFields({required this.registro, super.key, this.moneda});

  final Map<String, Object?> registro;

  /// La moneda con la que formatear los importes. Si no se pasa, se toma la del
  /// propio registro para no asumir guaraníes en una compra en dólares.
  final String? moneda;

  @override
  Widget build(BuildContext context) {
    final divisa = moneda ?? registro['moneda']?.toString().trim() ?? 'PYG';
    final campos = separarCampos(registro);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        for (final entrada in campos.visibles)
          _Fila(clave: entrada.key, valor: entrada.value, moneda: divisa),
        if (campos.tecnicas.isNotEmpty) ...[
          const SizedBox(height: 4),
          _InformacionTecnica(campos: campos.tecnicas, moneda: divisa),
        ],
      ],
    );
  }
}

class _Fila extends StatelessWidget {
  const _Fila({required this.clave, required this.valor, required this.moneda});

  final String clave;
  final Object? valor;
  final String moneda;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    // Un objeto anidado se muestra como una sección con sus propios campos:
    // volcarlo como texto sería el mismo JSON de antes con otra ropa.
    if (valor is Map) {
      final anidado = Map<String, Object?>.from(valor! as Map);
      if (anidado.isEmpty) return const SizedBox.shrink();
      return Padding(
        padding: const EdgeInsets.only(top: 8, bottom: 4),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              etiquetaDeCampo(clave),
              style: theme.textTheme.labelLarge?.copyWith(
                color: theme.colorScheme.primary,
              ),
            ),
            Padding(
              padding: const EdgeInsets.only(left: 12, top: 2),
              child: DetailFields(registro: anidado, moneda: moneda),
            ),
          ],
        ),
      );
    }
    if (valor is List) {
      final lista = valor! as List;
      if (lista.isEmpty) {
        return _texto(context, etiquetaDeCampo(clave), 'Ninguno');
      }
      // Una lista de objetos se numera; una de valores simples se une.
      if (lista.first is Map) {
        return Padding(
          padding: const EdgeInsets.only(top: 8, bottom: 4),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                '${etiquetaDeCampo(clave)} (${lista.length})',
                style: theme.textTheme.labelLarge?.copyWith(
                  color: theme.colorScheme.primary,
                ),
              ),
              for (var i = 0; i < lista.length; i++)
                Padding(
                  padding: const EdgeInsets.only(left: 12, top: 6),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        '${i + 1}.',
                        style: theme.textTheme.labelSmall?.copyWith(
                          color: theme.colorScheme.onSurfaceVariant,
                        ),
                      ),
                      DetailFields(
                        registro: Map<String, Object?>.from(lista[i] as Map),
                        moneda: moneda,
                      ),
                    ],
                  ),
                ),
            ],
          ),
        );
      }
      return _texto(
        context,
        etiquetaDeCampo(clave),
        lista.map((e) => formatearValor(clave, e, moneda: moneda)).join(', '),
      );
    }
    return _texto(
      context,
      etiquetaDeCampo(clave),
      formatearValor(clave, valor, moneda: moneda),
    );
  }

  Widget _texto(BuildContext context, String etiqueta, String valor) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 132,
            child: Text(
              etiqueta,
              style: theme.textTheme.bodySmall?.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
              ),
            ),
          ),
          const SizedBox(width: 8),
          Expanded(
            child: SelectableText(valor, style: theme.textTheme.bodyMedium),
          ),
        ],
      ),
    );
  }
}

class _InformacionTecnica extends StatelessWidget {
  const _InformacionTecnica({required this.campos, required this.moneda});

  final List<MapEntry<String, Object?>> campos;
  final String moneda;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Theme(
      // Sin divisores: dentro de una tarjeta el marco doble ensucia.
      data: theme.copyWith(dividerColor: Colors.transparent),
      child: ExpansionTile(
        tilePadding: EdgeInsets.zero,
        childrenPadding: const EdgeInsets.only(left: 4, bottom: 8),
        title: Text(
          'Información técnica',
          style: theme.textTheme.labelMedium?.copyWith(
            color: theme.colorScheme.onSurfaceVariant,
          ),
        ),
        children: [
          for (final entrada in campos)
            _Fila(clave: entrada.key, valor: entrada.value, moneda: moneda),
        ],
      ),
    );
  }
}
