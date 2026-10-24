import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, writeFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { execFileSync, spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

test('Firebase exemption only accepts the configured public key in Android archives', () => {
  const dir = mkdtempSync(join(tmpdir(), 'techstore-security-'));
  const key = 'AIza' + 'a'.repeat(35);
  const otherKey = 'AIza' + 'b'.repeat(35);
  const config = join(dir, 'firebase.json');
  writeFileSync(config, JSON.stringify({ apiKey: key, appId: '1:123:android:abc', messagingSenderId: '123', projectId: 'test-project' }));
  const scanner = new URL('./ci-security-check.mjs', import.meta.url);
  const run = (payload, extension = 'apk', allow = true) => {
    writeFileSync(join(dir, 'payload.txt'), `\n${payload}\n`);
    const archive = join(dir, `fixture.${extension}`);
    execFileSync('jar', ['cf0', archive, '-C', dir, 'payload.txt']);
    return spawnSync(process.execPath, [fileURLToPath(scanner), ...(allow ? ['--firebase-config', config] : []), '--archive', archive], { encoding: 'utf8' });
  };
  try {
    assert.equal(run(key).status, 0);
    assert.equal(run(key, 'apk', false).status, 1);
    assert.equal(run(`${key}\n${otherKey}`).status, 1);
    assert.equal(run(`${key}\n${'-----BEGIN ' + 'PRIVATE KEY-----'}`).status, 1);
    assert.equal(run(`${key}\n${JSON.stringify({ type: 'service_account' })}`).status, 1);
    assert.equal(run(key, 'jar').status, 1);
    writeFileSync(config, '{}');
    assert.equal(run(key).status, 1);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});
