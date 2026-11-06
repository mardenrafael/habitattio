// Pure core of the version sync (specs/005-contribution-conventions/data-model.md).
// No I/O and no dependencies: every rule here is covered by semver.test.mjs.

// Same header grammar and types as commitlint.config.mjs (research R2).
const HEADER = /^(\w*)(?:\(([^)]*)\))?(!)?: (.*)$/;
const TYPES = new Set(['feat', 'fix', 'docs', 'style', 'refactor', 'perf', 'test', 'build', 'ci', 'chore']);
const HEADER_MAX = 100;
const GIT_GENERATED = [/^Merge /, /^Revert "/, /^(fixup|squash|amend)! /];
const BREAKING_FOOTER = /^BREAKING[ -]CHANGE: /m;
const SYNC_FOOTER = /^Version-Sync: \S+$/m;
const CORE = /^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$/;
const SNAPSHOT = '-SNAPSHOT';
const PROJECT_VERSION = /(<artifactId>habitattio<\/artifactId>\s*<version>)([^<]*)(<\/version>)/g;
const IMPACT = { breaking: 3, feature: 2, other: 1 };

export function classify(message) {
  const text = message.replace(/\r\n/g, '\n').trim();
  const [header, ...rest] = text.split('\n');
  const body = rest.join('\n');

  if (GIT_GENERATED.some((pattern) => pattern.test(header))) return 'ignored-git';
  if (SYNC_FOOTER.test(body)) return 'sync-marker';

  const match = HEADER.exec(header);
  if (!match || header.length > HEADER_MAX) return 'invalid';
  const [, type, scope, bang, subject] = match;
  if (!TYPES.has(type) || !scope || !subject.trim()) return 'invalid';

  if (bang || BREAKING_FOOTER.test(body)) return 'breaking';
  return type === 'feat' ? 'feature' : 'other';
}

export function parseVersion(text, { allowSnapshot = false } = {}) {
  if (typeof text !== 'string') return null;
  const core = allowSnapshot && text.endsWith(SNAPSHOT) ? text.slice(0, -SNAPSHOT.length) : text;
  const match = CORE.exec(core);
  if (!match) return null;
  return { major: Number(match[1]), minor: Number(match[2]), patch: Number(match[3]) };
}

export function formatVersion(v) {
  return `${v.major}.${v.minor}.${v.patch}`;
}

export function impactOf(kind) {
  return IMPACT[kind] ?? 0;
}

export function nextVersion(base, impact) {
  const { major, minor, patch } = base;
  if (impact === 3 && major >= 1) return { major: major + 1, minor: 0, patch: 0 };
  if (impact === 3 || impact === 2) return { major, minor: minor + 1, patch: 0 };
  if (impact === 1) return { major, minor, patch: patch + 1 };
  return { major, minor, patch };
}

function sameVersion(a, b) {
  return a.major === b.major && a.minor === b.minor && a.patch === b.patch;
}

function fail(reason, detail) {
  return { outcome: 'fail', reason, detail };
}

export function decide({ headIsMarker, marker, base, current }, messages) {
  if (headIsMarker) return { outcome: 'noop', reason: 'already-synced', version: current };

  let from;
  if (marker === null) {
    from = parseVersion(current, { allowSnapshot: true });
    if (!from) return fail('invalid-version', `project version "${current}" is not X.Y.Z`);
  } else {
    const now = parseVersion(current);
    if (!now) return fail('invalid-version', `project version "${current}" is not X.Y.Z`);
    from = parseVersion(base);
    if (!from) return fail('invalid-version', `version at last sync "${base}" is not X.Y.Z`);
    if (!sameVersion(now, from)) {
      if (from.major === 0 && current === '1.0.0') return { outcome: 'mark', version: '1.0.0' };
      return fail('manual-version-change', `version changed by hand from ${base} to ${current}`);
    }
  }

  const impacts = messages.map((message) => impactOf(classify(message))).filter((impact) => impact > 0);
  if (impacts.length === 0) return { outcome: 'noop', reason: 'nothing-to-bump', version: formatVersion(from) };

  const to = nextVersion(from, Math.max(...impacts));
  return { outcome: 'write', from: formatVersion(from), to: formatVersion(to), commits: impacts.length };
}

function projectVersionMatches(pomText) {
  const matches = [...pomText.matchAll(PROJECT_VERSION)];
  if (matches.length !== 1) {
    throw new Error(`expected exactly one <version> after <artifactId>habitattio</artifactId>, found ${matches.length}`);
  }
  return matches[0];
}

export function readProjectVersion(pomText) {
  return projectVersionMatches(pomText)[2];
}

export function writeProjectVersion(pomText, version) {
  const match = projectVersionMatches(pomText);
  const start = match.index + match[1].length;
  return pomText.slice(0, start) + version + pomText.slice(start + match[2].length);
}

export function syncCommitMessage(version) {
  return `chore(release): set version to ${version}\n\nVersion-Sync: ${version}\n`;
}
