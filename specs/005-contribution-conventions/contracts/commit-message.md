# Contract: mensagem de commit e hook `commit-msg`

Cobre FR-001 a FR-009. Fonte de verdade das regras: `commitlint.config.mjs` (research R2).

## Formato

```text
<type>(<scope>)[!]: <description>
<linha em branco>
[body]
<linha em branco>
[footers]          ex.: BREAKING CHANGE: <text>
```

- `type`: `feat` | `fix` | `docs` | `style` | `refactor` | `perf` | `test` | `build` | `ci` | `chore`
- `scope`: obrigatório, não vazio, livre (ex.: `fatura`, `persistence`, `specs`, `agents`)
- `!` e/ou rodapé `BREAKING CHANGE:` marcam breaking change
- header ≤ 100 caracteres; descrição não vazia; inglês (convenção, não verificada)

## Aceito sem verificação (`defaultIgnores` do commitlint)

`Merge branch '…'`, `Merge branch '…' into …`, `Merge pull request #N from …`,
`Merge remote-tracking branch '…'`, `Revert "…"`, `fixup! …`, `squash! …`, `amend! …`.

## Exemplos obrigatórios (viram o teste `tools/conventions/commit-msg.test.mjs`)

| # | Mensagem | Esperado | Regra |
|---|---|---|---|
| V1 | `feat(fatura): add late fee calculation` | aceita | |
| V2 | `refactor(api)!: rename endpoints` | aceita (breaking) | |
| V3 | `fix(contrato): clamp due day` + rodapé `BREAKING CHANGE: due day semantics changed` | aceita (breaking) | |
| V4 | `Merge branch '002-contribution-conventions'` | aceita | |
| V5 | `Revert "feat(fatura): add late fee calculation"` | aceita | |
| V6 | `chore(release): set version to 0.4.3` + `Version-Sync: 0.4.3` | aceita | |
| I1 | `feat: add late fee` | recusada | `scope-empty` |
| I2 | `feat(): add late fee` | recusada | `scope-empty` |
| I3 | `feature(fatura): add late fee` | recusada | `type-enum` |
| I4 | `Feat(fatura): add late fee` | recusada | `type-case` |
| I5 | `feat(fatura):` | recusada | `subject-empty` |
| I6 | `merge: integrate feature x` | recusada | `type-enum` |
| I7 | `feat(fatura): ` + 95 caracteres | recusada | `header-max-length` |
| I8 | `add late fee` | recusada | `type-empty` |

## Hook `.githooks/commit-msg`

| Situação | Código de saída | Saída (stderr) |
|---|---|---|
| mensagem válida ou ignorada | 0 | nada |
| mensagem inválida | 1 | erros do commitlint + bloco fixo: formato, exemplo `feat(fatura): add late fee calculation`, ponteiro `AGENTS.md §10` |
| `npx` ausente (ou `HABITATTIO_NPX` aponta para comando inexistente) | 1 | `commit-msg: Node.js (npx) not found. Install Node >= 20 …` + como verificar |
| `npx` não conseguiu obter o commitlint | 1 | `commit-msg: could not run commitlint@21.2.4 (offline on first run?) …` |

Variáveis: `HABITATTIO_NPX` (padrão `npx`) troca o executável do npx. Existe para os testes
simularem ferramenta ausente (`HABITATTIO_NPX=habitattio-no-such-npx`) ou falha do npx
(`HABITATTIO_NPX=false`). A config é resolvida a partir do próprio hook
(`<dir do hook>/../commitlint.config.mjs`), não do diretório corrente.

## Ativação

```bash
git config core.hooksPath .githooks          # uma vez por clone; vale para todos os worktrees
git config --get core.hooksPath              # verificação: imprime .githooks
```
