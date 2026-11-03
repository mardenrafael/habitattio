---

description: "Tasks de pr-validation-ci: validação automática e obrigatória de PRs com alvo main"
---

# Tasks: Validação automática de PRs com alvo `main`

**Input**: Design documents from `specs/004-pr-validation-ci/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/workflow-validacao-pr.md](./contracts/workflow-validacao-pr.md), [quickstart.md](./quickstart.md)

**Tests**: **obrigatórios**. A constitution (Princípio II), o AGENTS.md §3 e a research R12
exigem TDD: o workflow só é escrito depois de o `ValidacaoPullRequestWorkflowTest` ser visto
falhar pelo motivo certo. A partir daí, o teste é **read-only**: não pode ser editado, apagado
nem marcado `@Disabled` para passar. Testes existentes não podem ser alterados. **Nenhuma exceção
ao TDD foi pedida.** A única verificação manual é o comportamento do script de testes pulados
(T011): o teste do contrato garante que o passo existe, não que o script funciona. Isso deve ser
declarado no relatório.

**Organization**: tasks agrupadas por user story da spec, em ordem de prioridade.
- **US1** (P1) concentra quase todo o contrato (W1–W13, W15, W16). Ela inclui W6, o job único com
  nome estável, que também atende à US2.
- **US2** (P2) não tem código próprio. Ela é entregue pelo nome estável (W6, na US1), pela
  documentação (T017) e pelo passo manual do dono no ruleset (T027–T028).
- **US3** (P3) é o upload dos relatórios (W14).
- As provas de ponta a ponta dependem de um PR real e ficam juntas na Phase 6, com o rótulo da
  story que provam.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: pode rodar em paralelo (arquivo diferente, sem dependência pendente)
- **[Story]**: a user story atendida (US1–US3)
- Caminhos relativos à raiz do repositório. Pacote de testes: `br.com.marden.habitattio`
  (`src/test/java/br/com/marden/habitattio/`).

## Decisões de sequenciamento (TDD)

- **Todos os testes da US1 antes do arquivo**: T004–T007 escrevem os testes de W1–W13, W15 e
  W16. Em T008, todos falham porque `.github/workflows/validacao-pr.yml` não existe
  (`NoSuchFileException`). Esse é o motivo esperado (quickstart Q1). Uma falha por compilação ou
  por outro motivo exige corrigir o **teste** antes de considerá-lo visto falhar. Só depois disso
  ele fica read-only.
- **US3 com vermelho próprio**: o teste de W14 (T013) é escrito com o arquivo já existente e
  falha por **asserção** (não há passo de upload), não por arquivo ausente. Por isso a US3 vem
  depois da US1.
- **Testes de caracterização**: nenhum é previsto. Se algum teste passar de primeira, declare no
  relatório. Ele não justifica código.
- **SnakeYAML (YAML 1.1)**: a chave `on` vira `Boolean.TRUE` no `Map`. O helper de T003 trata
  isso, e nenhum teste deve procurar a string `"on"` sozinha (research R12).
- **PRs descartáveis** (T023–T025, T028): saem do branch da feature, não de `main`, enquanto a
  feature não estiver mergeada. O `pull_request` usa o workflow do merge ref, e só o branch da
  feature tem o arquivo. Testes descartáveis são **arquivos novos** nesses branches; nenhum teste
  existente é editado (guardrail). Esses PRs são fechados **sem merge** e os branches apagados.

---

## Phase 1: Setup

**Purpose**: confirmar o ponto de partida antes de qualquer mudança.

- [ ] T001 Rodar `./mvnw test` com Docker no ar e registrar no relatório que a suíte atual está verde **antes** de qualquer alteração (número de testes e duração). Se o Docker não estiver disponível, **pare e reporte**: esta feature exige a suíte completa (FR-004/FR-005) e não pode ser validada só com os testes de domínio.
- [ ] T002 [P] Conferir que os SHAs fixados na research continuam apontando para as tags e registrar no relatório: `gh api repos/actions/checkout/commits/v7.0.1 --jq .sha` → `3d3c42e5aac5ba805825da76410c181273ba90b1`; `gh api repos/actions/setup-java/commits/v6.1.0 --jq .sha` → `b843ebf9b913146b38da2eaa7aeddf76074ae1ad`; `gh api repos/actions/upload-artifact/commits/v7.0.2 --jq .sha` → `cf430e030ddbb5b0abf93d22962f4752f3646cd9`. Se algum divergir, pare e reporte, sem trocar SHA por conta própria. Registrar também as regras atuais do ruleset: `gh api repos/mardenrafael/habitattio/rulesets/26249037 --jq '.rules[].type'` (esperado hoje: `deletion`, `non_fast_forward`).

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: a estrutura do teste do contrato, compartilhada por US1 e US3.

**⚠️ CRITICAL**: nenhum método de teste antes desta fase.

- [ ] T003 Criar `src/test/java/br/com/marden/habitattio/ValidacaoPullRequestWorkflowTest.java` **sem métodos de teste**, só a infraestrutura: JUnit 5 puro, sem contexto Spring, sem Docker, imports explícitos e Javadoc curto em português citando `specs/004-pr-validation-ci/contracts/workflow-validacao-pr.md`. Helpers estáticos:
  - (a) `CAMINHO = Path.of(".github/workflows/validacao-pr.yml")`, relativo à raiz do projeto, como o `ImagemContainerTest` faz com o `Dockerfile`.
  - (b) `texto()`: `Files.readString(CAMINHO)`, deixando a `NoSuchFileException` propagar (é o motivo esperado do vermelho).
  - (c) `workflow()`: `Map<String, Object>` de `new org.yaml.snakeyaml.Yaml().load(texto())`.
  - (d) `gatilhos()`: o `Map` sob a chave `Boolean.TRUE`, ou sob `"on"` se ela existir (YAML 1.1).
  - (e) `job()`: `jobs.validar` como `Map`.
  - (f) `passos()`: `List<Map<String, Object>>` de `job().get("steps")`.
  - (g) `passoQueUsa(String acao)`: o passo cujo `uses` começa com `acao + "@"`, falhando com mensagem em português se não houver.
  - (h) `indiceDoPassoQueRoda(String trecho)`: o índice do primeiro passo cujo `run` contém o trecho, ou -1.

  Confirmar que compila: `./mvnw test-compile`. O SnakeYAML já está no classpath (transitivo de `spring-boot-starter`, research R12). **Não alterar o `pom.xml`.**

**Checkpoint**: classe compila sem testes. US1 pode começar.

---

## Phase 3: User Story 1 - Veredito automático da suíte completa em todo PR para `main` (Priority: P1) 🎯 MVP

**Goal**: um workflow que, em todo PR com alvo `main`, roda a suíte completa e o empacotamento
sobre o merge ref e produz um único check `validar`, seguro para fork, com teto e cancelamento.

**Independent Test**: o `ValidacaoPullRequestWorkflowTest` passa localmente (Q2). Num PR real, o
check `validar` aparece sozinho e dá `success` com todos os testes executados (Q4, T021).

### Tests for User Story 1 ⚠️ (escrever e ver falhar antes de T009)

Todos no mesmo arquivo, `src/test/java/br/com/marden/habitattio/ValidacaoPullRequestWorkflowTest.java`.
Por isso nenhum é [P]. Um comportamento por método, estrutura AAA e mensagens de asserção em
português.

- [ ] T004 [US1] Testes de gatilho:
  - `deveDispararSomenteEmPullRequest` (W1): o conjunto de chaves de `gatilhos()` é exatamente `{"pull_request"}`. Isso exclui `push`, `pull_request_target`, `workflow_dispatch` e `schedule`.
  - `deveValidarSomentePullRequestComAlvoMain` (W2): `gatilhos().pull_request.branches` é exatamente `List.of("main")`.
  - `deveRevalidarANovosCommitsEReabertura` (W3): `pull_request.types` está ausente ou contém `opened`, `synchronize` e `reopened`.
- [ ] T005 [US1] Testes de segurança:
  - `deveTerPermissaoSomenteDeLeitura` (W4): `workflow().permissions` é exatamente `Map.of("contents", "read")`.
  - `deveNaoUsarSegredos` (W5): `texto()` não contém `secrets.`.
  - `deveFixarAcoesPorSha` (W15): todo `uses` dos passos casa com `^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+@[0-9a-f]{40}$`, e há ao menos um.
  - `deveNaoPersistirCredenciais` (W10, 2ª parte): `passoQueUsa("actions/checkout").with.persist-credentials` é `Boolean.FALSE`.
- [ ] T006 [US1] Testes da forma do job:
  - `deveTerUmUnicoJobComNomeEstavel` (W6, que também atende à US2): as chaves de `jobs` são exatamente `{"validar"}`, `job().name` é `"validar"` e não existe `strategy`.
  - `deveRodarEmMaquinaLinuxDeVersaoFixa` (W7): `runs-on` é `"ubuntu-24.04"`.
  - `deveTerTetoDeDuracao` (W8): `timeout-minutes` é `Integer` entre 1 e 60, inclusive.
  - `deveCancelarValidacaoAnteriorDoMesmoPr` (W9): `concurrency.group` (String) contém `github.event.pull_request.number`, e `concurrency.cancel-in-progress` é `Boolean.TRUE`. A `concurrency` pode estar no topo ou no job; o teste aceita as duas posições.
  - `deveReprovarAoPrimeiroPassoQueFalhar` (W16): nem o job nem nenhum passo tem `continue-on-error` igual a `true`.
- [ ] T007 [US1] Testes do build:
  - `deveValidarOEstadoCombinadoComMain` (W10, 1ª parte): o passo de `actions/checkout` não tem `with.ref`.
  - `deveUsarJava25` (W11): `passoQueUsa("actions/setup-java").with` tem `distribution` igual a `"temurin"` e `String.valueOf(java-version)` igual a `"25"`.
  - `deveRodarSuiteCompletaEEmpacotarPeloWrapper` (W12): existe passo com `run` contendo `./mvnw` e a palavra `package`. Nenhum `run` contém `-DskipTests`, `-Dmaven.test.skip`, `-Dtest=`, `-Dgroups`, `-DexcludedGroups`, ` -fae`, ` -fn`, `--fail-at-end` nem `--fail-never`.
  - `deveReprovarQuandoAlgumTesteForPulado` (W13): `indiceDoPassoQueRoda("surefire-reports")` é maior que `indiceDoPassoQueRoda("./mvnw")` (ambos ≥ 0), e o `run` desse passo contém `skipped`.
- [ ] T008 [US1] Rodar `./mvnw test -Dtest=ValidacaoPullRequestWorkflowTest` e ver **todos** os testes de T004–T007 falharem com `NoSuchFileException` em `.github/workflows/validacao-pr.yml` (quickstart Q1). Registrar a saída no relatório. Uma falha por outro motivo exige corrigir o teste e repetir. A partir daqui, T004–T007 são **read-only**.

### Implementation for User Story 1

- [ ] T009 [US1] Criar `.github/workflows/validacao-pr.yml` (research R1–R11), na ordem:
  - **Topo do workflow**:
    - `name: Validação de PR`.
    - `on: pull_request` com `branches: [main]` e sem `types`.
    - `permissions: contents: read`.
    - `concurrency: { group: validacao-pr-${{ github.event.pull_request.number }}, cancel-in-progress: true }`.
  - **Job `validar`**:
    - `name: validar`, `runs-on: ubuntu-24.04`, `timeout-minutes: 30`.
  - **Passos do job**:
    1. `actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1 # v7.0.1` com `persist-credentials: false` e sem `ref`.
    2. `actions/setup-java@b843ebf9b913146b38da2eaa7aeddf76074ae1ad # v6.1.0` com `distribution: temurin`, `java-version: '25'` e `cache: maven`.
    3. `name: Suíte completa e empacotamento`, `run: ./mvnw -B package`.
    4. `name: Nenhum teste pulado`, com um `run` bash que:
       - usa `shopt -s nullglob` e lista `target/surefire-reports/TEST-*.xml`;
       - falha com `::error::Nenhum relatório de teste encontrado` se não houver arquivos;
       - separa os arquivos com `grep -lE 'skipped="[1-9][0-9]*"'` (com `|| true`);
       - se houver algum, imprime `::error::Testes pulados (proibido pela constitution, FR-005):` mais a lista e sai com 1.

  Sem `continue-on-error` em lugar nenhum. Nenhum segredo. Um comentário curto no topo aponta para `docs/validacao-pr.md` e `specs/004-pr-validation-ci/`.
