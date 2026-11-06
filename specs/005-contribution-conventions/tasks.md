---

description: "Tasks de contribution-conventions: commits, PRs, issues e versão verificáveis"
---

# Tasks: Convenções de contribuição verificáveis (commits, PRs, issues e versão)

**Input**: Design documents from `specs/005-contribution-conventions/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/commit-message.md](./contracts/commit-message.md), [contracts/pull-requests-and-issues.md](./contracts/pull-requests-and-issues.md), [contracts/version-sync.md](./contracts/version-sync.md), [quickstart.md](./quickstart.md)

**Tests**: **obrigatórios** para todo código executável (hook, config do commitlint, núcleo e
casca da versão), pela constitution §II e pelo AGENTS.md §3. A ordem é: **forma** (stub que
compila/roda, sem lógica) → **teste** → **ver falhar pelo motivo certo** → **implementar**. Um
teste visto falhar fica **read-only**: não pode ser editado, apagado nem pulado (`skip`/`todo`)
para passar. Templates, YAML de workflow/issue e texto do AGENTS.md não são código executável e
são validados pelo quickstart. Nenhum teste Java existente é alterado e nenhum arquivo em `src/`
muda.

**Organization**: tasks agrupadas por user story da spec (US1 = P1, US2 = P2, US3 = P3).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: pode rodar em paralelo (arquivo diferente, sem dependência pendente)
- **[Story]**: a user story atendida (US1, US2, US3)
- Caminhos relativos à raiz do repositório.
- Testes Node: `node:test` + `node:assert/strict`, ESM (`.mjs`), **nomes em português**
  descrevendo o comportamento (`test('deve recusar commit sem escopo', …)`), um comportamento
  por teste, Arrange/Act/Assert separados por linha em branco (research R10).
- Repositórios git temporários dos testes: criados em `fs.mkdtempSync(path.join(os.tmpdir(),
  'habitattio-'))`, com `GIT_AUTHOR_NAME/EMAIL` e `GIT_COMMITTER_NAME/EMAIL` fixos no `env` e
  `init.defaultBranch=main`; removidos em `after`.

## Decisões de sequenciamento (TDD)

- **Stub que aceita tudo antes do teste**: o stub da config (`rules: {}`) e o stub do hook
  (`exit 0`) fazem os testes de recusa falharem por **asserção**, não por arquivo ausente.
- **Stub que lança antes do teste do núcleo**: as funções de `semver.mjs` lançam
  `new Error('not implemented')`, o equivalente ao `UnsupportedOperationException` do AGENTS.md
  §3, passo 1. É o motivo certo de falha.
- **Testes de caracterização**: os casos de mensagem **válida** (V1–V6) passam já com o stub
  que aceita tudo. Eles não justificam código; quem justifica a config são os inválidos
  (I1–I8). Declarar isso no relatório.
- **A barreira do próprio repositório só é ligada em T010**, depois que hook e config estão
  verdes. A partir daí, todo commit desta feature passa por ela.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: confirmar o ambiente e a linha de base. Nenhum arquivo do projeto muda.

- [X] T001 Confirmar e registrar no relatório: `node --version` (≥ 20; esperado `v24.x`), `npx --version`, e aquecer o cache com `npx --yes -p @commitlint/cli@21.2.4 commitlint --version` (esperado `@commitlint/cli@21.2.4`). Se falhar por rede, parar: US1 depende disso.
- [X] T002 Rodar `./mvnw test` e registrar a linha de base (verde; Docker disponível ou não, conforme AGENTS.md §1). Registrar também `git config --get core.hooksPath` (esperado: vazio).

**Checkpoint**: ambiente pronto; suíte Java verde antes de qualquer mudança.

---

## Phase 2: Foundational (Blocking Prerequisites)

Não há pré-requisito compartilhado além do Setup. Cada story cria as próprias peças. US2 e US3
dependem da **convenção** declarada na US1 (formato), não do hook.

---

## Phase 3: User Story 1 - Commit fora do padrão é recusado na hora (Priority: P1) 🎯 MVP

**Goal**: todo commit em qualquer worktree de um clone preparado passa pelo commitlint;
mensagem inválida ou ferramenta ausente → commit recusado com instrução (FR-001 a FR-009).

**Independent Test**: `node --test "tools/conventions/*.test.mjs"` verde, e quickstart §2 (aceita/recusa em
dois worktrees, falha fechada com `HABITATTIO_NPX`).

### Forma (sem lógica)

- [X] T003 [US1] Criar os stubs: (a) `commitlint.config.mjs` na raiz com exatamente `export default { rules: {} };`; (b) `.githooks/commit-msg` executável (`chmod +x`; confirmar modo `100755` em `git ls-files -s` depois do `git add`), com `#!/bin/sh`, a linha `COMMITLINT_VERSION=21.2.4` sozinha numa linha (os testes a leem por regex `^COMMITLINT_VERSION=(.+)$`) e `exit 0`.

