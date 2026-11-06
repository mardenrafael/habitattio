// Hook .githooks/commit-msg em repositório temporário (contracts/commit-message.md, "Hook" e "Ativação").
import { test, after } from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { chmodSync, copyFileSync, mkdirSync, mkdtempSync, rmSync } from 'node:fs';
import os from 'node:os';
import path from 'node:path';

const ROOT = path.resolve(import.meta.dirname, '..', '..');
const VALID = 'feat(fatura): add late fee calculation';
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

function git(cwd, args, extraEnv = {}) {
  const result = spawnSync('git', args, { cwd, encoding: 'utf8', env: { ...ENV, ...extraEnv } });
  return { code: result.status, stdout: result.stdout, stderr: result.stderr };
}

function mustGit(cwd, args) {
  const result = git(cwd, args);
  assert.equal(result.code, 0, `git ${args.join(' ')}: ${result.stderr}`);
  return result.stdout.trim();
}

function newRepo() {
  const base = mkdtempSync(path.join(os.tmpdir(), 'habitattio-'));
  tempDirs.push(base);
  const repo = path.join(base, 'repo');
  mkdirSync(path.join(repo, '.githooks'), { recursive: true });
  mustGit(repo, ['init', '-q', '-b', 'main']);
  copyFileSync(path.join(ROOT, '.githooks', 'commit-msg'), path.join(repo, '.githooks', 'commit-msg'));
  chmodSync(path.join(repo, '.githooks', 'commit-msg'), 0o755);
  copyFileSync(path.join(ROOT, 'commitlint.config.mjs'), path.join(repo, 'commitlint.config.mjs'));
  mustGit(repo, ['add', '.']);
  mustGit(repo, ['-c', 'core.hooksPath=/dev/null', 'commit', '-q', '-m', 'chore(test): bootstrap']);
  mustGit(repo, ['config', 'core.hooksPath', '.githooks']);
  return { base, repo };
}

function commitCount(repo) {
  return Number(mustGit(repo, ['rev-list', '--count', 'HEAD']));
}

function prepareBranchToMerge(repo) {
  mustGit(repo, ['checkout', '-q', '-b', 'feature']);
  mustGit(repo, ['commit', '-q', '--allow-empty', '-m', 'feat(feature): add something']);
  mustGit(repo, ['checkout', '-q', 'main']);
  mustGit(repo, ['commit', '-q', '--allow-empty', '-m', 'fix(main): adjust something']);
}

test('deve aceitar commit no formato', () => {
  const { repo } = newRepo();

  const result = git(repo, ['commit', '--allow-empty', '-m', VALID]);

  assert.equal(result.code, 0, result.stderr);
});

test('deve recusar commit sem escopo', () => {
  const { repo } = newRepo();
  const before = commitCount(repo);

  const result = git(repo, ['commit', '--allow-empty', '-m', 'feat: no scope']);

  assert.notEqual(result.code, 0);
  assert.match(result.stderr, /feat\(fatura\): add late fee calculation/);
  assert.match(result.stderr, /AGENTS\.md §10/);
  assert.equal(commitCount(repo), before);
});

test('deve aceitar merge com a mensagem padrão do git', () => {
  const { repo } = newRepo();
  prepareBranchToMerge(repo);

  const result = git(repo, ['merge', '--no-ff', '--no-edit', 'feature']);

  assert.equal(result.code, 0, result.stderr);
});

test('deve recusar merge com mensagem própria fora do padrão', () => {
  const { repo } = newRepo();
  prepareBranchToMerge(repo);
  const before = commitCount(repo);

  const result = git(repo, ['merge', '--no-ff', '-m', 'merge: integrate x', 'feature']);

  assert.notEqual(result.code, 0);
  assert.equal(commitCount(repo), before);
});

test('deve recusar quando o npx não existe', () => {
  const { repo } = newRepo();

  const result = git(repo, ['commit', '--allow-empty', '-m', VALID], {
    HABITATTIO_NPX: 'habitattio-no-such-npx',
  });

  assert.notEqual(result.code, 0);
  assert.match(result.stderr, /Node\.js \(npx\) not found/);
});

test('deve recusar quando o commitlint não pode ser executado', () => {
  const { repo } = newRepo();

  const result = git(repo, ['commit', '--allow-empty', '-m', VALID], { HABITATTIO_NPX: 'false' });

  assert.notEqual(result.code, 0);
  assert.match(result.stderr, /could not run commitlint@21\.2\.4/);
});

test('deve aplicar a mesma barreira em outro worktree do mesmo clone', () => {
  const { base, repo } = newRepo();
  const worktree = path.join(base, 'wt');
  mustGit(repo, ['worktree', 'add', '-q', '-b', 'other', worktree]);

  const rejected = git(worktree, ['commit', '--allow-empty', '-m', 'feat: no scope']);
  const accepted = git(worktree, ['commit', '--allow-empty', '-m', VALID]);

  assert.notEqual(rejected.code, 0);
  assert.equal(accepted.code, 0, accepted.stderr);
});
