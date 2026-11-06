# Data Model: Convenções de contribuição verificáveis

**Feature**: `005-contribution-conventions` | **Date**: 2026-11-02

Não há persistência nem mudança no domínio Java. As "entidades" abaixo são as estruturas que o
hook e a sincronização de versão leem e produzem. Os nomes de campo são os usados no núcleo
`tools/versioning/semver.mjs`.

## Mensagem de commit (`CommitMessage`)

| Campo | Origem | Regra |
|---|---|---|
| `header` | 1ª linha | `tipo(escopo)[!]: descrição`, ≤ 100 caracteres (FR-001, FR-004) |
| `type` | header | ∈ {`feat`,`fix`,`docs`,`style`,`refactor`,`perf`,`test`,`build`,`ci`,`chore`}, minúsculo (FR-002) |
| `scope` | header | não vazio, livre (FR-003) |
| `bang` | header | `true` se há `!` antes de `:` (FR-005) |
| `subject` | header | não vazio (FR-004) |
| `body` | linhas após a 1ª linha em branco | livre |
| `footers` | bloco final `Token: valor` | `BREAKING CHANGE:`/`BREAKING-CHANGE:` → breaking (FR-005); `Version-Sync: X.Y.Z` → marcador |

**Classificação** (`classify(message) → kind`), na ordem:

| `kind` | Condição | Conta para a versão? |
|---|---|---|
| `ignored-git` | `Merge …` (padrões do git/GitHub), `Revert "…"`, `fixup!`, `squash!`, `amend!` | não |
| `sync-marker` | rodapé `Version-Sync:` | não (é o limite da faixa) |
| `invalid` | header não casa o padrão ou tipo fora da lista | não (bypass, Edge Cases) |
| `breaking` | válido e (`bang` ou rodapé breaking) | sim, impacto 3 |
| `feature` | válido, `type = feat` | sim, impacto 2 |
| `other` | válido, qualquer outro tipo | sim, impacto 1 |

## Versão do projeto (`Version`)

- Formato aceito: `X.Y.Z`, inteiros sem zero à esquerda (SemVer core). `X.Y.Z-SNAPSHOT` é
  aceito **somente** na execução sem marcador (bootstrap), e é lido como `X.Y.Z`.
- Local: o `<version>` filho direto de `<project>` no `pom.xml` (após
  `<artifactId>habitattio</artifactId>`). O `<version>` do `<parent>` nunca é lido nem escrito.

**Próxima versão** (`nextVersion(base, impact)`):

| Impacto | `base.X = 0` | `base.X ≥ 1` |
|---|---|---|
| 3 (breaking) | `0.(Y+1).0` | `(X+1).0.0` |
| 2 (feat) | `0.(Y+1).0` | `X.(Y+1).0` |
| 1 (outro) | `0.Y.(Z+1)` | `X.Y.(Z+1)` |
| 0 (nada conta) | sem mudança, sem commit | sem mudança, sem commit |

## Estado de sincronização (`SyncState`)

| Campo | Significado |
|---|---|
| `marker` | último commit em `--first-parent` da `main` com `Version-Sync:`; `null` no bootstrap |
| `range` | `marker..HEAD` (sem merges); `HEAD^1..HEAD` no bootstrap |
| `base` | versão do `pom.xml` no `marker`; no bootstrap, a de `HEAD` sem `-SNAPSHOT` |
| `current` | versão do `pom.xml` em `HEAD` |

**Decisão** (`decide(state, commits) → Outcome`):

```text
HEAD é sync-marker                          → noop("already synced")
current inválida                            → fail("invalid version")          FR-027
marker ≠ null e current ≠ base:
    base.X = 0 e current = 1.0.0            → mark(1.0.0)  (commit vazio)      FR-022a
    senão                                   → fail("manual version change")    FR-027
impacto máximo = 0                          → noop("nothing to bump")          FR-025
senão                                       → write(nextVersion(base, impacto))
```

`Outcome` ∈ {`noop`, `fail`, `mark`, `write`}. `mark` e `write` geram o commit de
sincronização com `Version-Sync: <versão>`. `write` também altera o `pom.xml`; `mark` é vazio.

**Invariantes**:
- A versão nunca diminui (a próxima versão é sempre maior que a `base`; a promoção só vai para
  cima).
- Um commit entra em no máximo uma faixa, porque as faixas são delimitadas por marcadores
  consecutivos em `--first-parent` (FR-026).
- `decide` é determinística: o mesmo `HEAD` dá o mesmo `Outcome` (idempotência).

## Commit de sincronização (`SyncCommit`)

```text
chore(release): set version to X.Y.Z

Version-Sync: X.Y.Z
```

Autor e committer: `github-actions[bot] <41898282+github-actions[bot]@users.noreply.github.com>`.
Passa no `commitlint.config.mjs` (research R2).

## Issue de task (`TaskIssue`)

| Campo | Regra |
|---|---|
| `title` | `[NNN] TXXX: <description>`; `NNN` = 3 dígitos do diretório da feature; `TXXX` = id da task (≥ 3 dígitos); descrição em inglês |
| `body` | linha original da task do `tasks.md` (português) + caminho do `tasks.md` |
| chave de deduplicação | `^\[NNN\] TXXX:` da feature atual |