- [ ] T010 [US1] Rodar `./mvnw test -Dtest=ValidacaoPullRequestWorkflowTest` e ver T004–T007 verdes (quickstart Q2). Se algum só passar mudando o teste, o problema é o workflow: corrija o workflow, não o teste.
- [ ] T011 [US1] **Verificação manual declarada** do script do passo "Nenhum teste pulado". Copie o corpo do `run` para `$CLAUDE_JOB_DIR/tmp/guarda.sh` (ou outro diretório temporário fora do repositório) e rode-o num diretório de trabalho temporário com `target/surefire-reports/` montado à mão em três casos:
  - (a) um `TEST-a.xml` com `<testsuite ... skipped="0">` → saída 0;
  - (b) um `TEST-b.xml` com `skipped="1"` → saída 1, listando o arquivo;
  - (c) diretório vazio → saída 1 com a mensagem de nenhum relatório.

  Registrar as três saídas no relatório.
- [ ] T012 [US1] Rodar `./mvnw -B package` completo, com Docker (quickstart Q3), e em seguida o script de T011 sobre os relatórios reais. Esperado: verde, e nenhum arquivo com `skipped` diferente de 0. Registrar o total de testes e a duração local.

**Checkpoint**: US1 completa localmente. O MVP já pode ir para PR (T020) mesmo sem a US3.

