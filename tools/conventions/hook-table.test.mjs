// SC-003: tabela V1–V6 / I1–I8 (contracts/commit-message.md) passada pelo hook real, em dois
// worktrees do mesmo clone, com core.hooksPath relativo como na ativação real.
import { test, after, before } from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { chmodSync, copyFileSync, mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import os from 'node:os';
import path from 'node:path';

const ROOT = path.resolve(import.meta.dirname, '..', '..');
const ENV = {
  ...process.env,
  GIT_AUTHOR_NAME: 'Test',
  GIT_AUTHOR_EMAIL: 'test@example.com',
  GIT_COMMITTER_NAME: 'Test',
  GIT_COMMITTER_EMAIL: 'test@example.com',
  GIT_CONFIG_GLOBAL: '/dev/null',
  GIT_CONFIG_NOSYSTEM: '1',
};
let base;
const worktrees = {};

function git(cwd, args) {
  const result = spawnSync('git', args, { cwd, encoding: 'utf8', env: ENV });
  return { code: result.status, stdout: result.stdout.trim(), stderr: result.stderr };
}

function mustGit(cwd, args) {
  const result = git(cwd, args);
  assert.equal(result.code, 0, `git ${args.join(' ')}: ${result.stderr}`);
  return result.stdout;
}

function lastSubject(cwd) {
  return mustGit(cwd, ['log', '-1', '--format=%s']);
}

before(() => {
  base = mkdtempSync(path.join(os.tmpdir(), 'habitattio-'));
  const repo = path.join(base, 'repo');
  mkdirSync(path.join(repo, '.githooks'), { recursive: true });
  mustGit(repo, ['init', '-q', '-b', 'main']);
  copyFileSync(path.join(ROOT, '.githooks', 'commit-msg'), path.join(repo, '.githooks', 'commit-msg'));
  chmodSync(path.join(repo, '.githooks', 'commit-msg'), 0o755);
  copyFileSync(path.join(ROOT, 'commitlint.config.mjs'), path.join(repo, 'commitlint.config.mjs'));
  mustGit(repo, ['add', '.']);
  mustGit(repo, ['-c', 'core.hooksPath=/dev/null', 'commit', '-q', '-m', 'chore(test): bootstrap']);
  mustGit(repo, ['config', 'core.hooksPath', '.githooks']);
  const second = path.join(base, 'second');
  mustGit(repo, ['worktree', 'add', '-q', '-b', 'second', second]);
  worktrees['worktree principal'] = repo;
  worktrees['segundo worktree'] = second;
});

after(() => rmSync(base, { recursive: true, force: true }));

const ACCEPTED = [
  ['V1', ['-m', 'feat(fatura): add late fee calculation']],
  ['V2', ['-m', 'refactor(api)!: rename endpoints']],
  ['V3', ['-m', 'fix(contrato): clamp due day', '-m', 'BREAKING CHANGE: due day semantics changed']],
  ['V6', ['-m', 'chore(release): set version to 0.4.3', '-m', 'Version-Sync: 0.4.3']],
];

const REJECTED = [
  ['I1', 'feat: add late fee'],
  ['I2', 'feat(): add late fee'],
  ['I3', 'feature(fatura): add late fee'],
  ['I4', 'Feat(fatura): add late fee'],
  ['I5', 'feat(fatura):'],
  ['I6', 'merge: integrate feature x'],
  ['I7', `feat(fatura): ${'a'.repeat(95)}`],
  ['I8', 'add late fee'],
];

for (const where of ['worktree principal', 'segundo worktree']) {
  for (const [id, args] of ACCEPTED) {
    test(`${id} deve ser aceita pelo hook no ${where}`, () => {
      const cwd = worktrees[where];

      const result = git(cwd, ['commit', '--allow-empty', ...args]);

      assert.equal(result.code, 0, result.stderr);
    });
  }

  test(`V4 deve aceitar a mensagem padrão de merge do git no ${where}`, () => {
    const cwd = worktrees[where];
    const branch = `002-contribution-conventions-${where.replace(/\s/g, '-')}`;
    mustGit(cwd, ['checkout', '-q', '-b', branch]);
    mustGit(cwd, ['commit', '-q', '--allow-empty', '-m', 'feat(x): add something']);
    mustGit(cwd, ['checkout', '-q', '-']);

    const result = git(cwd, ['merge', '--no-ff', '--no-edit', branch]);

    assert.equal(result.code, 0, result.stderr);
    assert.equal(lastSubject(cwd), `Merge branch '${branch}'${where === 'segundo worktree' ? ' into second' : ''}`);
  });

  test(`V5 deve aceitar a mensagem padrão de revert do git no ${where}`, () => {
    const cwd = worktrees[where];
    writeFileSync(path.join(cwd, 'late-fee.txt'), 'late fee\n');
    mustGit(cwd, ['add', 'late-fee.txt']);
    mustGit(cwd, ['commit', '-q', '-m', 'feat(fatura): add late fee calculation']);

    const result = git(cwd, ['revert', '--no-edit', 'HEAD']);

    assert.equal(result.code, 0, result.stderr);
    assert.equal(lastSubject(cwd), 'Revert "feat(fatura): add late fee calculation"');
  });

  for (const [id, message] of REJECTED) {
    test(`${id} deve ser recusada pelo hook no ${where}`, () => {
      const cwd = worktrees[where];
      const before = mustGit(cwd, ['rev-parse', 'HEAD']);

      const result = git(cwd, ['commit', '--allow-empty', '-m', message]);

      assert.notEqual(result.code, 0, `deveria recusar: ${JSON.stringify(message)}`);
      assert.equal(mustGit(cwd, ['rev-parse', 'HEAD']), before);
    });
  }
}
