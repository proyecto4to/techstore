#!/usr/bin/env node

import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs';
import { dirname, extname, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const repositoryRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const ownPath = 'scripts/ci-security-check.mjs';
const knownLegacyFiles = new Set([
  'techstore-backend/src/main/resources/sifen/techstore-fe-test.p12',
]);
const allowedEnvironmentExamples = new Set([
  '.env.example',
  '.env.commercial.example',
  'techstore-mobile/.env.example',
]);
const forbiddenExtensions = new Set([
  '.jks',
  '.key',
  '.keystore',
  '.mobileprovision',
  '.p12',
  '.p8',
  '.pem',
  '.pfx',
]);
const forbiddenNames = new Set([
  'credentials.json',
  'GoogleService-Info.plist',
  'google-services.json',
]);
const allowedArchivePublicCertificates = new Set([
  'assets/expo-root.pem',
  'base/assets/expo-root.pem',
]);
const textExtensions = new Set([
  '.cjs',
  '.conf',
  '.env',
  '.example',
  '.java',
  '.js',
  '.json',
  '.jsx',
  '.mjs',
  '.properties',
  '.ps1',
  '.sh',
  '.ts',
  '.tsx',
  '.xml',
  '.yaml',
  '.yml',
]);
const secretPatterns = [
  ['clave privada', /-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----/g],
  ['access key AWS', /\bAKIA[0-9A-Z]{16}\b/g],
  ['token GitHub', /\bgh[pousr]_[A-Za-z0-9]{36,}\b/g],
  ['token npm', /\bnpm_[A-Za-z0-9]{36}\b/g],
  ['token Slack', /\bxox[baprs]-[A-Za-z0-9-]{10,}\b/g],
  ['API key Google', /\bAIza[0-9A-Za-z_-]{35}\b/g],
  ['service account Google', /"type"\s*:\s*"service_account"/g],
];
const mobileSecretAssignment = /\b(?:DB_PASSWORD|JWT_SECRET|KEYSTORE_PASSWORD|POSTGRES_PASSWORD|PRIVATE_KEY|SIFEN_PASSWORD|SMTP_PASSWORD)\b\s*[:=]\s*["']?(?!\$\{|<|$)[^\s,"']+/g;
const reservedPortPattern = /(?<!\d)(?:8080|8181)(?!\d)/g;
const portPolicyFiles = new Set([
  ownPath,
  'techstore-mobile/app.config.ts',
  'techstore-mobile/scripts/validate-build-config.cjs',
  'techstore-mobile/tests/appConfig.test.ts',
]);

const failures = [];
const warnings = [];

function normalizePath(value) {
  return value.replaceAll('\\', '/').replace(/^\.\//, '');
}

function lineNumber(source, offset) {
  return source.slice(0, offset).split('\n').length;
}

function recordMatches(relativePath, source, patterns) {
  for (const [label, pattern] of patterns) {
    pattern.lastIndex = 0;
    const match = pattern.exec(source);
    if (match) failures.push(`${relativePath}:${lineNumber(source, match.index)} contiene ${label}`);
  }
}

function filesRecursively(target) {
  const absoluteTarget = resolve(target);
  if (!existsSync(absoluteTarget)) {
    failures.push(`No existe el artefacto solicitado: ${target}`);
    return [];
  }
  if (statSync(absoluteTarget).isFile()) return [absoluteTarget];
  return readdirSync(absoluteTarget, { withFileTypes: true }).flatMap((entry) =>
    filesRecursively(resolve(absoluteTarget, entry.name)),
  );
}

function scanArtifact(targets) {
  const files = targets.flatMap(filesRecursively);
  for (const file of files) {
    const displayPath = normalizePath(relative(repositoryRoot, file));
    const extension = extname(file).toLowerCase();
    if (forbiddenExtensions.has(extension) || forbiddenNames.has(file.split(/[\\/]/).at(-1))) {
      failures.push(`${displayPath} tiene un formato sensible prohibido`);
      continue;
    }
    if (statSync(file).size > 25 * 1024 * 1024) {
      warnings.push(`${displayPath} supera 25 MiB y no se inspeccionó por contenido`);
      continue;
    }
    const source = readFileSync(file).toString('latin1');
    recordMatches(displayPath, source, [
      ...secretPatterns,
      ['nombre de secreto de servidor', mobileSecretAssignment],
    ]);
  }
  console.log(`ARTIFACT_SECURITY_OK files=${files.length} targets=${targets.length}`);
}

function scanArchives(targets) {
  let entryCount = 0;
  for (const target of targets) {
    const absoluteTarget = resolve(target);
    if (!existsSync(absoluteTarget)) {
      failures.push(`No existe el archivo solicitado: ${target}`);
      continue;
    }
    let entries;
    try {
      entries = execFileSync('jar', ['tf', absoluteTarget], { encoding: 'utf8' })
        .split(/\r?\n/)
        .filter(Boolean)
        .map(normalizePath);
    } catch (error) {
      failures.push(`No se pudo listar ${target} con jar: ${error.message}`);
      continue;
    }
    entryCount += entries.length;
    for (const entry of entries) {
      const extension = extname(entry).toLowerCase();
      const baseName = entry.split('/').at(-1);
      if (!forbiddenExtensions.has(extension) && !forbiddenNames.has(baseName)) continue;
      if (entry === 'BOOT-INF/classes/sifen/techstore-fe-test.p12') {
        warnings.push(`${target}:${entry} es la excepción SIFEN heredada`);
      } else if (allowedArchivePublicCertificates.has(entry)) {
        warnings.push(`${target}:${entry} es un certificado público raíz de Expo`);
      } else {
        failures.push(`${target}:${entry} tiene un formato sensible prohibido`);
      }
    }
    const source = readFileSync(absoluteTarget).toString('latin1');
    recordMatches(normalizePath(relative(repositoryRoot, absoluteTarget)), source, [
      ...secretPatterns,
      ['nombre de secreto de servidor', mobileSecretAssignment],
    ]);
  }
  console.log(`ARCHIVE_SECURITY_OK archives=${targets.length} entries=${entryCount}`);
}

function repositoryFiles() {
  const result = execFileSync(
    'git',
    ['-C', repositoryRoot, 'ls-files', '--cached', '--others', '--exclude-standard', '-z'],
    { encoding: 'utf8' },
  );
  return result.split('\0').filter(Boolean).map(normalizePath);
}

function isTextCandidate(relativePath) {
  if (relativePath === ownPath || relativePath.endsWith('package-lock.json')) return false;
  if (relativePath.startsWith('docs/') || relativePath.endsWith('.md')) return false;
  const extension = extname(relativePath).toLowerCase();
  return textExtensions.has(extension) || relativePath.split('/').at(-1)?.startsWith('.env');
}

function scanWorkflowPins(relativePath, source) {
  for (const match of source.matchAll(/^\s*-?\s*uses:\s*([^\s#]+)(?:\s+#.*)?$/gm)) {
    const reference = match[1];
    if (reference.startsWith('./') || /@[0-9a-f]{40}$/.test(reference)) continue;
    failures.push(`${relativePath}:${lineNumber(source, match.index)} no fija '${reference}' a un SHA completo`);
  }
  if (/\$\{\{\s*secrets\./.test(source)) {
    failures.push(`${relativePath} inyecta secretos; los jobs de PR de este workflow deben ser secretless`);
  }
}

function scanRepository() {
  const files = repositoryFiles();
  for (const relativePath of files) {
    const baseName = relativePath.split('/').at(-1);
    const extension = extname(relativePath).toLowerCase();
    const isSensitiveName = forbiddenNames.has(baseName)
      || /^service-account.*\.json$/i.test(baseName)
      || forbiddenExtensions.has(extension)
      || (baseName.startsWith('.env') && !allowedEnvironmentExamples.has(relativePath));

    if (isSensitiveName) {
      if (knownLegacyFiles.has(relativePath)) {
        warnings.push(`${relativePath} es la excepción SIFEN heredada: debe rotarse y externalizarse`);
      } else {
        failures.push(`${relativePath} tiene un nombre o formato sensible prohibido`);
      }
    }

    if (!isTextCandidate(relativePath)) continue;
    const absolutePath = resolve(repositoryRoot, relativePath);
    if (!existsSync(absolutePath) || statSync(absolutePath).size > 2 * 1024 * 1024) continue;
    const source = readFileSync(absolutePath, 'utf8');
    recordMatches(relativePath, source, secretPatterns);

    if (relativePath.startsWith('techstore-mobile/')) {
      recordMatches(relativePath, source, [['asignación de secreto de servidor', mobileSecretAssignment]]);
    }

    if (!relativePath.startsWith('.github/') && !portPolicyFiles.has(relativePath) && reservedPortPattern.test(source)) {
      reservedPortPattern.lastIndex = 0;
      const match = reservedPortPattern.exec(source);
      failures.push(`${relativePath}:${lineNumber(source, match.index)} usa un puerto reservado (8080/8181)`);
    }
    reservedPortPattern.lastIndex = 0;

    if (relativePath.startsWith('.github/workflows/')) scanWorkflowPins(relativePath, source);
  }
  console.log(`REPOSITORY_SECURITY_OK files=${files.length}`);
}

const archiveIndex = process.argv.indexOf('--archive');
const artifactIndex = process.argv.indexOf('--artifact');
if (archiveIndex >= 0) {
  const targets = process.argv.slice(archiveIndex + 1);
  if (!targets.length) failures.push('Falta indicar al menos un archivo después de --archive');
  else scanArchives(targets);
} else if (artifactIndex >= 0) {
  const targets = process.argv.slice(artifactIndex + 1);
  if (!targets.length) failures.push('Falta indicar al menos un artefacto después de --artifact');
  else scanArtifact(targets);
} else {
  scanRepository();
}

for (const warning of warnings) console.warn(`SECURITY_WARNING ${warning}`);
if (failures.length) {
  for (const failure of failures) console.error(`SECURITY_ERROR ${failure}`);
  process.exitCode = 1;
} else {
  console.log(`SECURITY_OK warnings=${warnings.length}`);
}