### Tests (escrever, rodar e ver falhar antes da implementação)

- [X] T004 [P] [US1] Criar `tools/conventions/commit-msg.test.mjs`. Helper `lint(message)`: lê a versão em `.githooks/commit-msg` pela regex de T003 e executa `npx --yes -p @commitlint/cli@<versão> commitlint --config <raiz>/commitlint.config.mjs` (sem `--edit`) com `message` no **stdin** (o probe da research R2 mostrou que `--edit` exige raiz git), retornando `{ code, output }`. Um teste por linha da tabela "Exemplos obrigatórios" de [contracts/commit-message.md](./contracts/commit-message.md), com as mensagens **verbatim**: V1–V6 esperam `code === 0`; I1–I8 esperam `code !== 0` **e** a regra citada no `output` (ex.: `[scope-empty]` para I1/I2, `[type-enum]` para I3/I6, `[type-case]` para I4, `[subject-empty]` para I5, `[header-max-length]` para I7, `[type-empty]` para I8). I7: `feat(fatura): ` seguido de 95 caracteres `a` (header de 109 caracteres). Rodar `node --test tools/conventions/commit-msg.test.mjs`. **Vermelho esperado**: I1–I8 aceitas pelo stub. V1–V6 verdes são **caracterização**.
- [X] T005 [P] [US1] Criar `tools/conventions/hook.test.mjs`. Em `before`: repositório temporário com **cópias** de `.githooks/commit-msg` (preservando o modo executável) e `commitlint.config.mjs` do checkout, commitadas com `git -c core.hooksPath=/dev/null commit` (bootstrap sem barreira), e então `git config core.hooksPath .githooks` — **caminho relativo**, igual ao da ativação real (research R1). Um teste por comportamento (verificar exit code de `git commit --allow-empty -m …` e `stderr`): (1) `deve aceitar commit no formato` com `feat(fatura): add late fee calculation`; (2) `deve recusar commit sem escopo` com `feat: no scope`: código ≠ 0, stderr contém `feat(fatura): add late fee calculation` **e** `AGENTS.md §10`, e `git rev-list --count HEAD` não aumentou; (3) `deve aceitar merge com a mensagem padrão do git` (`git merge --no-ff --no-edit` de um branch); (4) `deve recusar merge com mensagem própria fora do padrão` (`git merge --no-ff -m "merge: integrate x"`); (5) `deve recusar quando o npx não existe` com `env.HABITATTIO_NPX='habitattio-no-such-npx'`: stderr contém `Node.js (npx) not found`; (6) `deve recusar quando o commitlint não pode ser executado` com `env.HABITATTIO_NPX='false'`: stderr contém `could not run commitlint@21.2.4`; (7) `deve aplicar a mesma barreira em outro worktree do mesmo clone`: `git worktree add` de um branch novo, **sem nenhuma configuração adicional**; `feat: no scope` recusado e `feat(fatura): add late fee calculation` aceito nesse worktree (prova que o caminho relativo resolve a partir da raiz de cada worktree). Rodar `node --test tools/conventions/hook.test.mjs`. **Vermelho esperado**: (2), (4), (5), (6) e (7) aceitos pelo stub `exit 0`.

### Implementation

