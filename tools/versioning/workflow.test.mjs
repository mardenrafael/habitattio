// Estrutura de .github/workflows/version-sync.yml (contracts/version-sync.md, "Gatilho e ambiente").
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import path from 'node:path';

const WORKFLOW = path.resolve(import.meta.dirname, '..', '..', '.github', 'workflows', 'version-sync.yml');

function workflow() {
  return readFileSync(WORKFLOW, 'utf8');
}

test('deve disparar só em push na main', () => {
  const text = workflow();

  assert.match(text, /^on:\s*\n\s+push:\s*\n\s+branches:\s*\[\s*main\s*\]/m);
  assert.doesNotMatch(text, /pull_request/);
});

test('deve pedir permissão de escrita em contents', () => {
  const text = workflow();

  assert.match(text, /^permissions:\s*\n\s+contents:\s*write\s*$/m);
});

test('deve serializar execuções sem cancelar', () => {
  const text = workflow();

  assert.match(text, /group:\s*version-sync/);
  assert.match(text, /cancel-in-progress:\s*false/);
});

test('deve fazer checkout do histórico completo', () => {
  const text = workflow();

  assert.match(text, /fetch-depth:\s*0/);
});

test('deve testar antes de sincronizar', () => {
  const text = workflow();

  const testsAt = text.indexOf('node --test "tools/versioning/*.test.mjs"');
  const syncAt = text.indexOf('node tools/versioning/version-sync.mjs');

  assert.ok(testsAt >= 0, 'passo de testes ausente');
  assert.ok(syncAt > testsAt, 'a sincronização deve vir depois dos testes');
});

test('não deve criar tags nem releases', () => {
  const text = workflow();

  assert.doesNotMatch(text, /git tag|create-release|gh release/);
});
