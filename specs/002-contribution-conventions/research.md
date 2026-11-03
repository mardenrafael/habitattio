# Research: Convenções de contribuição verificáveis

**Feature**: `002-contribution-conventions` | **Date**: 2026-11-02 | **Spec**: [spec.md](./spec.md)

Cada item resolve uma incógnita do Technical Context do [plan.md](./plan.md). Formato:
decisão, por quê, alternativas descartadas.

Ambiente verificado em 2026-11-02 na máquina do dono: Node `v24.14.1`, npm/npx `11.11.0`,
`gh 2.102.1`. `core.hooksPath` não configurado. Nenhum `.github/` no repositório.

---

## R1 — Como a barreira roda num projeto Maven sem `package.json`

**Decisão**: hook versionado em `.githooks/commit-msg` (shell POSIX), ativado por
`git config core.hooksPath .githooks`. O hook roda
`npx --yes -p @commitlint/cli@21.2.4 commitlint --edit "$1"`, com versão **fixa**.

**Por quê**:
- `core.hooksPath` fica no `config` comum do repositório (`.git/config`), compartilhado por
  todos os worktrees do clone. Caminho relativo é resolvido a partir da raiz do worktree em
  que o commit acontece, e cada worktree tem o seu `.githooks/` versionado. Um único comando
  vale para o clone inteiro (FR-008).
- `npx -p` baixa o commitlint para o cache do npm na primeira vez e reutiliza depois, sem
  `package.json`, sem `node_modules` no repositório e sem nada no `pom.xml` (FR-009).
- Versão fixa (`21.2.4`, a mais recente em `npm view` hoje) evita que o comportamento mude
  sozinho. Uma atualização vira um commit `build(commitlint): …`.

**Alternativas descartadas**:
- `package.json` com `devDependencies` + husky: traz um segundo gerenciador de pacotes para
  dentro do projeto Maven e exige `npm install` em cada clone. O ganho é pequeno para um único
  dono.
- Plugin Maven que instala o hook no `validate` (ex.: `git-build-hook-maven-plugin`): é
  mudança no `pom.xml` (FR-009) e só ativaria depois do primeiro build.
- Copiar o hook para `.git/hooks/`: não é versionado, e o diretório dos worktrees é separado.

## R2 — Configuração do commitlint sem pacote de preset

**Decisão**: `commitlint.config.mjs` na raiz, **autossuficiente**: sem `extends`. Regras
escritas à mão e `parserPreset.parserOpts` inline, com
`headerPattern: /^(\w*)(?:\(([^)]*)\))?(!)?: (.*)$/`. Regras de erro (nível 2): `type-enum`
(10 tipos), `type-empty`, `type-case` (minúsculo), `scope-empty`, `subject-empty`,
`header-max-length` 100. Mantém os `defaultIgnores` do commitlint.

**Por quê**: com `npx -p`, um `extends: ['@commitlint/config-conventional']` teria de ser
resolvido a partir da raiz do repositório, onde não há `node_modules`. Configuração inline
elimina a segunda dependência. O parser padrão (Angular) não aceita `!`; o `headerPattern`
acima aceita.

**Verificado** (probe em `$CLAUDE_JOB_DIR/tmp`, commitlint 21.2.4, config acima):

| Mensagem | Resultado | Regras |
|---|---|---|
| `feat(fatura): add late fee` | aceita | |
| `refactor(api)!: rename endpoints` | aceita | |
| `fix(x): y` + rodapé `BREAKING CHANGE: z` | aceita | |
| `chore(release): set version to 0.4.3` + trailer `Version-Sync: 0.4.3` | aceita | |
| `Merge branch 'foo'`, `… into main`, `Merge pull request #12 from a/b`, `Merge remote-tracking branch '…'` | aceita (ignorada) | |
| `Revert "feat(x): y"`, `fixup! feat(x): y` | aceita (ignorada) | |
| `feat: no scope` | recusada | `scope-empty` |
| `feat(): empty scope` | recusada | `scope-empty` |
| `feature(x): bad type` | recusada | `type-enum` |
| `Feat(x): upper` | recusada | `type-enum`, `type-case` |
| `merge: integrate x` | recusada | `type-enum`, `scope-empty` |
| `fix(x):` | recusada | `type-empty`, `scope-empty`, `subject-empty` |