- [X] T006 [US1] Implementar `commitlint.config.mjs` exatamente como research R2: sem `extends`; `parserPreset.parserOpts` com `headerPattern: /^(\w*)(?:\(([^)]*)\))?(!)?: (.*)$/`, `headerCorrespondence: ['type', 'scope', 'breaking', 'subject']`, `noteKeywords: ['BREAKING CHANGE', 'BREAKING-CHANGE']`; regras nível 2: `type-enum` `['feat','fix','docs','style','refactor','perf','test','build','ci','chore']`, `type-empty` never, `type-case` `lower-case`, `scope-empty` never, `subject-empty` never, `header-max-length` 100. Manter `defaultIgnores` (não definir `ignores`). Rodar T004 até verde.
- [X] T007 [US1] Implementar `.githooks/commit-msg` (sh POSIX, `set -u`), conforme a tabela "Hook" de [contracts/commit-message.md](./contracts/commit-message.md): `NPX="${HABITATTIO_NPX:-npx}"`; `HOOK_DIR=$(cd "$(dirname "$0")" && pwd)`; `CONFIG="$HOOK_DIR/../commitlint.config.mjs"`. (1) `command -v "$NPX"` falha → stderr `commit-msg: Node.js (npx) not found. Install Node >= 20 and retry. Check: npx --version` + `exit 1`; (2) `"$NPX" --yes -p "@commitlint/cli@$COMMITLINT_VERSION" commitlint --version` falha → `commit-msg: could not run commitlint@$COMMITLINT_VERSION (offline on first run?). Retry with network access.` + `exit 1`; (3) `"$NPX" --yes -p … commitlint --config "$CONFIG" --edit "$1"` falha → imprimir em **stderr** (`>&2`) o bloco fixo: `Expected: <type>(<scope>)[!]: <description>`, `Example: feat(fatura): add late fee calculation`, `Types: feat fix docs style refactor perf test build ci chore`, `See AGENTS.md §10` + `exit 1`. Sucesso → `exit 0`. Rodar T005 até verde, depois `node --test "tools/conventions/*.test.mjs"` inteiro.

### Instruções e ativação

- [X] T008 [US1] Em `AGENTS.md`, criar `## 10. Contribuição: commits, PRs, issues e versão` com a subseção **Commits**: formato e tipos de [contracts/commit-message.md](./contracts/commit-message.md); escopo obrigatório e livre; breaking por `!`/`BREAKING CHANGE:`; inglês; merges com a mensagem padrão do git (nunca `merge: …`); **proibido `--no-verify`**; a recusa do hook se corrige reescrevendo a mensagem, nunca contornando a barreira; commits feitos no github.com (editor web, sugestões, merge) também seguem a convenção — a barreira não os vê; ao mesclar um PR na web, manter a mensagem padrão do GitHub. Incluir **Ativação**: `git config core.hooksPath .githooks` (uma vez por clone, vale para todos os worktrees), verificação com `git config --get core.hooksPath` → `.githooks`, e exigência de Node ≥ 20 (fail closed). Em §1 (Comandos), acrescentar a linha de ativação e `node --test "tools/**/*.test.mjs"`. Em §9 (Limites), registrar a exceção acordada: `@commitlint/cli` 21.2.4 via `npx`, fora do `pom.xml`; atualização de versão = commit `build(commitlint): …` que muda `COMMITLINT_VERSION` no hook.
- [X] T009 [US1] Rodar `node --test "tools/conventions/*.test.mjs"` (verde) e registrar a contagem.
- [X] T010 [US1] Ativar a barreira neste clone: `git config core.hooksPath .githooks`; conferir com `git config --get core.hooksPath`. Executar o quickstart §2 (recusa de `feat: no scope`, aceite de `feat(conventions): smoke test`, recusa de `merge: x` em outro worktree, falha fechada com `HABITATTIO_NPX=habitattio-no-such-npx`); desfazer commits e worktree de teste. Medir `time git commit --allow-empty -m "feat(conventions): timing"` com o cache quente e registrar (desfazer depois); acima de 3 s, reportar (plan, Performance Goals) sem mudar o desenho. A partir daqui, todo commit desta feature passa pelo hook.

**Checkpoint**: US1 entregue e útil sozinha. SC-003 e SC-004 verificáveis.