---

## Phase 4: User Story 3 - Diagnóstico da falha a partir do PR (Priority: P3)

**Goal**: na falha, os relatórios do surefire ficam acessíveis como artefato do run (FR-010).

**Independent Test**: o teste de W14 passa localmente. Num PR propositalmente reprovado, o
artefato `relatorios-de-teste` identifica o teste e a mensagem (Q6, T023).

### Tests for User Story 3 ⚠️

- [ ] T013 [US3] Em `src/test/java/br/com/marden/habitattio/ValidacaoPullRequestWorkflowTest.java`, adicionar `deveAnexarRelatoriosDeTesteQuandoFalhar` (W14). `passoQueUsa("actions/upload-artifact")` existe e cumpre:
  - `if` é `"failure()"` ou `"${{ failure() }}"`;
  - `with.name` é `"relatorios-de-teste"`;
  - `with.path` contém `target/surefire-reports`;
  - `with.retention-days` é `14`;
  - `with.if-no-files-found` é `"ignore"`.
- [ ] T014 [US3] Rodar `./mvnw test -Dtest=ValidacaoPullRequestWorkflowTest#deveAnexarRelatoriosDeTesteQuandoFalhar` e ver falhar por **asserção** (passo de upload ausente), não por arquivo ausente. Registrar. A partir daqui, T013 é read-only.

