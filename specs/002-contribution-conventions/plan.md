# Implementation Plan: Convenções de contribuição verificáveis (commits, PRs, issues e versão)

**Branch**: `002-contribution-conventions` | **Date**: 2026-11-02 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/002-contribution-conventions/spec.md`

## Summary

O repositório passa a declarar a convenção Conventional Commits e a impô-la num hook
versionado `.githooks/commit-msg`. O hook roda `@commitlint/cli@21.2.4` via `npx`, com um
`commitlint.config.mjs` autossuficiente (regras e `headerPattern` com `!` inline, sem pacote
de preset). É ativado uma vez por clone com `git config core.hooksPath .githooks` e vale para
todos os worktrees. Se a ferramenta não está disponível, o commit é recusado.

Templates de PR e issue forms (feature request `feat(<scope>): …`, bug report `fix(<scope>): …`)
entram em `.github/`. O AGENTS.md ganha uma seção §10 com o contrato que agentes seguem ao
criar PR/issue por CLI. A skill `speckit-taskstoissues` passa a gerar `[NNN] TXXX: <English>`
e a deduplicar por feature.

Por fim, um workflow `version-sync` em cada push na `main` roda um script Node sem
dependências (`tools/versioning/`). O script calcula a versão a partir dos commits desde o
último marcador `Version-Sync:`, grava o `<version>` do projeto no `pom.xml` sem `-SNAPSHOT`
e devolve o resultado num commit `chore(release): set version to X.Y.Z`. As regras e as
alternativas estão em [research.md](./research.md) (R1–R10).

## Technical Context

**Language/Version**: shell POSIX (hook), JavaScript ESM em Node 24 (config, tooling, testes),
YAML (GitHub Actions e issue forms). O código Java 25 / Spring Boot 4.1.2 **não muda**. Só a
linha `<version>` do projeto no `pom.xml` passa a ser gravada pela automação.

**Primary Dependencies**: `@commitlint/cli` **21.2.4** (fixo), obtido via `npx`, fora do
`pom.xml`; é a exceção aceita pelo dono (AGENTS.md §9, decision.md). GitHub Actions oficiais
`actions/checkout@v7` e `actions/setup-node@v7` (majors mais recentes em 2026-11-02: v7.0.1 e v7.1.0). Nenhuma outra dependência: os testes usam
`node:test`/`node:assert`, embutidos.

**Storage**: N/A. O estado vive no git: o trailer `Version-Sync:` marca a última sincronização,
e a versão está no `pom.xml`.

**Testing**: `node --test "tools/**/*.test.mjs"`, com tabela de mensagens contra a config, hook executado em
repositório git temporário, núcleo SemVer puro e casca contra repositórios temporários.
`./mvnw test` continua obrigatório no fim (Definition of Done).

**Target Platform**: Linux (máquina do dono e worktrees) para o hook; runner `ubuntu-latest`
do GitHub Actions para a sincronização.

**Project Type**: tooling de repositório (processo de contribuição) num web-service Maven.

**Performance Goals**: hook < 3 s com o commitlint em cache; sincronização visível na `main` em
até 10 min após o push (SC-008).

**Constraints**: sem dependência nova no `pom.xml` (FR-009); sem editar `.specify/templates/`
nem `.specify/scripts/` (FR-017); sem tags/releases/changelog (FR-028); sem validação de
commit/título na CI (fora de escopo); histórico e issues existentes intocados (FR-018); `main`
sem proteção (Assumptions).

**Scale/Scope**: 1 hook, 1 config, 3 templates + 1 config de issue, 1 workflow, 2 módulos Node
(núcleo + casca), 5 arquivos de teste, 1 skill editada, 1 seção nova e 2 ajustes no AGENTS.md,
1 emenda PATCH na constitution (Stack).

Nenhum `NEEDS CLARIFICATION` restante: todas as incógnitas foram resolvidas em research.md.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Princípio | Verificação | Pré-design | Pós-design |
|---|---|---|---|
| I. Hexagonal | Nenhum arquivo em `src/` muda. Tooling fica em `tools/`, `.githooks/` e `.github/`, fora das camadas. | ✅ | ✅ |
| II. TDD | Todo código desta feature (hook, config, núcleo, casca) nasce de um teste `node:test` visto falhar pelo motivo certo (ver "Ordem TDD"). Testes criados são read-only. Nomes de teste em português, AAA (research R10). Nenhum teste Java é alterado. O workflow `version-sync.yml`, que executa na CI, também nasce de um teste vermelho (`tools/versioning/workflow.test.mjs`, estrutural; analyze D1). Templates de PR/issue e texto do AGENTS.md não são executáveis e são validados pelo quickstart. | ✅ | ✅ (corrigido após `/speckit-analyze` D1) |
| III. SDD | spec → clarify (3 respostas do dono na spec) → plan. Tasks e analyze vêm a seguir. | ✅ | ✅ |
| IV. YAGNI / acordo | Dependência nova **fora** do `pom.xml` (commitlint): acordo explícito registrado no assessment (decision.md, Strategic fit). Nada no `pom.xml`. Sem framework de teste (`node:test`). Sem ferramenta de release. O núcleo e a casca existem porque há duas regras reais (cálculo e git), não "para o futuro". | ✅ (com acordo) | ✅ |
| V. Convenções | Domínio intacto. Commits, PRs e issues em inglês, por decisão do dono. Specs, docs, `tasks.md` e testes descritos em português. | ✅ | ✅ |
| Stack / Governance | Node, commitlint e GitHub Actions entram como tooling de repositório. **Ação**: emenda PATCH (1.0.1 → 1.0.2) na seção "Stack Tecnológico e Restrições", via `/speckit-constitution` (task de Polish), como no precedente da 001 (Flyway) (analyze F1). O AGENTS.md é atualizado no mesmo change set (FR-029): §10 nova, §9 registra a exceção do commitlint, §4 aponta o formato das issues de task. | ⚠️ follow-up | ⚠️ follow-up, vira task |

**Resultado**: o gate passa sem violação a justificar. O único item pendente é a emenda
documental do Stack, que entra como task. Complexity Tracking não se aplica.

### Ordem TDD

Cada passo diz qual teste fica vermelho e qual mudança ele justifica. A numeração final das
tasks fica no `tasks.md`.

1. **US1, config**: `tools/conventions/commit-msg.test.mjs` com os casos V1–V6/I1–I8 do
   [contrato](./contracts/commit-message.md) fica vermelho, porque não há
   `commitlint.config.mjs`. Isso justifica a config.
2. **US1, hook**: `tools/conventions/hook.test.mjs` (repositório temporário com cópias do hook
   e da config, e `core.hooksPath` **relativo** `.githooks`, como na ativação real; analyze C2)
   fica vermelho contra o stub `exit 0`. Casos: aceita válida, recusa inválida com exemplo no
   stderr, merge padrão × `merge: …`, falha fechada via `HABITATTIO_NPX`, outro worktree. Isso
   justifica `.githooks/commit-msg`.
3. **US2**: templates `.github/`, edição da skill `speckit-taskstoissues` e AGENTS.md §10 (PR e
   issues). São texto, sem teste automatizado, e são validados pelo quickstart §3.
4. **US3, núcleo**: `tools/versioning/semver.test.mjs` (classificação, `nextVersion`, `decide`,
   leitura e escrita do `<version>` do projeto com `<parent>` presente, mensagem gerada passando
   no commitlint) fica vermelho. Isso justifica `semver.mjs`.
5. **US3, casca**: `tools/versioning/version-sync.test.mjs` (S1–S14, S17 e S18 do
   [contrato](./contracts/version-sync.md) em repositórios temporários, com `--dry-run`,
   `--no-push` e remote bare local) fica vermelho. Isso justifica `version-sync.mjs`.
6. **US3, workflow**: `tools/versioning/workflow.test.mjs` fica vermelho (arquivo ausente). Isso
   justifica `.github/workflows/version-sync.yml`. A execução real é validada no quickstart §4,
   no primeiro merge. Depois, AGENTS.md §10 (versão).
7. **Polish**: emenda PATCH da constitution (Stack), `node --test "tools/**/*.test.mjs"`, `./mvnw test`,
   quickstart, e confirmar que `domain/` e `application/` não foram tocados.

## Project Structure

### Documentation (this feature)

```text
specs/002-contribution-conventions/
├── spec.md
├── plan.md              # este arquivo
├── research.md          # R1–R10
├── data-model.md        # mensagem, versão, estado de sincronização, issue de task
├── quickstart.md        # validação ponta a ponta
├── contracts/
│   ├── commit-message.md
│   ├── pull-requests-and-issues.md
│   └── version-sync.md
├── checklists/requirements.md
└── tasks.md             # /speckit-tasks (ainda não criado)
```

### Source Code (repository root)

```text
.githooks/
└── commit-msg                       # NOVO (executável): npx commitlint --edit, falha fechada
commitlint.config.mjs                # NOVO: regras e parserPreset inline
.github/
├── pull_request_template.md         # NOVO
├── ISSUE_TEMPLATE/
│   ├── feature_request.yml          # NOVO
│   ├── bug_report.yml               # NOVO
│   └── config.yml                   # NOVO: blank_issues_enabled: false
└── workflows/
    └── version-sync.yml             # NOVO