---

## Phase 4: User Story 2 - PRs e issues chegam com estrutura previsível (Priority: P2)

**Goal**: templates de PR e de issue, contrato para agentes e títulos de issue de task
`[NNN] TXXX: <description>` (FR-010 a FR-018).

**Independent Test**: conferência dos arquivos contra [contracts/pull-requests-and-issues.md](./contracts/pull-requests-and-issues.md) agora; formulários web e criação por agente no quickstart §3, **depois do merge** (o GitHub só lê templates da branch padrão).

### Implementation

- [X] T011 [P] [US2] Criar `.github/pull_request_template.md` com os cabeçalhos exatos, nesta ordem: `## Summary`, `## Notable changes`, `## Breaking changes`, `## Related`, `## Verification`, cada um com um comentário HTML curto em inglês dizendo o que preencher (`None` permitido em Breaking changes e Related), conforme o contrato.
- [X] T012 [P] [US2] Criar `.github/ISSUE_TEMPLATE/feature_request.yml` (issue form): `name: Feature request`, `description` em inglês, `title: "feat(<scope>): "`, campos `textarea` com `id`/`label` exatos do contrato: `problem` "Problem / motivation" (required), `behavior` "Desired behavior" (required), `beneficiaries` "Who benefits" (required), `out_of_scope` "Out of scope" (opcional), `acceptance` "Suggested acceptance criteria" (required). Um `markdown` inicial dizendo que a issue alimenta `/speckit-specify`.
- [X] T013 [P] [US2] Criar `.github/ISSUE_TEMPLATE/bug_report.yml`: `name: Bug report`, `title: "fix(<scope>): "`, campos do contrato: `description` "Description", `steps` "Steps to reproduce", `expected` "Expected result", `actual` "Actual result" (todos `textarea` required); `mode` "Run mode" (`dropdown`, required, opções `fast (H2, no profile)`, `prod (PostgreSQL, profile prod)`, `tests`); `evidence` "Evidence (logs, stack trace)" (`textarea`, opcional, `render: shell`). `markdown` inicial apontando `/speckit-bug-assess` → `/speckit-bug-fix`.
- [X] T014 [P] [US2] Criar `.github/ISSUE_TEMPLATE/config.yml` com `blank_issues_enabled: false`.
- [X] T015 [US2] Editar `.claude/skills/speckit-taskstoissues/SKILL.md` (**não** tocar em `.specify/templates/` nem `.specify/scripts/`, FR-017). Logo após o frontmatter, inserir `<!-- habitattio: local change, see AGENTS.md §10 — re-apply after a Spec Kit update -->`. No passo de deduplicação, trocar o critério para: obter `NNN` do prefixo de 3 dígitos do nome de `FEATURE_DIR` e considerar já existente só a issue cujo título case `^\[NNN\] TXXX:` (títulos legados `TXXX: …` sem prefixo não contam). No passo de criação, trocar o título para `[NNN] TXXX: <description>` com a descrição **traduzida para o inglês** (mantendo identificadores, caminhos e termos de domínio como `Fatura`/`diaVencimento`) e o corpo para `Task (tasks.md): <linha original em português, sem checkbox>` + `Source: specs/NNN-<name>/tasks.md`. Atualizar o exemplo (`- [ ] T001 Create project structure` → `[005] T001: Create project structure`). Não alterar mais nada da skill.
- [X] T016 [US2] Em `AGENTS.md` §10, subseções **Pull requests** e **Issues**: título de PR no formato do commit; corpo com as 5 seções; bug report `fix(<scope>): …` e feature request `feat(<scope>): …` com os blocos `### <label>` na ordem do contrato e `_No response_` em campo opcional vazio; exemplos `gh pr create --title … --body-file …` e `gh issue create …`; issues de task `[NNN] TXXX: <English>` com o corpo do contrato; issues e PRs existentes não são renomeados (FR-018). Em §4 (SDD), uma linha: `/speckit-taskstoissues` gera títulos `[NNN] TXXX: …` (ver §10).
- [X] T017 [US2] Conferir T011–T016 contra [contracts/pull-requests-and-issues.md](./contracts/pull-requests-and-issues.md) item a item (cabeçalhos, ids, labels, obrigatoriedade, títulos) e validar a sintaxe YAML dos três `.yml` com `ruby -e 'require "yaml"; ARGV.each { |f| YAML.load_file(f) }' <arquivos>` ou `python3 -c 'import yaml,sys; [yaml.safe_load(open(f)) for f in sys.argv[1:]]' <arquivos>`. Se nenhum dos dois estiver disponível, registrar que a sintaxe só será validada no GitHub (T032).

