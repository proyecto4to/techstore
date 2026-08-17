#!/usr/bin/env node

import { createHash } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { basename } from 'node:path';
import { X509Certificate } from 'node:crypto';

const APK_SIGNING_MAGIC = Buffer.from('APK Sig Block 42', 'ascii');
const SIGNATURE_SCHEMES = new Map([
  [0x7109871a, 'v2'],
  [0xf05368c0, 'v3'],
  [0x1b93ad61, 'v3.1'],
]);

function uint64(buffer, offset) {
  const value = Number(buffer.readBigUInt64LE(offset));
  if (!Number.isSafeInteger(value)) throw new Error('Longitud APK fuera de rango');
  return value;
}

function lengthPrefixed(buffer, offset) {
  if (offset + 4 > buffer.length) throw new Error('Campo truncado');
  const length = buffer.readUInt32LE(offset);
  const start = offset + 4;
  const end = start + length;
  if (end > buffer.length) throw new Error('Campo de longitud inválida');
  return { data: buffer.subarray(start, end), end };
}

function findEocd(buffer) {
  const minimum = Math.max(0, buffer.length - 65_557);
  for (let offset = buffer.length - 22; offset >= minimum; offset -= 1) {
    if (buffer.readUInt32LE(offset) === 0x06054b50) return offset;
  }
  throw new Error('No se encontró el directorio ZIP');
}

function signingPairs(apk) {
  const eocd = findEocd(apk);
  const centralDirectory = apk.readUInt32LE(eocd + 16);
  const footer = centralDirectory - 24;
  if (footer < 0 || !apk.subarray(centralDirectory - 16, centralDirectory).equals(APK_SIGNING_MAGIC)) {
    throw new Error('No se encontró APK Sig Block 42');
  }
  const size = uint64(apk, footer);
  const start = centralDirectory - size - 8;
  if (start < 0 || uint64(apk, start) !== size) throw new Error('Bloque de firma inconsistente');

  const pairs = [];
  let offset = start + 8;
  while (offset < footer) {
    const length = uint64(apk, offset);
    const idOffset = offset + 8;
    const end = idOffset + length;
    if (length < 4 || end > footer) throw new Error('Par de firma inválido');
    pairs.push({ id: apk.readUInt32LE(idOffset), value: apk.subarray(idOffset + 4, end) });
    offset = end;
  }
  return pairs;
}

function firstCertificate(schemeValue) {
  const signers = lengthPrefixed(schemeValue, 0).data;
  const signer = lengthPrefixed(signers, 0).data;
  const signedData = lengthPrefixed(signer, 0).data;
  const digests = lengthPrefixed(signedData, 0);
  const certificates = lengthPrefixed(signedData, digests.end).data;
  return lengthPrefixed(certificates, 0).data;
}

const files = process.argv.slice(2);
if (!files.length) throw new Error('Indicá al menos un APK');

for (const file of files) {
  const apk = readFileSync(file);
  const pair = signingPairs(apk).find(({ id }) => SIGNATURE_SCHEMES.has(id));
  if (!pair) throw new Error(`${basename(file)} no contiene firma v2/v3`);
  const der = firstCertificate(pair.value);
  const certificate = new X509Certificate(der);
  console.log(JSON.stringify({
    file: basename(file),
    scheme: SIGNATURE_SCHEMES.get(pair.id),
    certificateSha256: createHash('sha256').update(der).digest('hex').toUpperCase(),
    subject: certificate.subject,
    issuer: certificate.issuer,
    validFrom: certificate.validFrom,
    validTo: certificate.validTo,
  }));
}
