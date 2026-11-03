// Núcleo puro da sincronização de versão (data-model.md; contracts/version-sync.md S15, S16).
import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  classify,
  decide,
  formatVersion,
  nextVersion,
  parseVersion,
  readProjectVersion,
  syncCommitMessage,
  writeProjectVersion,
} from './semver.mjs';

const POM = `<?xml version="1.0" encoding="UTF-8"?>
<project>
\t<modelVersion>4.0.0</modelVersion>
\t<parent>
\t\t<groupId>org.springframework.boot</groupId>
\t\t<artifactId>spring-boot-starter-parent</artifactId>
\t\t<version>4.1.2</version>
\t\t<relativePath/>
\t</parent>
\t<groupId>br.com.marden</groupId>
\t<artifactId>habitattio</artifactId>
\t<version>0.0.1-SNAPSHOT</version>
\t<name/>
</project>
`;

function bump(base, impact) {
  return formatVersion(nextVersion(parseVersion(base), impact));
}

// classify

for (const message of [
  "Merge branch 'x'",
  'Merge pull request #1 from a/b',
  "Merge remote-tracking branch 'origin/main'",
  'Revert "feat(x): y"',
  'fixup! feat(x): y',
  'squash! feat(x): y',
  'amend! feat(x): y',
]) {
  test(`deve ignorar mensagem gerada pelo git: ${message}`, () => {
    const kind = classify(message);

    assert.equal(kind, 'ignored-git');
  });
}

test('deve reconhecer o commit de sincronização pelo rodapé Version-Sync', () => {
  const message = 'chore(release): set version to 0.4.3\n\nVersion-Sync: 0.4.3\n';

  const kind = classify(message);

  assert.equal(kind, 'sync-marker');
});

for (const message of ['feat: no scope', 'feature(x): y', 'add late fee']) {
  test(`deve classificar como inválida mensagem fora do padrão: ${message}`, () => {
    const kind = classify(message);

    assert.equal(kind, 'invalid');
  });
}

test('deve reconhecer breaking change marcada com exclamação', () => {
  const message = 'refactor(api)!: y';

  const kind = classify(message);

  assert.equal(kind, 'breaking');
});

test('deve reconhecer breaking change no rodapé BREAKING CHANGE', () => {
  const message = 'fix(x): y\n\nBREAKING CHANGE: z';

  const kind = classify(message);

  assert.equal(kind, 'breaking');
});

test('deve reconhecer breaking change no rodapé BREAKING-CHANGE', () => {
  const message = 'fix(x): y\n\nBREAKING-CHANGE: z';

  const kind = classify(message);

  assert.equal(kind, 'breaking');
});

test('deve classificar feat como feature', () => {
  const message = 'feat(x): y';

  const kind = classify(message);

  assert.equal(kind, 'feature');
});

for (const type of ['fix', 'docs', 'style', 'refactor', 'perf', 'test', 'build', 'ci', 'chore']) {
  test(`deve classificar ${type} como other`, () => {
    const message = `${type}(x): y`;

    const kind = classify(message);

    assert.equal(kind, 'other');
  });
}

// parseVersion

test('deve ler versão SemVer válida', () => {
  const text = '0.4.2';

  const version = parseVersion(text);

  assert.deepEqual(version, { major: 0, minor: 4, patch: 2 });
});

for (const text of ['01.2.3', '1.2', 'banana', '1.2.3-rc.1']) {
  test(`deve rejeitar versão inválida: ${text}`, () => {
    const version = parseVersion(text);

    assert.equal(version, null);
  });
}

test('deve rejeitar sufixo SNAPSHOT por padrão', () => {
  const text = '0.0.1-SNAPSHOT';

  const version = parseVersion(text);

  assert.equal(version, null);
});

test('deve aceitar sufixo SNAPSHOT quando permitido', () => {
  const text = '0.0.1-SNAPSHOT';

  const version = parseVersion(text, { allowSnapshot: true });

  assert.deepEqual(version, { major: 0, minor: 0, patch: 1 });
});

// nextVersion (tabela "Próxima versão" do data-model)

test('deve incrementar MINOR em breaking change na série 0.x', () => {
  const next = bump('0.4.2', 3);

  assert.equal(next, '0.5.0');
});

test('deve incrementar MINOR em feat na série 0.x', () => {
  const next = bump('0.4.2', 2);

  assert.equal(next, '0.5.0');
});