**Checkpoint**: US2 pronta no branch; a validação web fica para depois do merge (T032).

---

## Phase 5: User Story 3 - A versão do projeto acompanha a `main` (Priority: P3)

**Goal**: a cada push na `main`, a versão SemVer derivada dos commits desde o último
`Version-Sync:` é gravada no `pom.xml` sem `-SNAPSHOT` (FR-019 a FR-028).

**Independent Test**: `node --test "tools/versioning/*.test.mjs"` verde (S1–S18 de [contracts/version-sync.md](./contracts/version-sync.md)); `--dry-run` neste branch; quickstart §4 após o merge.

### Forma (sem lógica)

- [X] T018 [US3] Criar `tools/versioning/semver.mjs` com as assinaturas de [data-model.md](./data-model.md), cada corpo `throw new Error('not implemented')`: `export function classify(message)` → `'ignored-git'|'sync-marker'|'invalid'|'breaking'|'feature'|'other'`; `export function parseVersion(text, { allowSnapshot = false } = {})` → `{ major, minor, patch }` ou `null`; `export function formatVersion(v)`; `export function nextVersion(base, impact)` (impact 0–3); `export function impactOf(kind)`; `export function decide({ headIsMarker, marker, base, current }, messages)` → `{ outcome: 'noop'|'fail'|'mark'|'write', reason?, version?, from?, to?, commits? }`; `export function readProjectVersion(pomText)`; `export function writeProjectVersion(pomText, version)`; `export function syncCommitMessage(version)`. Criar `tools/versioning/version-sync.mjs` como CLI que aceita `--dry-run`, `--no-push` e `--repo <path>`, imprime `outcome=fail reason=not-implemented` e sai com código 1.

### Tests (escrever, rodar e ver falhar antes da implementação)

