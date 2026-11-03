# Quickstart: validar as convenções de contribuição

**Feature**: `002-contribution-conventions`. Guia de validação ponta a ponta. As regras estão
nos [contracts](./contracts/) e no [data-model.md](./data-model.md); aqui ficam só os passos.

## Pré-requisitos

- Node ≥ 20 com `npx` (`node --version`); a máquina do dono tem `v24.14.1`.
- Rede na primeira execução do hook (download do `@commitlint/cli@21.2.4` para o cache do npm).
- `gh` autenticado no repositório (para US2/US3).
- No GitHub: Actions habilitado, "Workflow permissions" com escrita, `main` sem proteção.

## 1. Testes automatizados (todas as stories)

```bash
node --test "tools/**/*.test.mjs"   # hook, config do commitlint, núcleo e casca da versão
./mvnw test                         # suíte Java: não pode quebrar (Testcontainers exige Docker)
```

Esperado: tudo verde. Sem Docker, rode só os testes de domínio/aplicação por
`./mvnw test -Dtest=<Classe>` (AGENTS.md §1) e registre que a integração não rodou.

## 2. US1 — barreira (SC-003, SC-004)

```bash
git config core.hooksPath .githooks && git config --get core.hooksPath   # → .githooks
git commit --allow-empty -m "feat: no scope"                  # recusado: scope-empty + exemplo
git commit --allow-empty -m "feat(conventions): smoke test"   # aceito
git worktree add ../wt-smoke HEAD && cd ../wt-smoke
git commit --allow-empty -m "merge: x"                        # recusado no outro worktree também
```

Desfaça os commits de teste (`git reset --hard HEAD~1`) e remova o worktree
(`git worktree remove ../wt-smoke`). Para ver a falha fechada, simule a ausência do `npx` com
`HABITATTIO_NPX=habitattio-no-such-npx git commit --allow-empty -m "feat(x): y"`. O commit deve
ser recusado com a instrução de instalação. Limpar o `PATH` não serve no Arch, porque o `node`
fica em `/usr/bin`.

## 3. US2 — PRs e issues (SC-005 a SC-007)

1. Web: "New issue" mostra só *Feature request* e *Bug report*, com título pré-preenchido e
   campos obrigatórios. "New pull request" já vem com as 5 seções.
2. Agente: pedir a uma sessão nova "abra um bug report de teste" e conferir título
   `fix(<scope>): …` e os blocos `###` do [contrato](./contracts/pull-requests-and-issues.md).
   Feche a issue de teste.
3. Task issues: rodar `/speckit-taskstoissues` nesta feature e conferir títulos
   `[002] TXXX: <English>`, corpo com a linha original e nenhuma duplicata ao rodar de novo.

## 4. US3 — versão (SC-008, SC-009)

Local, sem efeito:

```bash
node tools/versioning/version-sync.mjs --dry-run   # outcome=… com a versão que seria gravada
```

Na `main` (após o merge desta feature):
1. O merge dispara `version-sync`. Em até 10 min a `main` tem um commit
   `chore(release): set version to 0.1.0` com `Version-Sync: 0.1.0` (bootstrap: base `0.0.1`
   mais o `feat` da feature).
2. A execução disparada pelo próprio commit de sincronização não existe, ou sai com
   `outcome=noop reason=already-synced`.
3. Opcional: `./mvnw -q help:evaluate -Dexpression=project.version -DforceStdout` imprime a
   mesma versão.
4. Mesclar um PR só de `docs(…)` → versão sobe um PATCH.