### Implementation for User Story 3

- [ ] T015 [US3] Em `.github/workflows/validacao-pr.yml`, adicionar como último passo:
  - `name: Relatórios de teste`;
  - `if: failure()`;
  - `uses: actions/upload-artifact@cf430e030ddbb5b0abf93d22962f4752f3646cd9 # v7.0.2`;
  - `with: { name: relatorios-de-teste, path: target/surefire-reports/, retention-days: 14, if-no-files-found: ignore }`.
- [ ] T016 [US3] Rodar `./mvnw test -Dtest=ValidacaoPullRequestWorkflowTest` inteiro e ver todos os 17 métodos verdes (T004–T007 e T013).

**Checkpoint**: arquivo de workflow completo, com contrato W1–W16 coberto.

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: documentação (FR-017, entrega a parte documental da US2), suíte completa e PR.

- [ ] T017 [P] Criar `docs/validacao-pr.md`, em português e no estilo do `docs/container.md`, com estas seções:
  - (1) **O que roda**: gatilho (PR com alvo `main`; abrir, reabrir, novo commit), merge ref com `main`, `ubuntu-24.04`, Temurin 25, `./mvnw -B package` e o passo de testes pulados.
  - (2) **Como ler uma falha**: "Details" do check `validar` → log, mais o artefato `relatorios-de-teste` (14 dias). Uma falha de compilação não gera artefato.
  - (3) **Re-run**: "Re-run jobs" ou `gh run rerun <id>`. Uma falha de infraestrutura reprova, nunca aprova.
  - (4) **Teto**: 30 min; revise se a duração observada passar de ~10 min.
  - (5) **Segurança**: token só de leitura, sem segredos, PRs de fork, ações fixadas por SHA. Inclui como atualizar: `gh api repos/<ação>/releases/latest` e `gh api repos/<ação>/commits/<tag> --jq .sha`, atualizando o SHA e o comentário de versão juntos. O `ValidacaoPullRequestWorkflowTest` exige o SHA.
  - (6) **Tornar obrigatório (passo do dono)**: *Settings → Rules → Rulesets → `block-main`* → **Require status checks to pass** → adicionar `validar` (GitHub Actions). Opcionalmente, **Require a pull request before merging**. Registrar o achado: hoje o `block-main` só bloqueia exclusão e force-push (research R13).
  - (7) **Por que um `@Disabled` reprova o PR** (research R6).