- [X] T019 [P] [US3] Criar `tools/versioning/semver.test.mjs`, um teste por comportamento: **classify**: `Merge branch 'x'`, `Merge pull request #1 from a/b`, `Merge remote-tracking branch 'origin/main'`, `Revert "feat(x): y"`, `fixup! feat(x): y`, `squash! feat(x): y`, `amend! feat(x): y` → `ignored-git`; mensagem com rodapé `Version-Sync: 0.4.3` → `sync-marker`; `feat: no scope`, `feature(x): y`, `add late fee` → `invalid`; `refactor(api)!: y`, `fix(x): y` + `BREAKING CHANGE: z`, `fix(x): y` + `BREAKING-CHANGE: z` → `breaking`; `feat(x): y` → `feature`; cada um de `fix docs style refactor perf test build ci chore` → `other`. **parseVersion**: `0.4.2` ok; `01.2.3`, `1.2`, `banana`, `1.2.3-rc.1` → `null`; `0.0.1-SNAPSHOT` → `null` sem opção e `0.0.1` com `allowSnapshot: true`. **nextVersion** (tabela "Próxima versão" do data-model, verbatim): `0.4.2`+3→`0.5.0`, +2→`0.5.0`, +1→`0.4.3`; `1.2.3`+3→`2.0.0`, +2→`1.3.0`, +1→`1.2.4`. **decide** (pseudocódigo do data-model): `headIsMarker` → `noop already-synced`; `current` inválida → `fail invalid-version`; marker presente, `base 0.9.3`, `current 1.0.0` → `mark 1.0.0`; marker presente, `base 0.4.2`, `current 0.7.0` → `fail manual-version-change`; `base 1.2.3`, `current 2.0.0` → `fail manual-version-change` (promoção só de `0.x` para `1.0.0`); só mensagens ignoradas/inválidas → `noop nothing-to-bump`; `fix` + `feat` em `0.4.2` → `write from=0.4.2 to=0.5.0 commits=2`; bootstrap (`marker: null`, `current` `0.0.1-SNAPSHOT`) + `feat` → `write 0.0.1 → 0.1.0`. **pom**: em texto com `<parent>…<version>4.1.2</version>…</parent>` seguido de `<artifactId>habitattio</artifactId>` e `<version>0.0.1-SNAPSHOT</version>`, `readProjectVersion` → `0.0.1-SNAPSHOT` e `writeProjectVersion(…, '0.1.0')` muda só essa linha (o resto do texto idêntico, byte a byte) (S15). **mensagem**: `syncCommitMessage('0.4.3')` === `'chore(release): set version to 0.4.3\n\nVersion-Sync: 0.4.3\n'` (S16, igual à V6). Rodar `node --test tools/versioning/semver.test.mjs`. **Vermelho esperado**: `not implemented` em todos.
- [X] T020 [P] [US3] Criar `tools/versioning/version-sync.test.mjs` com repositórios temporários contendo um `pom.xml` mínimo (parent `4.1.2` + `<artifactId>habitattio</artifactId>` + `<version>`) e executando `node tools/versioning/version-sync.mjs --repo <tmp> [--dry-run|--no-push]`, conferindo a linha `outcome=…` do stdout, o código de saída, o `pom.xml` e o último commit. Um teste por cenário S1–S14, S17 e S18 de [contracts/version-sync.md](./contracts/version-sync.md): o marcador é um commit com `Version-Sync: <base>` no rodapé e `pom.xml` na versão base; S13 usa dois branches mesclados com `git merge --no-ff --no-edit`; S14 roda `--no-push` e depois de novo (2ª: `noop already-synced`); com `--no-push`, conferir que o commit criado tem a mensagem de `syncCommitMessage` e (em `write`) só o `pom.xml` alterado, e (em `mark`) nenhum arquivo alterado; S17 e S18 usam um remote `git init --bare` local (`origin`). S17: outro clone empurra `feat(y)` depois do checkout, e o sync faz fetch + recálculo + push, resultando em `0.5.0` no remote. S18: `hooks/pre-receive` com `exit 1` resulta em `fail reason=push-rejected` e código 1. Rodar `node --test tools/versioning/version-sync.test.mjs`. **Vermelho esperado**: `outcome=fail reason=not-implemented` em todos.
- [X] T021 [P] [US3] Criar `tools/versioning/workflow.test.mjs` que lê `.github/workflows/version-sync.yml` como texto e verifica, um teste por item: `deve disparar só em push na main` (`branches: [main]` sob `push:`, e nenhum `pull_request:`); `deve pedir permissão de escrita em contents` (`contents: write`); `deve serializar execuções sem cancelar` (`group: version-sync` e `cancel-in-progress: false`); `deve fazer checkout do histórico completo` (`fetch-depth: 0`); `deve testar antes de sincronizar` (a linha `node --test "tools/versioning/*.test.mjs"` aparece antes de `node tools/versioning/version-sync.mjs`); `não deve criar tags nem releases` (nenhuma ocorrência de `git tag`, `create-release`, `gh release`). Rodar `node --test tools/versioning/workflow.test.mjs`. **Vermelho esperado**: arquivo inexistente (ENOENT) — aceito como motivo certo, porque o arquivo é o próprio artefato sob teste.

### Implementation

