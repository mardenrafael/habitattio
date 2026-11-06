// Mensagens de commit × commitlint.config.mjs (contracts/commit-message.md, "Exemplos obrigatórios").
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import path from 'node:path';

const ROOT = path.resolve(import.meta.dirname, '..', '..');
const CONFIG = path.join(ROOT, 'commitlint.config.mjs');
const HOOK = path.join(ROOT, '.githooks', 'commit-msg');

function commitlintVersion() {
  const match = readFileSync(HOOK, 'utf8').match(/^COMMITLINT_VERSION=(.+)$/m);
  assert.ok(match, 'COMMITLINT_VERSION não encontrado em .githooks/commit-msg');
  return match[1].trim();
}

function lint(message) {
  const result = spawnSync(
    'npx',
    ['--yes', '-p', `@commitlint/cli@${commitlintVersion()}`, 'commitlint', '--config', CONFIG],
    { input: message, encoding: 'utf8', cwd: ROOT },
  );
  return { code: result.status, output: `${result.stdout}${result.stderr}` };
}

function assertAccepted(message) {
  const { code, output } = lint(message);

  assert.equal(code, 0, output);
}

function assertRejected(message, rule) {
  const { code, output } = lint(message);

  assert.notEqual(code, 0, `deveria recusar: ${JSON.stringify(message)}`);
  assert.match(output, new RegExp(`\\[${rule}\\]`));
}

test('V1 deve aceitar commit com tipo, escopo e descrição', () => {
  const message = 'feat(fatura): add late fee calculation';

  assertAccepted(message);
});

test('V2 deve aceitar breaking change marcada com exclamação', () => {
  const message = 'refactor(api)!: rename endpoints';

  assertAccepted(message);
});

test('V3 deve aceitar breaking change marcada no rodapé', () => {
  const message = 'fix(contrato): clamp due day\n\nBREAKING CHANGE: due day semantics changed';

  assertAccepted(message);
});

test('V4 deve aceitar a mensagem padrão de merge do git', () => {
  const message = "Merge branch '002-contribution-conventions'";

  assertAccepted(message);
});

test('V5 deve aceitar a mensagem padrão de revert do git', () => {
  const message = 'Revert "feat(fatura): add late fee calculation"';

  assertAccepted(message);
});

test('V6 deve aceitar o commit de sincronização de versão', () => {
  const message = 'chore(release): set version to 0.4.3\n\nVersion-Sync: 0.4.3';

  assertAccepted(message);
});

test('I1 deve recusar commit sem escopo', () => {
  const message = 'feat: add late fee';

  assertRejected(message, 'scope-empty');
});

test('I2 deve recusar commit com escopo vazio', () => {
  const message = 'feat(): add late fee';

  assertRejected(message, 'scope-empty');
});

test('I3 deve recusar tipo fora da lista', () => {
  const message = 'feature(fatura): add late fee';

  assertRejected(message, 'type-enum');
});

test('I4 deve recusar tipo com maiúscula', () => {
  const message = 'Feat(fatura): add late fee';

  assertRejected(message, 'type-case');
});

test('I5 deve recusar commit sem descrição', () => {
  const message = 'feat(fatura):';

  assertRejected(message, 'subject-empty');
});

test('I6 deve recusar merge com mensagem própria fora do padrão', () => {
  const message = 'merge: integrate feature x';

  assertRejected(message, 'type-enum');
});

test('I7 deve recusar cabeçalho com mais de 100 caracteres', () => {
  const message = `feat(fatura): ${'a'.repeat(95)}`;

  assertRejected(message, 'header-max-length');
});

test('I8 deve recusar mensagem sem tipo', () => {
  const message = 'add late fee';

  assertRejected(message, 'type-empty');
});
