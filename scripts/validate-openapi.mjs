import { readFileSync } from 'node:fs';

const contractPath = new URL('../docs/api/techstore-v1.openapi.json', import.meta.url);
const document = JSON.parse(readFileSync(contractPath, 'utf8'));
const methods = new Set(['get', 'post', 'put', 'patch', 'delete', 'options', 'head']);
const operations = [];

for (const [path, pathItem] of Object.entries(document.paths ?? {})) {
  for (const [method, operation] of Object.entries(pathItem)) {
    if (methods.has(method)) operations.push({ path, method, operation });
  }
}

const failures = [];
const requireValue = (condition, message) => {
  if (!condition) failures.push(message);
};

requireValue(/^3\./.test(document.openapi ?? ''), 'El contrato debe usar OpenAPI 3.x');
requireValue(document.servers?.[0]?.url === '/', 'El servidor canónico debe ser relativo (/)');
requireValue(Object.keys(document.paths ?? {}).length >= 100, 'El contrato perdió rutas conocidas');
requireValue(operations.length >= 122, 'El contrato perdió operaciones conocidas');
requireValue(document.components?.securitySchemes?.bearerAuth?.scheme === 'bearer', 'Falta bearerAuth');
requireValue(Boolean(document.paths?.['/api/v1/sync/events']?.get), 'Falta recuperación de sincronización');

const operationIds = operations.map(({ operation }) => operation.operationId).filter(Boolean);
requireValue(operationIds.length === operations.length, 'Todas las operaciones deben tener operationId');
requireValue(new Set(operationIds).size === operationIds.length, 'Los operationId deben ser únicos');

const publicOperations = new Set([
  'POST /api/v1/auth/registro', 'POST /api/v1/auth/login',
  'POST /api/v1/auth/refresh', 'POST /api/v1/auth/logout',
  'POST /api/v1/auth/password/forgot', 'POST /api/v1/auth/password/reset',
  'GET /api/v1/productos', 'GET /api/v1/productos/{id}',
  'GET /api/v1/marcas', 'POST /api/v1/carrito/validar',
]);
for (const { path, method, operation } of operations) {
  if (publicOperations.has(`${method.toUpperCase()} ${path}`)) {
    requireValue(Array.isArray(operation.security) && operation.security.length === 0,
      `${method.toUpperCase()} ${path} debe declarar security: []`);
  }
}

if (failures.length) {
  console.error(failures.map((failure) => `- ${failure}`).join('\n'));
  process.exit(1);
}

console.log(`OpenAPI válido: ${Object.keys(document.paths).length} rutas, ${operations.length} operaciones.`);