- [X] T022 [US3] Implementar `tools/versioning/semver.mjs` conforme [data-model.md](./data-model.md) e research R8: header pelo mesmo `headerPattern` da config e tipos da mesma lista; rodapé breaking `^BREAKING[ -]CHANGE: ` em linha própria; `Version-Sync:` em linha própria → marcador; ordem de classificação da tabela do data-model; SemVer core `^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$`; `readProjectVersion`/`writeProjectVersion` só no `<version>` imediatamente após `<artifactId>habitattio</artifactId>` (fora de `<parent>`), falhando com erro se não encontrar exatamente um. Sem dependências. Rodar T019 até verde.
- [X] T023 [US3] Implementar `tools/versioning/version-sync.mjs` (casca, usa `child_process.execFileSync('git', …)` com `cwd: repo`), research R7: marcador = primeiro commit de `git log --first-parent --format=%H%x00%B%x1e HEAD` cuja mensagem tem `Version-Sync:`; faixa = `git log --no-merges --format=%B%x1e <marker>..HEAD` (bootstrap: `HEAD^1..HEAD`; se `HEAD` não tem pai, todos os commits); `base` = `readProjectVersion` de `git show <marker>:pom.xml` (bootstrap: o de `HEAD` com `allowSnapshot`); `current` = o de `HEAD`; chamar `decide`. `write`: `writeProjectVersion`, `git add pom.xml`, `git commit -m` com `syncCommitMessage`, autor/committer `github-actions[bot] <41898282+github-actions[bot]@users.noreply.github.com>` via `-c user.name=… -c user.email=…`; `mark`: idem com `--allow-empty`. Sem `--no-push`/`--dry-run`: `git push origin HEAD:main`; se rejeitado por non-fast-forward (`git fetch origin main` + `git reset --hard origin/main` + recalcular do zero), até 3 tentativas; qualquer outra recusa ou tentativas esgotadas → `outcome=fail reason=push-rejected`, código 1. `invalid-version`/`manual-version-change` → código 1, detalhe no stderr. Saída: exatamente uma linha no formato da tabela CLI do contrato. Rodar T020 até verde, depois `node --test "tools/versioning/*.test.mjs"`.
- [X] T024 [US3] Criar `.github/workflows/version-sync.yml` conforme [contracts/version-sync.md](./contracts/version-sync.md): `on: push: branches: [main]`; `permissions: contents: write`; `concurrency: { group: version-sync, cancel-in-progress: false }`; job em `ubuntu-latest`: `actions/checkout@v7` com `fetch-depth: 0` e `ref: main`; `actions/setup-node@v7` com `node-version: 24`; passo `node --test "tools/versioning/*.test.mjs"`; passo `node tools/versioning/version-sync.mjs`. Sem tags, releases nem changelog (FR-028). Nenhum outro workflow. Rodar T021 até verde.
- [X] T025 [US3] Em `AGENTS.md` §10, subseção **Versão**: a versão do `pom.xml` na `main` é gravada só pela automação (`chore(release): set version to X.Y.Z` + `Version-Sync:`); regra de bump (breaking em `0.x` → MINOR, em `≥1` → MAJOR; `feat` → MINOR; demais tipos → PATCH); promoção para `1.0.0` só por commit explícito do dono que muda a versão para `1.0.0`; agentes **não** editam `<version>`; `node tools/versioning/version-sync.mjs --dry-run` mostra o que seria gravado.
- [X] T026 [US3] Rodar `node tools/versioning/version-sync.mjs --dry-run` neste branch e registrar a linha `outcome=…` (esperado: bootstrap com base `0.0.1`). O `pom.xml` não pode ter mudado (`git diff --quiet pom.xml`).

**Checkpoint**: US3 pronta no branch; a primeira sincronização real acontece no merge (T033).

---

## Phase 6: Polish & Cross-Cutting Concerns