test('deve incrementar PATCH em outros tipos na série 0.x', () => {
  const next = bump('0.4.2', 1);

  assert.equal(next, '0.4.3');
});

test('deve incrementar MAJOR em breaking change a partir de 1.0.0', () => {
  const next = bump('1.2.3', 3);

  assert.equal(next, '2.0.0');
});

test('deve incrementar MINOR em feat a partir de 1.0.0', () => {
  const next = bump('1.2.3', 2);

  assert.equal(next, '1.3.0');
});

test('deve incrementar PATCH em outros tipos a partir de 1.0.0', () => {
  const next = bump('1.2.3', 1);

  assert.equal(next, '1.2.4');
});

// decide

test('deve não fazer nada quando HEAD já é o commit de sincronização', () => {
  const state = { headIsMarker: true, marker: 'abc', base: '0.4.2', current: '0.4.2' };

  const outcome = decide(state, []);

  assert.deepEqual(outcome, { outcome: 'noop', reason: 'already-synced', version: '0.4.2' });
});

test('deve falhar quando a versão atual é inválida', () => {
  const state = { headIsMarker: false, marker: 'abc', base: '0.4.2', current: 'banana' };

  const outcome = decide(state, ['fix(x): y']);

  assert.equal(outcome.outcome, 'fail');
  assert.equal(outcome.reason, 'invalid-version');
});

test('deve aceitar a promoção manual de 0.x para 1.0.0', () => {
  const state = { headIsMarker: false, marker: 'abc', base: '0.9.3', current: '1.0.0' };

  const outcome = decide(state, ['chore(release): promote to 1.0.0', 'feat(x): y']);

  assert.deepEqual(outcome, { outcome: 'mark', version: '1.0.0' });
});

test('deve falhar em alteração manual de versão que não é a promoção para 1.0.0', () => {
  const state = { headIsMarker: false, marker: 'abc', base: '0.4.2', current: '0.7.0' };

  const outcome = decide(state, ['fix(x): y']);

  assert.equal(outcome.outcome, 'fail');
  assert.equal(outcome.reason, 'manual-version-change');
});

test('deve falhar em alteração manual de versão a partir de 1.0.0', () => {
  const state = { headIsMarker: false, marker: 'abc', base: '1.2.3', current: '2.0.0' };

  const outcome = decide(state, ['feat(x)!: y']);

  assert.equal(outcome.outcome, 'fail');
  assert.equal(outcome.reason, 'manual-version-change');
});

test('deve não fazer nada quando nenhum commit conta', () => {
  const state = { headIsMarker: false, marker: 'abc', base: '0.4.2', current: '0.4.2' };

  const outcome = decide(state, ["Merge branch 'x'", 'Revert "feat(x): y"', 'add late fee']);

  assert.deepEqual(outcome, { outcome: 'noop', reason: 'nothing-to-bump', version: '0.4.2' });
});

test('deve gravar a versão pelo commit de maior impacto', () => {
  const state = { headIsMarker: false, marker: 'abc', base: '0.4.2', current: '0.4.2' };

  const outcome = decide(state, ['fix(x): y', 'feat(y): z']);

  assert.deepEqual(outcome, { outcome: 'write', from: '0.4.2', to: '0.5.0', commits: 2 });
});

test('deve partir da versão sem SNAPSHOT na primeira sincronização', () => {
  const state = { headIsMarker: false, marker: null, base: null, current: '0.0.1-SNAPSHOT' };

  const outcome = decide(state, ['feat(x): y']);

  assert.deepEqual(outcome, { outcome: 'write', from: '0.0.1', to: '0.1.0', commits: 1 });
});

// pom.xml (S15)

test('deve ler a versão do projeto e não a do parent', () => {
  const pom = POM;

  const version = readProjectVersion(pom);

  assert.equal(version, '0.0.1-SNAPSHOT');
});

test('deve alterar só a versão do projeto no pom', () => {
  const pom = POM;

  const written = writeProjectVersion(pom, '0.1.0');

  assert.equal(written, POM.replace('<version>0.0.1-SNAPSHOT</version>', '<version>0.1.0</version>'));
  assert.match(written, /<version>4\.1\.2<\/version>/);
});

// mensagem (S16)

test('deve gerar a mensagem do commit de sincronização igual à V6 do contrato', () => {
  const version = '0.4.3';

  const message = syncCommitMessage(version);

  assert.equal(message, 'chore(release): set version to 0.4.3\n\nVersion-Sync: 0.4.3\n');
});
