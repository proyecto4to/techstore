#!/usr/bin/env node

import { readFileSync } from 'node:fs';

const forbiddenLicense = /(?:\bAGPL\b|\bGPL-3\.0(?:-only|-or-later)?\b|\bSSPL\b|\bBUSL\b|Commons Clause|Elastic License)/i;
const files = process.argv.slice(2);

if (!files.length) {
  console.error('Uso: node scripts/review-sbom-licenses.mjs <sbom.cdx.json> [...]');
  process.exit(1);
}

let componentCount = 0;
let unresolvedCount = 0;
const rejected = [];

function licenseLabels(component) {
  return (component.licenses ?? []).flatMap((entry) => {
    if (typeof entry.expression === 'string') return [entry.expression];
    if (typeof entry.license?.id === 'string') return [entry.license.id];
    if (typeof entry.license?.name === 'string') return [entry.license.name];
    return [];
  });
}

for (const file of files) {
  const sbom = JSON.parse(readFileSync(file, 'utf8'));
  for (const component of sbom.components ?? []) {
    componentCount += 1;
    const licenses = licenseLabels(component);
    if (!licenses.length) unresolvedCount += 1;
    for (const license of licenses) {
      if (forbiddenLicense.test(license)) {
        rejected.push(`${component.name ?? 'componente-sin-nombre'}@${component.version ?? '?'} (${license})`);
      }
    }
  }
}

if (rejected.length) {
  for (const dependency of rejected) console.error(`LICENSE_REJECTED ${dependency}`);
  process.exit(1);
}

console.log(`LICENSE_REVIEW_OK files=${files.length} components=${componentCount} unresolved=${unresolvedCount}`);
if (unresolvedCount) {
  console.warn('LICENSE_WARNING Hay componentes sin licencia normalizada; revisar el SBOM antes de distribuir.');
}