Isso cobre FR-002 a FR-006 sem regra customizada.

**Alternativa descartada**: `extends` + `npx -p @commitlint/config-conventional`. A resolução
do pacote a partir do cache do npx não é garantida, e o preset traz regras de aviso que não
foram pedidas (ex.: `body-max-line-length`).

## R3 — Ferramenta ausente e mensagem de erro (FR-007)

**Decisão**: o hook falha **fechado**.
1. Sem `npx` no `PATH`: sai com código 1 e imprime o que falta (Node ≥ 20) e como resolver.
2. Se o `npx` falha antes do commitlint rodar (ex.: offline na primeira execução, sem o pacote
   em cache): sai com código 1 e uma mensagem equivalente.
3. Se o commitlint recusa: o próprio commitlint lista as regras violadas, e o hook acrescenta
   um bloco fixo com o formato e um exemplo válido
   (`feat(fatura): add late fee calculation`) e aponta para o AGENTS.md.

O bypass `git commit --no-verify` continua possível (Edge Cases da spec). O AGENTS.md proíbe
o uso por agentes.

**Por quê**: a meta é 100% (SC-001). Pular a verificação em silêncio é exatamente o risco
apontado no concept. O probe mostrou que `npx --no-install` com uma versão fora do cache é
cancelado, então o caso 2 é real e precisa de mensagem própria.

## R4 — Templates de PR e issue, e como agentes os seguem

**Decisão**:
- `.github/pull_request_template.md`: seções `## Summary`, `## Notable changes`,
  `## Breaking changes`, `## Related`, `## Verification` (FR-011).
- Issue forms em YAML: `.github/ISSUE_TEMPLATE/feature_request.yml` (title pré-preenchido
  `feat(<scope>): `) e `bug_report.yml` (title `fix(<scope>): `), campos obrigatórios conforme
  FR-012 e FR-013. `config.yml` com `blank_issues_enabled: false`, para que o formulário web
  só ofereça os dois tipos.
- Título de bug report e feature request = mesmo formato Conventional do commit, com tipo `fix`
  ou `feat`. Isso torna "o título no padrão" (FR-014) verificável com a mesma regra.
- Por linha de comando, o agente monta o corpo com os mesmos cabeçalhos que o GitHub gera a
  partir do formulário (`### <label>`), para que issues criadas pela web e pela CLI fiquem
  iguais. O formato exato está em [contracts/pull-requests-and-issues.md](./contracts/pull-requests-and-issues.md).

**Por quê**: issue forms validam campos obrigatórios na web. O GitHub não aplica template em
`gh issue create --body`/MCP, então o contrato precisa estar no AGENTS.md (FR-014, FR-029).
`blank_issues_enabled: false` não afeta a criação por API, então as issues de task continuam
possíveis.

**Alternativa descartada**: templates de issue em Markdown. Não têm campos obrigatórios, e o
corpo pré-preenchido vira texto solto para o agente reconstituir.

## R5 — Título de issue de task `[NNN] TXXX: <description>` sem tocar em `.specify/`

**Decisão**: editar `.claude/skills/speckit-taskstoissues/SKILL.md` (versionado no
repositório, fora de `.specify/templates/` e `.specify/scripts/`, então FR-017 é respeitado):
1. **Título**: `[NNN] TXXX: <description>`. `NNN` vem do prefixo do diretório da feature
   (`FEATURE_DIR`). A descrição é a da task traduzida para o inglês, mantendo identificadores e
   termos de domínio como estão (Edge Cases).
2. **Corpo**: a linha original da task em português (FR-016) e o caminho do `tasks.md`.
3. **Deduplicação**: casar `^\[NNN\] TXXX:` desta feature, em vez de `\bT\d{3,}\b` solto. O
   padrão atual marcaria o `T001` da feature 002 como já existente por causa do `T001` de outra
   feature, um bug latente que o novo título torna corrigível. Títulos legados `TXXX: …` sem
   prefixo **não** são considerados da feature atual.

A mesma regra entra no AGENTS.md (FR-029), e a skill alterada ganha um comentário-sentinela
(`<!-- habitattio: local change, see AGENTS.md §10 -->`) que torna visível, no diff, uma
sobrescrita por atualização do Spec Kit.

