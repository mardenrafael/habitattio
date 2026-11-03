#!/usr/bin/env node
// Version sync CLI (specs/002-contribution-conventions/contracts/version-sync.md, research R7).
// Usage: node tools/versioning/version-sync.mjs [--dry-run] [--no-push] [--repo <path>]
//
// Derives the range from state, not from the push event: commits since the last commit on
// main's first-parent history that carries a "Version-Sync:" footer. Running twice on the same
// HEAD gives the same result, and every commit is counted once.
import { execFileSync } from 'node:child_process';
import { readFileSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { classify, decide, readProjectVersion, syncCommitMessage, writeProjectVersion } from './semver.mjs';

const MAX_PUSH_ATTEMPTS = 3;
const BOT_NAME = 'github-actions[bot]';
const BOT_EMAIL = '41898282+github-actions[bot]@users.noreply.github.com';
const RECORD = '\x1e';

function parseArgs(argv) {
  const options = { dryRun: false, push: true, repo: process.cwd() };
  for (let i = 0; i < argv.length; i += 1) {
    if (argv[i] === '--dry-run') options.dryRun = true;
    else if (argv[i] === '--no-push') options.push = false;
    else if (argv[i] === '--repo') options.repo = path.resolve(argv[++i]);
    else throw new Error(`unknown argument: ${argv[i]}`);
  }
  return options;
}

function makeGit(repo) {
  const env = {
    ...process.env,
    GIT_AUTHOR_NAME: BOT_NAME,
    GIT_AUTHOR_EMAIL: BOT_EMAIL,
    GIT_COMMITTER_NAME: BOT_NAME,
    GIT_COMMITTER_EMAIL: BOT_EMAIL,
  };
  return (args, input) =>
    execFileSync('git', args, { cwd: repo, env, input, encoding: 'utf8', stdio: ['pipe', 'pipe', 'pipe'] });
}

function tryGit(git, args) {
  try {
    git(args);
    return true;
  } catch {
    return false;
  }
}

function records(output) {
  return output.split(RECORD).map((record) => record.replace(/^\n/, '')).filter((record) => record.trim());
}

function projectVersionAt(git, rev) {
  try {
    return readProjectVersion(git(['show', `${rev}:pom.xml`]));
  } catch {
    return null;
  }
}

function compute(git) {
  const history = records(git(['log', '--first-parent', `--format=%H%x00%B${RECORD}`, 'HEAD'])).map((record) => {
    const [sha, message] = record.split('\x00');
    return { sha, message };
  });
  const markerIndex = history.findIndex(({ message }) => classify(message) === 'sync-marker');
  const marker = markerIndex >= 0 ? history[markerIndex].sha : null;

  const current = projectVersionAt(git, 'HEAD');
  const base = marker ? projectVersionAt(git, marker) : null;
  const hasParent = tryGit(git, ['rev-parse', '--verify', '--quiet', 'HEAD^1']);
  const range = marker ? `${marker}..HEAD` : hasParent ? 'HEAD^1..HEAD' : 'HEAD';
  const messages = records(git(['log', '--no-merges', `--format=%B${RECORD}`, range]));

  return decide({ headIsMarker: markerIndex === 0, marker, base, current: current ?? '' }, messages);
}

function commitSync(git, repo, outcome) {
  const version = outcome.outcome === 'write' ? outcome.to : outcome.version;
  if (outcome.outcome === 'write') {
    const pomPath = path.join(repo, 'pom.xml');
    writeFileSync(pomPath, writeProjectVersion(readFileSync(pomPath, 'utf8'), version));
    git(['add', 'pom.xml']);
    git(['commit', '-q', '-F', '-'], syncCommitMessage(version));
  } else {
    git(['commit', '-q', '--allow-empty', '-F', '-'], syncCommitMessage(version));
  }
}

function remoteMoved(git) {
  git(['fetch', '-q', 'origin', '+refs/heads/main:refs/remotes/origin/main']);
  return !tryGit(git, ['merge-base', '--is-ancestor', 'refs/remotes/origin/main', 'HEAD']);
}

function line(outcome) {
  switch (outcome.outcome) {
    case 'noop':
      return `outcome=noop reason=${outcome.reason} version=${outcome.version}`;
    case 'write':
      return `outcome=write from=${outcome.from} to=${outcome.to} commits=${outcome.commits}`;
    case 'mark':
      return `outcome=mark version=${outcome.version}`;
    default:
      return `outcome=fail reason=${outcome.reason}`;
  }
}

function finish(outcome) {
  console.log(line(outcome));
  if (outcome.outcome === 'fail' && outcome.detail) console.error(`version-sync: ${outcome.detail}`);
  return outcome.outcome === 'fail' ? 1 : 0;
}

function run(options) {
  const git = makeGit(options.repo);

  for (let attempt = 1; attempt <= MAX_PUSH_ATTEMPTS; attempt += 1) {
    const outcome = compute(git);
    if (options.dryRun || outcome.outcome === 'noop' || outcome.outcome === 'fail') return finish(outcome);

    commitSync(git, options.repo, outcome);
    if (!options.push) return finish(outcome);
    if (tryGit(git, ['push', '-q', 'origin', 'HEAD:main'])) return finish(outcome);

    if (!remoteMoved(git)) {
      return finish({ outcome: 'fail', reason: 'push-rejected', detail: 'push to main was refused by the remote' });
    }
    git(['reset', '-q', '--hard', 'refs/remotes/origin/main']);
  }

  return finish({
    outcome: 'fail',
    reason: 'push-rejected',
    detail: `main kept moving; gave up after ${MAX_PUSH_ATTEMPTS} attempts`,
  });
}

try {
  process.exitCode = run(parseArgs(process.argv.slice(2)));
} catch (error) {
  console.log('outcome=fail reason=error');
  console.error(`version-sync: ${error.message}`);
  process.exitCode = 1;
}