tools/
├── conventions/
│   ├── commit-msg.test.mjs          # NOVO: tabela de mensagens × config
│   └── hook.test.mjs                # NOVO: hook em repositório temporário
└── versioning/
    ├── semver.mjs                   # NOVO: núcleo puro
    ├── semver.test.mjs              # NOVO
    ├── version-sync.mjs             # NOVO: casca git/CLI
    ├── version-sync.test.mjs        # NOVO
    └── workflow.test.mjs            # NOVO: estrutura do version-sync.yml
.specify/memory/constitution.md      # ALTERADO: PATCH 1.0.2, tooling no Stack
.claude/skills/speckit-taskstoissues/SKILL.md   # ALTERADO: título [NNN] TXXX, dedupe por feature
AGENTS.md                            # ALTERADO: §10 nova; §9 exceção commitlint; §4 issues de task
pom.xml                              # gravado pela automação na main (só <version> do projeto)
```

**Structure Decision**: tooling de processo em diretórios próprios na raiz (`.githooks/`,
`.github/`, `tools/`), separados de `src/`, para que nenhuma regra de arquitetura hexagonal seja
tocada e o build Maven não dependa do Node. `tools/` agrupa por finalidade (`conventions/`,
`versioning/`), cada uma com os seus testes ao lado.

## Riscos e mitigação

| Risco | Mitigação |
|---|---|
| Atualização do Spec Kit sobrescreve a skill `speckit-taskstoissues` | Comentário-sentinela no SKILL.md, regra duplicada no AGENTS.md §10, risco aceito na spec |
| Primeira execução do hook offline | Falha fechada com mensagem própria (R3) |
| `main` ganha proteção de branch | Push da sincronização falha de forma visível (FR-027); exigiria um token com bypass, fora de escopo |
| Agente usa `--no-verify` | Proibido no AGENTS.md §10; commit fora do padrão não conta para a versão |
| Bootstrap conta commits demais | Faixa de bootstrap limitada a `HEAD^1..HEAD` (R7) |

## Complexity Tracking

Não se aplica: nenhuma violação da constitution.