**Por quê**: a skill é o único lugar onde o fluxo `tasks.md → issues` está definido. O risco de
sobrescrita foi aceito na spec (Assumptions) e fica mitigado pelo registro no AGENTS.md e pelo
sentinela.

**Alternativa descartada**: só instruir no AGENTS.md. A skill diz explicitamente
"`T001: <description>`", e uma instrução conflitante em outro arquivo é frágil. Também foi
descartado traduzir o `tasks.md` (fora de escopo).

**Variantes por diretório**: `.claude/worktrees/*/.claude/skills/` são cópias de outros
worktrees, não deste checkout. Não são tocadas.

## R6 — Onde e como a versão é calculada (US3)

**Decisão**: um workflow `.github/workflows/version-sync.yml`, `on: push` em `main`, chama um
script Node sem dependências, `tools/versioning/version-sync.mjs`, que separa:
- **núcleo puro** (`tools/versioning/semver.mjs`): parse de cabeçalho/rodapé, classificação,
  cálculo da próxima versão, leitura/escrita da `<version>` do projeto no texto do `pom.xml`;
- **casca** (`version-sync.mjs`): lê o git, decide, grava, commita e faz push.

Testes com `node --test` (embutido no Node, nenhuma dependência).

**Por quê**: Node já é exigido pela barreira. `node:test` evita framework de teste novo. O
núcleo puro permite TDD rápido de todas as regras de FR-019 a FR-028; a casca é testada contra
repositórios git temporários.

**Alternativas descartadas**:
- `./mvnw versions:set` / `help:evaluate`: exige JDK 25 no runner e baixa plugins a cada
  execução, só para trocar uma linha. Mantido como **verificação** opcional no quickstart.
- Ferramentas de release (release-please, semantic-release): fora de escopo (Option C rejeitada
  no assessment).
- Bash puro: regra de bump com SemVer e parsing de rodapé ficam ilegíveis e difíceis de testar.

## R7 — Faixa de commits "desde a última sincronização" e concorrência (FR-019, FR-026)

**Decisão**: a faixa é **derivada do estado**, não do evento:
- **Marcador**: todo commit de sincronização leva o trailer `Version-Sync: X.Y.Z`. A última
  sincronização é o commit mais recente em `--first-parent` da `main` com esse trailer.
- **Faixa**: `git log --no-merges <marcador>..HEAD`. Sem marcador (primeira execução), a faixa
  é `HEAD^1..HEAD`, ou seja, os commits trazidos pela atualização que entrega a feature, e a
  base é a versão do `pom.xml` sem `-SNAPSHOT` (`0.0.1`, FR-023).
- **Concorrência**: `concurrency: { group: version-sync, cancel-in-progress: false }`. Se uma
  execução pendente for substituída por outra mais nova, nada se perde: a mais nova cobre tudo
  desde o marcador. Push rejeitado por non-fast-forward → `fetch`, recalcula do zero e tenta de
  novo (até 3 vezes). Push recusado por outro motivo → falha visível (FR-027).
- **Sem cascata** (FR-024, SC-009): (a) pushes feitos com o `GITHUB_TOKEN` não disparam novos
  workflows (regra do GitHub Actions); (b) defesa extra: se o `HEAD` já é um commit
  `Version-Sync`, a execução sai sem fazer nada; (c) o commit de sincronização tem o trailer e
  nunca entra numa faixa, porque é o próprio marcador.

**Por quê**: usar `before..after` do evento perderia commits quando uma execução pendente é
cancelada pela política de concorrência do GitHub (no máximo uma em execução e uma pendente por
grupo). Derivar do marcador torna a execução idempotente: rodar duas vezes sobre o mesmo `HEAD`
dá o mesmo resultado, e cada commit é contado uma vez.

**Alternativas descartadas**: tags como marcador (fora de escopo: FR-028 proíbe tags);
detectar a última mudança da linha `<version>` com `git log -G` (confunde com a `<version>` do
parent Spring Boot e com a promoção manual).

## R8 — Regra de cálculo e promoção para `1.0.0` (FR-020 a FR-023, FR-022a)