- [X] T027 Rodar `/speckit-constitution` para uma emenda PATCH (1.0.1 → 1.0.2) em "Stack Tecnológico e Restrições" de `.specify/memory/constitution.md`, acrescentando: "Tooling de repositório (fora do build Maven): Node ≥ 20 para o hook `commit-msg` (`@commitlint/cli` versão fixa via `npx`, acordo do dono em 2026-11-02) e para `tools/`; GitHub Actions para a sincronização de versão na `main`." Sem mudar princípio nem regra MUST. Atualizar o Sync Impact Report e confirmar que o `AGENTS.md` §1/§9/§10 já reflete isso (Governance).
- [X] T028 Rodar `node --test "tools/**/*.test.mjs"` (todas as suítes) e registrar a contagem; tudo verde.
- [X] T029 Rodar `./mvnw test` e registrar: verde, igual à linha de base de T002. Sem Docker, seguir AGENTS.md §1 e dizer que a integração não rodou.
- [X] T030 Conferir fronteiras: `git diff --stat main -- src/ .specify/templates/ .specify/scripts/` vazio; `git diff main -- pom.xml` vazio (a versão só muda na `main`, pela automação); nenhuma dependência nova no `pom.xml`.
- [X] T031 Conferir SC-001 no branch: passar cada mensagem não-merge de `git log --no-merges --format=%B%x00 main..HEAD` pelo commitlint (mesmo comando do helper de T004) e registrar que todas passam; se alguma anterior a T010 falhar, registrar (o histórico não é reescrito, FR-018). Ao medir na `main` após o merge, separar commits locais de commits web (committer `GitHub <noreply@github.com>`).
- [ ] T032 **Após o merge na `main`** (ação do dono): quickstart §3. Os formulários web mostram só Feature request e Bug report, com título pré-preenchido e campos obrigatórios; o PR novo vem com as 5 seções; um agente cria um bug report de teste por CLI no formato (depois fechar); `/speckit-taskstoissues` desta feature gera `[005] TXXX: <English>` sem duplicar numa segunda execução. Registrar o resultado aqui.
- [ ] T033 **Após o merge na `main`**: quickstart §4. Em até 10 min, a `main` tem `chore(release): set version to 0.1.0` com `Version-Sync: 0.1.0`; nenhuma execução em cascata (ou `noop already-synced`); opcional `./mvnw -q help:evaluate -Dexpression=project.version -DforceStdout`. Registrar o resultado aqui.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (T001–T002)**: sem dependências.
- **US1 (T003–T010)**: depende do Setup. É o MVP.
- **US2 (T011–T017)**: depende só da convenção (formato) definida em [contracts/commit-message.md](./contracts/commit-message.md); pode começar junto com a US1. Na prática, roda depois de T010, para que os seus commits já passem pela barreira.
- **US3 (T018–T026)**: T019 depende da V6 (já fixada no contrato). Recomendado depois de T010. O workflow (T024) só faz sentido com a US1 em vigor.
- **Polish (T027–T031)**: depois das stories. **T032–T033**: depois do merge, ação do dono.

### Within Each User Story

- Forma (T003, T018) → testes (T004–T005, T019–T021) vistos falhar → implementação (T006–T007, T022–T023) → instruções (T008, T016, T025).
- T008, T016 e T025 editam o mesmo arquivo (`AGENTS.md`): **sequenciais**, nunca [P] entre si.

### Parallel Opportunities

- T004 ∥ T005 (arquivos de teste diferentes, ambos sobre os stubs de T003).
- T011 ∥ T012 ∥ T013 ∥ T014 (arquivos diferentes em `.github/`).
- T019 ∥ T020 ∥ T021 (arquivos de teste diferentes, ambos sobre os stubs de T018).

## Parallel Example: User Story 3

```bash
# depois de T018 (stubs):
Task: "T019 semver.test.mjs: classify / parseVersion / nextVersion / decide / pom / mensagem"
Task: "T020 version-sync.test.mjs: S1–S14, S17, S18 em repositórios temporários"
```

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Setup (T001–T002) → US1 (T003–T010).
2. **Parar e validar**: `node --test "tools/conventions/*.test.mjs"` + quickstart §2. Isso já fecha SC-001 a
   SC-004, e pode ir para a `main` sozinho.

### Incremental Delivery

1. US1 → barreira ativa (commits e merges no padrão).
2. US2 → templates e contrato para agentes (validação web depois do merge).
3. US3 → versão sincronizada (primeira execução real no merge).
4. Se a US3 estourar o apetite (2–4 dias), entregar US1 + US2 e deixar a US3 para um ciclo
   próprio (decision.md).

## Notes

- Testes vistos falhar são read-only (constitution §II). Se um parecer errado depois de visto
  falhar, parar e reportar ao dono, sem editar.
- Declarar no relatório os testes de caracterização (V1–V6 em T004; o caso (1) e o (3) de T005
  passam com o stub).
- Commits desta feature: Conventional, escopos sugeridos `conventions`, `versioning`, `github`,
  `agents`, `specs`.