- [ ] T018 [P] Em `AGENTS.md` §1, logo após a subseção "Imagem de container", adicionar a subseção `### Validação de PR (CI)`. Em no máximo 6 linhas, ela diz que:
  - todo PR com alvo `main` roda o check obrigatório `validar` (`./mvnw -B package` sobre o merge com `main`, com Docker, no GitHub Actions);
  - um teste pulado reprova o PR;
  - os relatórios de falha ficam no artefato do run;
  - o `ValidacaoPullRequestWorkflowTest` protege o contrato do workflow.

  Fecha com o link para [`docs/validacao-pr.md`](docs/validacao-pr.md). Nenhuma outra seção do AGENTS.md muda.
- [ ] T019 Rodar `./mvnw test` completo com Docker e registrar total, falhas (0) e pulados (0). Conferir com `git status`/`git diff --stat` que só mudaram estes arquivos:
  - `.github/workflows/validacao-pr.yml`;
  - `src/test/java/br/com/marden/habitattio/ValidacaoPullRequestWorkflowTest.java`;
  - `docs/validacao-pr.md`;
  - `AGENTS.md`;
  - `specs/004-pr-validation-ci/tasks.md`.

  Em particular, `pom.xml`, `src/main/`, o `Dockerfile` e os testes existentes ficam intactos.
- [ ] T020 Commitar e fazer push do branch da feature e abrir PR com alvo `main` (pode ser draft, porque drafts disparam a validação). A descrição do PR cita a spec e lista o passo manual do ruleset (T027). Anotar o número do PR para a Phase 6.

---

## Phase 6: Prova de ponta a ponta (PRs reais)

**Purpose**: cenários de aceitação da spec no GitHub (quickstart Q4–Q10). Se uma suposição da
research se mostrar falsa no runner (R3: Docker/Testcontainers; R4: Java 25), **pare e reporte**
com o log. Não altere testes nem desligue partes da suíte para contornar.

- [ ] T021 [US1] Q4: no PR de T020, `gh pr checks <n>` mostra `validar` como `pass`. No log do run (`gh run view <id> --log`), confira:
  - o total de testes igual ao de T019;
  - a execução de `ImagemContainerTest`, dos testes de PostgreSQL/Redis e do `ValidacaoPullRequestWorkflowTest`;
  - o passo "Nenhum teste pulado" verde.

  Registrar a duração (SC-007) e confirmar no relatório as suposições R3 e R4.
