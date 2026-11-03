# Contract: sincronização de versão

Cobre FR-019 a FR-028. Regras detalhadas em [data-model.md](../data-model.md) e research R6–R9.

## Gatilho e ambiente — `.github/workflows/version-sync.yml`

| Item | Valor |
|---|---|
| Evento | `push` em `main` |
| Concorrência | `group: version-sync`, `cancel-in-progress: false` |
| Permissões | `contents: write` |
| Checkout | `fetch-depth: 0` |
| Runtime | Node 24 (`actions/setup-node`) |
| Passos | `node --test tools/versioning` → `node tools/versioning/version-sync.mjs` |

## CLI — `node tools/versioning/version-sync.mjs [--dry-run] [--no-push] [--repo <path>]`

| Saída | Código | stdout (1 linha, chave=valor) | Efeito |
|---|---|---|---|
| noop (já sincronizado ou nada conta) | 0 | `outcome=noop reason=<already-synced\|nothing-to-bump> version=<X.Y.Z>` | nenhum |
| write | 0 | `outcome=write from=<base> to=<next> commits=<n>` | altera `pom.xml`, commita, faz push |
| mark (promoção 1.0.0) | 0 | `outcome=mark version=1.0.0` | commit vazio, push |
| fail | 1 | `outcome=fail reason=<invalid-version\|manual-version-change\|push-rejected>` + detalhe no stderr | nenhum |

`--dry-run`: calcula e imprime, sem commitar nem fazer push (usado nos testes e no quickstart).
`--no-push`: commita localmente, sem push (testes). `--repo`: diretório do repositório (padrão: cwd).

Push: `git push origin HEAD:main`. Em non-fast-forward: `fetch` + recálculo completo, até 3
tentativas; esgotou → `fail reason=push-rejected`.

## Casos de aceitação (viram `tools/versioning/*.test.mjs`)

| # | Base | Commits na faixa | Resultado |
|---|---|---|---|
| S1 | bootstrap, `0.0.1-SNAPSHOT` | `feat(x)` | `write 0.0.1 → 0.1.0` |
| S2 | `0.4.2` | `fix(x)`, `fix(y)` | `write → 0.4.3` |
| S3 | `0.4.2` | `fix(x)`, `feat(y)` | `write → 0.5.0` |
| S4 | `0.4.2` | `feat(x)`, `refactor(y)!` | `write → 0.5.0` |
| S5 | `0.4.2` | `docs(x)`, `test(y)` | `write → 0.4.3` |
| S6 | `1.2.3` | `fix(x)` + rodapé `BREAKING CHANGE:` | `write → 2.0.0` |
| S7 | `1.2.3` | `feat(x)` | `write → 1.3.0` |
| S8 | `0.4.2` | só merge/`Revert "…"`/mensagem fora do padrão | `noop nothing-to-bump` |
| S9 | `HEAD` é o commit `Version-Sync` | — | `noop already-synced` |
| S10 | `0.9.3`, `pom.xml` editado para `1.0.0` | qualquer | `mark 1.0.0` |
| S11 | `0.4.2`, `pom.xml` editado para `0.7.0` | qualquer | `fail manual-version-change` |
| S12 | `pom.xml` com `banana` | qualquer | `fail invalid-version` |
| S13 | dois merges após o marcador | `fix` no 1º, `feat` no 2º | `write → 0.5.0` (contados uma vez) |
| S14 | rodar S2 duas vezes sobre o mesmo `HEAD` após o push | — | 2ª: `noop already-synced` |
| S15 | pom com `<parent><version>4.1.2</version>` | `fix(x)` | só o `<version>` do projeto muda |
| S16 | — | — | a mensagem gerada é exatamente a V6 de [commit-message.md](./commit-message.md) |
| S17 | remote bare avançou depois do checkout | `fix(x)` local, `feat(y)` no remote | fetch + recálculo + push: `write → 0.5.0` |
| S18 | remote bare recusa o push (hook `pre-receive` com `exit 1`) | `fix(x)` | `fail push-rejected`, código 1 |

## Garantias

- Nenhuma tag, release, changelog ou nota (FR-028).
- O commit de sincronização não dispara nova execução (`GITHUB_TOKEN`) e é ignorado se
  disparar (S9) (FR-024, SC-009).
