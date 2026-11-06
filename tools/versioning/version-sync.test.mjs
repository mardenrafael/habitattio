// CLI version-sync.mjs em repositórios temporários (contracts/version-sync.md S1–S14, S17, S18).
import { test, after } from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { chmodSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import os from 'node:os';
import path from 'node:path';

const SCRIPT = path.join(import.meta.dirname, 'version-sync.mjs');
const ENV = {
  ...process.env,
  GIT_AUTHOR_NAME: 'Test',
  GIT_AUTHOR_EMAIL: 'test@example.com',
  GIT_COMMITTER_NAME: 'Test',
  GIT_COMMITTER_EMAIL: 'test@example.com',
  GIT_CONFIG_GLOBAL: '/dev/null',
  GIT_CONFIG_NOSYSTEM: '1',
};
const tempDirs = [];

after(() => tempDirs.forEach((dir) => rmSync(dir, { recursive: true, force: true })));

function pom(version) {
  return `<?xml version="1.0" encoding="UTF-8"?>
<project>
\t<parent>
\t\t<groupId>org.springframework.boot</groupId>
\t\t<artifactId>spring-boot-starter-parent</artifactId>
\t\t<version>4.1.2</version>
\t</parent>
\t<groupId>br.com.marden</groupId>
\t<artifactId>habitattio</artifactId>
\t<version>${version}</version>
</project>
`;
}

function syncMessage(version) {
  return `chore(release): set version to ${version}\n\nVersion-Sync: ${version}`;
}

function git(cwd, args) {
  const result = spawnSync('git', args, { cwd, encoding: 'utf8', env: ENV });
  assert.equal(result.status, 0, `git ${args.join(' ')}: ${result.stderr}`);
  return result.stdout.trim();
}

function tempDir() {
  const dir = mkdtempSync(path.join(os.tmpdir(), 'habitattio-'));
  tempDirs.push(dir);
  return dir;
}

// Repositório com pom.xml na versão dada; com `marker`, o primeiro commit já é o de sincronização.
function newRepo(version, { marker = true } = {}) {
  const repo = tempDir();
  git(repo, ['init', '-q', '-b', 'main']);
  writeFileSync(path.join(repo, 'pom.xml'), pom(version));
  git(repo, ['add', 'pom.xml']);
  git(repo, ['commit', '-q', '-m', marker ? syncMessage(version) : 'chore(build): initial pom']);
  return repo;
}

function commit(repo, message) {
  git(repo, ['commit', '-q', '--allow-empty', '-m', message]);
}

function commitPom(repo, version, message) {
  writeFileSync(path.join(repo, 'pom.xml'), pom(version));
  git(repo, ['commit', '-q', '-am', message]);
}

function mergeBranch(repo, branch, messages) {
  git(repo, ['checkout', '-q', '-b', branch]);
  messages.forEach((message) => commit(repo, message));
  git(repo, ['checkout', '-q', 'main']);
  git(repo, ['merge', '-q', '--no-ff', '--no-edit', branch]);
}

function sync(repo, ...flags) {
  const result = spawnSync('node', [SCRIPT, '--repo', repo, ...flags], { encoding: 'utf8', env: ENV });
  return { code: result.status, line: result.stdout.trim().split('\n')[0], stderr: result.stderr };
}

function projectVersion(repo) {
  return readFileSync(path.join(repo, 'pom.xml'), 'utf8').match(/<artifactId>habitattio<\/artifactId>\s*<version>([^<]*)</)[1];
}

function lastMessage(repo) {
  return git(repo, ['log', '-1', '--format=%B']);
}

function lastChangedFiles(repo) {
  return git(repo, ['diff-tree', '--no-commit-id', '--name-only', '-r', 'HEAD']);
}

test('S1 deve partir de 0.0.1 na primeira sincronização', () => {
  const repo = newRepo('0.0.1-SNAPSHOT', { marker: false });
  commit(repo, 'feat(x): y');

  const result = sync(repo, '--dry-run');

  assert.equal(result.line, 'outcome=write from=0.0.1 to=0.1.0 commits=1');
  assert.equal(result.code, 0);
  assert.equal(projectVersion(repo), '0.0.1-SNAPSHOT');
});

test('S2 deve incrementar PATCH quando só há fix', () => {
  const repo = newRepo('0.4.2');
  commit(repo, 'fix(x): a');
  commit(repo, 'fix(y): b');

  const result = sync(repo, '--dry-run');

  assert.equal(result.line, 'outcome=write from=0.4.2 to=0.4.3 commits=2');
});

test('S3 deve incrementar MINOR quando há feat', () => {
  const repo = newRepo('0.4.2');
  commit(repo, 'fix(x): a');
  commit(repo, 'feat(y): b');

  const result = sync(repo, '--dry-run');

  assert.equal(result.line, 'outcome=write from=0.4.2 to=0.5.0 commits=2');
});

test('S4 deve incrementar MINOR em breaking change na série 0.x', () => {
  const repo = newRepo('0.4.2');
  commit(repo, 'feat(x): a');
  commit(repo, 'refactor(y)!: b');

  const result = sync(repo, '--dry-run');

  assert.equal(result.line, 'outcome=write from=0.4.2 to=0.5.0 commits=2');
});

test('S5 deve incrementar PATCH quando só há tipos que não são feat nem fix', () => {
  const repo = newRepo('0.4.2');
  commit(repo, 'docs(x): a');
  commit(repo, 'test(y): b');

  const result = sync(repo, '--dry-run');

  assert.equal(result.line, 'outcome=write from=0.4.2 to=0.4.3 commits=2');
});

test('S6 deve incrementar MAJOR em breaking change a partir de 1.0.0', () => {
  const repo = newRepo('1.2.3');
  commit(repo, 'fix(x): a\n\nBREAKING CHANGE: b');

  const result = sync(repo, '--dry-run');

  assert.equal(result.line, 'outcome=write from=1.2.3 to=2.0.0 commits=1');
});

test('S7 deve incrementar MINOR em feat a partir de 1.0.0', () => {
  const repo = newRepo('1.2.3');
  commit(repo, 'feat(x): a');

  const result = sync(repo, '--dry-run');

  assert.equal(result.line, 'outcome=write from=1.2.3 to=1.3.0 commits=1');
});

test('S8 deve não fazer nada quando só há merge, revert e mensagem fora do padrão', () => {
  const repo = newRepo('0.4.2');
  mergeBranch(repo, 'outside', ['add late fee']);
  commit(repo, 'Revert "feat(x): y"');

  const result = sync(repo, '--no-push');

  assert.equal(result.line, 'outcome=noop reason=nothing-to-bump version=0.4.2');
  assert.equal(result.code, 0);
  assert.equal(lastMessage(repo), 'Revert "feat(x): y"');
});

test('S9 deve não fazer nada quando HEAD já é o commit de sincronização', () => {
  const repo = newRepo('0.4.2');

  const result = sync(repo, '--no-push');

  assert.equal(result.line, 'outcome=noop reason=already-synced version=0.4.2');
  assert.equal(result.code, 0);
});

test('S10 deve marcar a promoção manual para 1.0.0 com commit vazio', () => {
  const repo = newRepo('0.9.3');
  commitPom(repo, '1.0.0', 'chore(release): promote to 1.0.0');
  commit(repo, 'feat(x): y');

  const result = sync(repo, '--no-push');

  assert.equal(result.line, 'outcome=mark version=1.0.0');
  assert.equal(result.code, 0);
  assert.equal(lastMessage(repo), syncMessage('1.0.0'));
  assert.equal(lastChangedFiles(repo), '');
  assert.equal(projectVersion(repo), '1.0.0');
});

test('S11 deve falhar em alteração manual de versão que não é a promoção', () => {
  const repo = newRepo('0.4.2');
  commitPom(repo, '0.7.0', 'chore(build): bump by hand');

  const result = sync(repo, '--no-push');

  assert.equal(result.line, 'outcome=fail reason=manual-version-change');
  assert.equal(result.code, 1);
  assert.equal(lastMessage(repo), 'chore(build): bump by hand');
});

test('S12 deve falhar quando a versão do pom é inválida', () => {
  const repo = newRepo('0.4.2');
  commitPom(repo, 'banana', 'chore(build): break version');

  const result = sync(repo, '--no-push');

  assert.equal(result.line, 'outcome=fail reason=invalid-version');
  assert.equal(result.code, 1);
});

test('S13 deve contar uma vez cada commit de dois merges seguidos', () => {
  const repo = newRepo('0.4.2');
  mergeBranch(repo, 'a', ['fix(x): a']);
  mergeBranch(repo, 'b', ['feat(y): b']);

  const result = sync(repo, '--dry-run');

  assert.equal(result.line, 'outcome=write from=0.4.2 to=0.5.0 commits=2');
});

test('S14 deve gravar a versão e ficar idempotente na execução seguinte', () => {
  const repo = newRepo('0.4.2');
  commit(repo, 'fix(x): a');
  commit(repo, 'fix(y): b');

  const first = sync(repo, '--no-push');
  const second = sync(repo, '--no-push');

  assert.equal(first.line, 'outcome=write from=0.4.2 to=0.4.3 commits=2');
  assert.equal(projectVersion(repo), '0.4.3');
  assert.equal(lastMessage(repo), syncMessage('0.4.3'));
  assert.equal(lastChangedFiles(repo), 'pom.xml');
  assert.equal(second.line, 'outcome=noop reason=already-synced version=0.4.3');
});

test('S17 deve recalcular e empurrar de novo quando o remote avançou', () => {
  const base = tempDir();
  const remote = path.join(base, 'remote.git');
  git(base, ['init', '-q', '--bare', '-b', 'main', remote]);
  const repo = newRepo('0.4.2');
  commit(repo, 'fix(x): a');
  git(repo, ['remote', 'add', 'origin', remote]);
  git(repo, ['push', '-q', 'origin', 'main']);
  const other = path.join(base, 'other');
  git(base, ['clone', '-q', remote, other]);
  commit(other, 'feat(y): b');
  git(other, ['push', '-q', 'origin', 'main']);

  const result = sync(repo);

  assert.equal(result.line, 'outcome=write from=0.4.2 to=0.5.0 commits=2');
  assert.equal(result.code, 0);
  assert.equal(git(base, ['--git-dir', remote, 'log', '-1', '--format=%B', 'main']), syncMessage('0.5.0'));
  assert.match(git(base, ['--git-dir', remote, 'show', 'main:pom.xml']), /<version>0\.5\.0<\/version>/);
});

test('S18 deve falhar de forma visível quando o remote recusa o push', () => {
  const base = tempDir();
  const remote = path.join(base, 'remote.git');
  git(base, ['init', '-q', '--bare', '-b', 'main', remote]);
  const repo = newRepo('0.4.2');
  git(repo, ['remote', 'add', 'origin', remote]);
  git(repo, ['push', '-q', 'origin', 'main']);
  writeFileSync(path.join(remote, 'hooks', 'pre-receive'), '#!/bin/sh\nexit 1\n');
  chmodSync(path.join(remote, 'hooks', 'pre-receive'), 0o755);
  commit(repo, 'fix(x): a');

  const result = sync(repo);

  assert.equal(result.line, 'outcome=fail reason=push-rejected');
  assert.equal(result.code, 1);
  assert.equal(git(base, ['--git-dir', remote, 'log', '-1', '--format=%B', 'main']), syncMessage('0.4.2'));
});