- [ ] T022 [US1] Q5: com uma validação em andamento no PR de T020, enviar um commit novo (por exemplo, marcar tasks concluídas em `specs/004-pr-validation-ci/tasks.md`). Confirmar com `gh run list --workflow validacao-pr.yml` que o run anterior ficou `cancelled` e que o check do SHA novo é o exibido.
- [ ] T023 [US3] Q6: criar o branch `descartavel/validacao-reprovada` **a partir do branch da feature** e adicionar o arquivo novo `src/test/java/br/com/marden/habitattio/ProvaValidacaoReprovadaTest.java` com um único teste, `deveFalharDePropositoParaProvarAValidacao`, que faz `assertEquals(1, 2, "falha proposital da prova Q6")`. Abrir PR com alvo `main`. Confirmar que `validar` terminou `failure` e que o artefato `relatorios-de-teste` (`gh run download <id> -n relatorios-de-teste`) traz o nome do teste e a mensagem. **Não fechar ainda se T028 for feito em seguida**; se não for, fechar sem merge e apagar o branch.
- [ ] T024 [US3] Q7: criar o branch `descartavel/validacao-nao-compila` a partir do branch da feature, com o arquivo novo `src/test/java/br/com/marden/habitattio/ProvaNaoCompilaTest.java` contendo um erro de sintaxe. Abrir PR com alvo `main`. Confirmar `failure`, ausência do artefato e o erro de compilação visível no log. Fechar sem merge e apagar o branch.
- [ ] T025 [US1] Q9: criar o branch `descartavel/alvo-nao-main` a partir do branch da feature, com um commit trivial (arquivo novo de texto em `specs/004-pr-validation-ci/`, apagado junto com o branch). Abrir PR com alvo no **branch da feature**, não em `main`. Confirmar que nenhum check `validar` aparece. Fechar sem merge e apagar o branch.
- [ ] T026 [US1] Q10: `gh run rerun <id do run aprovado de T021/T022>` e confirmar uma nova execução no mesmo SHA com o mesmo veredito (SC-005).
- [ ] T027 [US2] **Passo do dono, fora do repositório; o agente não executa:** adicionar ao ruleset `block-main` a regra **Require status checks to pass** com o check `validar` (e, se o dono quiser, **Require a pull request before merging**). Depois, o agente confirma: `gh api repos/mardenrafael/habitattio/rulesets/26249037 --jq '.rules[].type'` inclui `required_status_checks`. Sem este passo, registrar no relatório que o SC-002 não está garantido.
- [ ] T028 [US2] Q8, só depois de T027: no PR de T023 (reprovado), `gh pr view <n> --json mergeStateStatus` deve mostrar `BLOCKED`. Disparar um re-run e conferir que, com a validação em andamento, o estado continua bloqueado. Fechar o PR sem merge e apagar o branch `descartavel/validacao-reprovada`.
- [ ] T029 Relatório final, com:
  - as durações (local em T012, runner em T021);
  - as suposições R3/R4 confirmadas ou refutadas;
  - a verificação manual de T011 declarada;
  - os testes de caracterização, se houver algum;
  - o estado do ruleset (T027 feito ou pendente);
  - a confirmação de que os PRs descartáveis foram fechados sem merge e os branches apagados.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: sem dependências. T001 e T002 podem rodar juntas.
- **Foundational (Phase 2)**: depende da Phase 1 e bloqueia US1 e US3.
- **US1 (Phase 3)**: depende da Phase 2. É o MVP.
- **US3 (Phase 4)**: depende da US1 (T009), porque precisa do arquivo existente para ter um vermelho por asserção.
- **Polish (Phase 5)**: T017 e T018 dependem só do desenho, e podem começar junto da US1. T019 e T020 dependem de US1 e US3.
- **Ponta a ponta (Phase 6)**: depende de T020. T028 depende de T027 (dono) e de T023.

### User Story Dependencies

- **US1 (P1)**: independente. Entrega o veredito.
- **US2 (P2)**: o nome estável vem da US1 (T006/T009) e a documentação de T017. A ativação depende do dono (T027).
- **US3 (P3)**: depende do arquivo da US1. É testável sozinha a partir daí.

### Within Each User Story

- Testes escritos e vistos falhar (T008, T014) antes de qualquer linha do workflow que eles
  cobrem.
- O workflow muda só até o teste passar. Teste visto falhar não muda.

---

## Parallel Example

```text
# Setup:
T001 (suíte baseline)  ||  T002 (conferir SHAs e ruleset)

# Durante a US1 (arquivos diferentes do teste/workflow):
T004–T012 (sequenciais, mesmo arquivo)  ||  T017 docs/validacao-pr.md  ||  T018 AGENTS.md

# Phase 6 (PRs independentes, depois de T020):
T023 (reprovado)  ||  T024 (não compila)  ||  T025 (alvo não-main)
```

O paralelismo é pequeno de propósito: o contrato inteiro vive em dois arquivos (teste e
workflow), editados em sequência.

---

## Implementation Strategy

### MVP First (US1)

1. Phases 1 e 2.
2. Phase 3 (US1): testes → vermelho → workflow → verde → verificação do script → suíte local.
3. **Parar e validar**: já é possível abrir o PR (T020) e provar Q4/Q5. O projeto sai do baseline
   de 0% de PRs validados.

### Incremental Delivery

1. US1 → PR com veredito (MVP).
2. US3 → relatórios na falha (mesmo PR, commit seguinte).
3. Docs (T017/T018) → US2 documentada. O dono ativa o ruleset (T027) → US2 entregue (SC-002).
4. Provas Q6–Q10 com PRs descartáveis.

---

## Notes

- Nenhuma task altera `pom.xml`, `src/main/`, `Dockerfile`, `.dockerignore`, testes existentes,
  `.specify/scripts/` ou `.specify/templates/`.
- Configurar o ruleset é decisão e ação do dono (T027). O agente só verifica.
- Commit ao fim de cada fase ou grupo lógico de tasks.