**Decisão** (núcleo puro, ver [data-model.md](./data-model.md)):
1. Ler a versão do projeto no `pom.xml` do `HEAD` (`atual`) e no marcador (`base`). Sem
   marcador: `base = atual` sem `-SNAPSHOT`.
2. `atual` não é `X.Y.Z` válida (exceto `-SNAPSHOT` sem marcador) → **falha** (FR-027).
3. `atual ≠ base` → é alteração manual. Válida só se `base` é `0.x.y` e `atual` é exatamente
   `1.0.0` (FR-022a): o resultado é `1.0.0` e é criado um commit de marcação vazio
   (`--allow-empty`), para que os commits da faixa não sejam contados de novo. Qualquer outra
   alteração manual → falha.
4. Classificar os commits da faixa: merge, `Revert "…"`, `fixup!`/`squash!`/`amend!`, trailer
   `Version-Sync` e cabeçalho fora do padrão → **não contam**. Contam os demais.
5. Impacto = o maior entre os commits que contam: breaking (`!` ou rodapé
   `BREAKING CHANGE:`/`BREAKING-CHANGE:`) > `feat` > qualquer outro tipo válido.
6. Aplicar: breaking em `0.x` → MINOR; breaking em `≥1` → MAJOR; `feat` → MINOR; outro → PATCH.
7. Nenhum commit conta → nada a fazer (FR-025).
8. Escrever a versão: substituir só o `<version>` filho direto de `<project>` (o que segue
   `<artifactId>habitattio</artifactId>`), sem tocar no `<parent>`.

**Commit de sincronização**: autor `github-actions[bot]`, mensagem
`chore(release): set version to X.Y.Z` + linha em branco + `Version-Sync: X.Y.Z`. Passa no
commitlint (verificado em R2), e o teste do núcleo garante isso.

## R9 — Permissões e pré-condições do GitHub

**Decisão**: o workflow declara `permissions: contents: write` e faz checkout com
`fetch-depth: 0`. Usa só actions oficiais (`actions/checkout`, `actions/setup-node`, Node 24),
fixadas por major. Pré-condições, a confirmar no quickstart: Actions habilitado no repositório,
permissão de escrita do workflow liberada, `main` sem proteção (Assumptions da spec).

**Por quê**: é o mínimo necessário para empurrar o commit. Se a `main` ganhar proteção, o push
falha de forma visível (FR-027), sem versão gravada em outro lugar.

## R10 — Testes e TDD para código fora do Java

**Decisão**: a constitution §II vale também para o código desta feature (shell e Node):
- `tools/conventions/commit-msg.test.mjs`: tabela de mensagens válidas e inválidas (≥ 8
  inválidas, uma por regra, e ≥ 5 válidas, como em SC-003) contra o `commitlint.config.mjs`. Fica
  vermelho antes de a config existir.
- `tools/conventions/hook.test.mjs`: executa `.githooks/commit-msg` num repositório git
  temporário (aceita, recusa, mensagem com exemplo, `npx` ausente → recusa).
- `tools/versioning/semver.test.mjs`: todas as regras de R8, uma por teste.
- `tools/versioning/version-sync.test.mjs`: repositórios git temporários (bootstrap, faixa a
  partir do marcador, sem commits que contam, promoção para `1.0.0`, alteração manual inválida,
  idempotência, `HEAD` = marcador, push com remote bare local).
- `tools/versioning/workflow.test.mjs`: estrutura do `.github/workflows/version-sync.yml` lida
  como texto (gatilho, permissão, concorrência, checkout completo, testes antes do sync, sem
  tags/releases). O workflow executa na CI, então também nasce de um teste vermelho (analyze D1).
- `node --test tools/` roda tudo. O workflow roda os testes de `tools/versioning` antes de
  sincronizar.

Os testes Java (`./mvnw test`) não mudam. A suíte Java ainda é executada no fim (Definition of
Done), para provar que a troca da `<version>` não quebra o build.

**Nomes de teste**: em português, descrevendo o comportamento, como manda a constitution §II
(`deve recusar commit sem escopo`, `deve incrementar MINOR em breaking change na série 0.x`).
Um comportamento por teste, Arrange/Act/Assert separados por linha em branco. Identificadores
de código e mensagens exibidas pelo hook ficam em inglês, porque falam do formato do commit,
que é em inglês.
